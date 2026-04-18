package com.pronosticup.backend.scores.service;

import com.pronosticup.backend.leagues.entity.League;
import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.pronostics.entity.Pronostic;
import com.pronosticup.backend.tournaments.model.TournamentSnapshotDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreBatchService {

    private static final Logger scoreBatchLogger = LoggerFactory.getLogger("SCORE_BATCH");

    private final ScoreService scoreService;

    /**
     *   lanzo el cálculo batch de todos los torneos soportados por la aplicación.
     */
    public void calculateScoresBatchForAllSupportedTournaments() {
        calculateScoresBatchForTournament("mundial");
        calculateScoresBatchForTournament("eurocopa");
    }

    /**
     *   orquesto el batch completo de cálculo de puntuaciones del torneo indicado.
     * Primero valido si estoy dentro de la ventana de cálculo y, si procede,
     * recorro las ligas y sus pronósticos confirmados para recalcular sus puntos.
     */
    public void calculateScoresBatchForTournament(String tournament) {
        String normalizedTournament = scoreService.normalizeTournament(tournament);

        scoreBatchLogger.info("Iniciando batch de puntuación para torneo {}", normalizedTournament);

        //   cargo el snapshot de partidos porque su fecha first me dice si ahora toca calcular.
        TournamentSnapshotDocument matchesSnapshot = scoreService.getMatchesKnockoutsSnapshot(normalizedTournament);
        Map<String, Object> matchesPayload = scoreService.getPayloadOrFail(
                matchesSnapshot,
                normalizedTournament + "_MATCHES_KNOCKOUTS"
        );

        //   obtengo la fecha de referencia del torneo y compruebo si estoy dentro de la ventana válida.
        Instant firstDate = scoreService.getFirstDateFromMatchesPayload(matchesPayload);
        boolean shouldCalculate = scoreService.isInsideCalculationWindow(firstDate);
        //alp:IMPORTANTE QUITAR ESTE IF PORQUE SOLO SON PARA PRUEBAS EN CALIENTE
        if(!shouldCalculate && tournament.equals("mundial")) {
            shouldCalculate = true;
        }
        if (!shouldCalculate) {
            scoreBatchLogger.info(
                    "No ejecuto el batch del torneo {} porque la fecha actual está fuera de la ventana válida",
                    normalizedTournament
            );
            return;
        }

        //   cargo también el snapshot de standings oficiales porque lo necesito para puntuar la clasificación.
        TournamentSnapshotDocument standingsSnapshot = scoreService.getStandingsSnapshot(normalizedTournament);
        Map<String, Object> standingsPayload = scoreService.getPayloadOrFail(
                standingsSnapshot,
                normalizedTournament + "_STANDINGS"
        );

        //   busco las ligas del torneo que existían antes de la fecha límite del torneo.
        List<League> eligibleLeagues = scoreService.getEligibleLeagues(normalizedTournament, firstDate);

        scoreBatchLogger.info(
                "Las ligas elegibles para puntuar del torneo {} son: {}",
                normalizedTournament,
                eligibleLeagues.stream().map(League::getId).toList()
        );

        //   recorro una a una las ligas elegibles.
        for (League league : eligibleLeagues) {
            processLeague(normalizedTournament, league, matchesPayload, standingsPayload);
        }

        scoreBatchLogger.info("Batch de puntuación finalizado correctamente para torneo {}", normalizedTournament);
    }

    /**
     *   proceso una liga concreta y recorro todos sus pronósticos confirmados.
     */
    private void processLeague(String tournament,
                               League league,
                               Map<String, Object> matchesPayload,
                               Map<String, Object> standingsPayload) {

        if (league == null || league.getId() == null || league.getId().isBlank()) {
            return;
        }

        scoreBatchLogger.info("Procesando liga {} del torneo {}", league.getId(), tournament);

        //   traigo solo los miembros confirmados porque son los únicos que deben entrar en el cálculo.
        List<LeagueMember> confirmedMembers = scoreService.getConfirmedLeagueMembers(league.getId());

        scoreBatchLogger.info(
                "La liga {} del torneo {} tiene los siguientes pronósticos confirmados: {}",
                league.getId(),
                tournament,
                confirmedMembers.stream()
                        .map(LeagueMember::getPronosticId)
                        .filter(Objects::nonNull)
                        .toList()
        );

        //   recorro cada miembro confirmado y proceso su pronóstico asociado.
        for (LeagueMember member : confirmedMembers) {
            processLeagueMember(tournament, member, matchesPayload, standingsPayload);
        }
    }

    /**
     *   proceso un miembro de liga confirmado y recalculo su pronóstico.
     */
    private void processLeagueMember(String tournament,
                                     LeagueMember member,
                                     Map<String, Object> matchesPayload,
                                     Map<String, Object> standingsPayload) {

        if (member == null) {
            return;
        }

        String pronosticId = member.getPronosticId();

        if (pronosticId == null || pronosticId.isBlank()) {
            return;
        }

        try {
            //   cargo el pronóstico real desde Mongo para recalcular todos sus puntos.
            Pronostic pronostic = scoreService.getPronosticOrFail(pronosticId);

            //   calculo todos los bloques de puntos y actualizo el total del pronóstico.
            Integer totalPoints = calculatePronosticTotalPoints(pronostic, matchesPayload, standingsPayload);

            pronostic.setTotalPoints(totalPoints);
            scoreService.savePronostic(pronostic);

            scoreBatchLogger.info(
                    "Pronóstico {} recalculado correctamente con {} puntos para torneo {}",
                    pronosticId,
                    totalPoints,
                    tournament
            );
        } catch (Exception e) {
            scoreBatchLogger.error(
                    "Error recalculando el pronóstico {} del torneo {}: {}",
                    pronosticId,
                    tournament,
                    e.getMessage(),
                    e
            );
        }
    }

    /**
     *   recalculo el total completo de un pronóstico sumando todos los bloques de puntuación.
     */
    private Integer calculatePronosticTotalPoints(Pronostic pronostic,
                                                  Map<String, Object> matchesPayload,
                                                  Map<String, Object> standingsPayload) {

        //   calculo primero los puntos de los partidos de fase de grupos.
        int groupMatchPoints = calculateGroupStageMatchPoints(pronostic, matchesPayload);

        //   calculo después los puntos del orden de clasificación de cada grupo.
        int groupStandingsPoints = calculateGroupStandingsPoints(pronostic, standingsPayload);

        //   calculo por último los puntos de las rondas eliminatorias.
        int knockoutPoints = calculateKnockoutPoints(pronostic, matchesPayload);

        return groupMatchPoints + groupStandingsPoints + knockoutPoints;
    }

    /**
     *   recorro todos los partidos de fase de grupos del pronóstico, comparo cada uno
     * con su partido oficial y guardo el resultado en el atributo matchPoints del propio partido.
     */
    @SuppressWarnings("unchecked")
    private Integer calculateGroupStageMatchPoints(Pronostic pronostic, Map<String, Object> matchesPayload) {
        int totalGroupMatchPoints = 0;

        if (pronostic == null || pronostic.getGroupStage() == null) {
            return 0;
        }

        Map<String, Object> groupStage = pronostic.getGroupStage();
        Map<String, Object> matchesByGroup = (Map<String, Object>) groupStage.get("matchesByGroup");

        if (matchesByGroup == null || matchesByGroup.isEmpty()) {
            return 0;
        }

        List<Map<String, Object>> officialMatches = scoreService.getMapList(matchesPayload, "matches");

        //   recorro cada grupo del pronóstico.
        for (Map.Entry<String, Object> entry : matchesByGroup.entrySet()) {
            Object rawMatches = entry.getValue();

            if (!(rawMatches instanceof List<?> predictedMatches)) {
                continue;
            }

            //   recorro partido a partido dentro del grupo.
            for (Object rawPredictedMatch : predictedMatches) {
                if (!(rawPredictedMatch instanceof Map<?, ?>)) {
                    continue;
                }

                Map<String, Object> predictedMatch = (Map<String, Object>) rawPredictedMatch;

                int pointsForMatch = calculateSingleGroupMatchPoints(predictedMatch, officialMatches);

                //   escribo los puntos directamente sobre el propio partido del pronóstico.
                predictedMatch.put("matchPoints", pointsForMatch);

                totalGroupMatchPoints += pointsForMatch;
            }
        }

        return totalGroupMatchPoints;
    }

    /**
     *   calculo los puntos de un único partido de fase de grupos.
     */
    private Integer calculateSingleGroupMatchPoints(Map<String, Object> predictedMatch,
                                                    List<Map<String, Object>> officialMatches) {

        if (predictedMatch == null || officialMatches == null || officialMatches.isEmpty()) {
            return 0;
        }

        String groupKey = scoreService.getString(predictedMatch, "groupKey");
        String homeName = scoreService.getString(predictedMatch, "homeName");
        String awayName = scoreService.getString(predictedMatch, "awayName");
        Integer predictedHomeGoals = scoreService.getInteger(predictedMatch, "homeGoals");
        Integer predictedAwayGoals = scoreService.getInteger(predictedMatch, "awayGoals");

        if (groupKey == null || homeName == null || awayName == null) {
            return 0;
        }

        String officialGroup = "GROUP_" + groupKey;

        //   localizo el partido oficial equivalente usando grupo, local y visitante.
        Map<String, Object> officialMatch = findOfficialGroupMatch(officialMatches, officialGroup, homeName, awayName);

        if (officialMatch == null) {
            return 0;
        }

        Map<String, Object> score = scoreService.getMap(officialMatch, "score");
        Map<String, Object> fullTime = scoreService.getMap(score, "fullTime");

        Integer officialHomeGoals = scoreService.getInteger(fullTime, "home");
        Integer officialAwayGoals = scoreService.getInteger(fullTime, "away");

        //   no puntúo si el resultado oficial todavía no existe.
        if (officialHomeGoals == null || officialAwayGoals == null) {
            return 0;
        }

        int points = 0;

        //   sumo 10 puntos si acierto el ganador o el empate.
        if (sameMatchOutcome(predictedHomeGoals, predictedAwayGoals, officialHomeGoals, officialAwayGoals)) {
            points += 10;
        }

        //   sumo 3 puntos por acertar los goles del local.
        if (Objects.equals(predictedHomeGoals, officialHomeGoals)) {
            points += 3;
        }

        //   sumo 3 puntos por acertar los goles del visitante.
        if (Objects.equals(predictedAwayGoals, officialAwayGoals)) {
            points += 3;
        }

        //   sumo 5 puntos extra si he acertado el marcador exacto.
        if (Objects.equals(predictedHomeGoals, officialHomeGoals)
                && Objects.equals(predictedAwayGoals, officialAwayGoals)) {
            points += 5;
        }

        return points;
    }

    /**
     *   localizo el partido oficial de fase de grupos que corresponde con el partido del pronóstico.
     */
    private Map<String, Object> findOfficialGroupMatch(List<Map<String, Object>> officialMatches,
                                                       String officialGroup,
                                                       String homeName,
                                                       String awayName) {

        for (Map<String, Object> officialMatch : officialMatches) {
            String group = scoreService.getString(officialMatch, "group");

            Map<String, Object> homeTeam = scoreService.getMap(officialMatch, "homeTeam");
            Map<String, Object> awayTeam = scoreService.getMap(officialMatch, "awayTeam");

            String officialHomeName = scoreService.getString(homeTeam, "name");
            String officialAwayName = scoreService.getString(awayTeam, "name");

            if (Objects.equals(group, officialGroup)
                    && Objects.equals(officialHomeName, homeName)
                    && Objects.equals(officialAwayName, awayName)) {
                return officialMatch;
            }
        }

        return null;
    }

    /**
     *   compruebo si dos marcadores tienen el mismo desenlace: victoria local, empate o victoria visitante.
     */
    private boolean sameMatchOutcome(Integer predictedHomeGoals,
                                     Integer predictedAwayGoals,
                                     Integer officialHomeGoals,
                                     Integer officialAwayGoals) {

        if (predictedHomeGoals == null || predictedAwayGoals == null
                || officialHomeGoals == null || officialAwayGoals == null) {
            return false;
        }

        int predictedOutcome = Integer.compare(predictedHomeGoals, predictedAwayGoals);
        int officialOutcome = Integer.compare(officialHomeGoals, officialAwayGoals);

        return predictedOutcome == officialOutcome;
    }

    /**
     *   calculo los puntos por acertar la posición final de cada equipo en su grupo.
     */
    @SuppressWarnings("unchecked")
    private Integer calculateGroupStandingsPoints(Pronostic pronostic, Map<String, Object> standingsPayload) {
        int totalStandingsPoints = 0;

        if (pronostic == null || pronostic.getGroupStage() == null) {
            return 0;
        }

        Map<String, Object> groupStage = pronostic.getGroupStage();
        Map<String, Object> standingsByGroup = (Map<String, Object>) groupStage.get("standingsByGroup");

        if (standingsByGroup == null || standingsByGroup.isEmpty()) {
            return 0;
        }

        List<Map<String, Object>> officialStandings = findOfficialStandingsArray(standingsPayload);

        if (officialStandings.isEmpty()) {
            return 0;
        }

        //   recorro grupo a grupo del pronóstico.
        for (Map.Entry<String, Object> entry : standingsByGroup.entrySet()) {
            String groupKey = entry.getKey();
            Object rawPredictedTable = entry.getValue();

            if (!(rawPredictedTable instanceof List<?> predictedTable)) {
                continue;
            }

            Map<String, Object> officialGroupStanding = findOfficialStandingByGroupKey(officialStandings, groupKey);
            if (officialGroupStanding == null) {
                continue;
            }

            List<Map<String, Object>> officialTable = scoreService.getMapList(officialGroupStanding, "table");

            //   comparo posición a posición entre el pronóstico y la clasificación oficial.
            for (int i = 0; i < predictedTable.size() && i < officialTable.size(); i++) {
                Object rawPredictedRow = predictedTable.get(i);

                if (!(rawPredictedRow instanceof Map<?, ?>)) {
                    continue;
                }

                Map<String, Object> predictedRow = (Map<String, Object>) rawPredictedRow;
                Map<String, Object> officialRow = officialTable.get(i);

                String predictedTeam = scoreService.getString(predictedRow, "team");

                Map<String, Object> officialTeam = scoreService.getMap(officialRow, "team");
                String officialTeamName = scoreService.getString(officialTeam, "name");

                if (predictedTeam == null || officialTeamName == null) {
                    continue;
                }

                if (Objects.equals(predictedTeam, officialTeamName)) {
                    //   sumo únicamente 15 puntos por acertar el puesto exacto del equipo.
                    totalStandingsPoints += 15;
                }
            }
        }

        return totalStandingsPoints;
    }

    /**
     *   localizo el array oficial de standings contemplando posibles nombres de clave.
     */
    private List<Map<String, Object>> findOfficialStandingsArray(Map<String, Object> standingsPayload) {
        List<Map<String, Object>> standings = scoreService.getMapList(standingsPayload, "standing");

        if (!standings.isEmpty()) {
            return standings;
        }

        standings = scoreService.getMapList(standingsPayload, "standings");

        if (!standings.isEmpty()) {
            return standings;
        }

        return List.of();
    }

    /**
     *   localizo la clasificación oficial de un grupo concreto a partir de su clave, por ejemplo A o B.
     */
    private Map<String, Object> findOfficialStandingByGroupKey(List<Map<String, Object>> officialStandings,
                                                               String groupKey) {

        if (groupKey == null || groupKey.isBlank()) {
            return null;
        }

        String expectedGroupName = "GROUP_" + groupKey;
        String expectedReadableGroupName = "Group " + groupKey;

        for (Map<String, Object> standing : officialStandings) {
            String group = scoreService.getString(standing, "group");

            if (Objects.equals(group, expectedGroupName) || Objects.equals(group, expectedReadableGroupName)) {
                return standing;
            }
        }

        return null;
    }

    /**
     *   calculo los puntos de las rondas eliminatorias teniendo en cuenta solo
     * qué equipo avanza en cada cruce, sin usar el número de goles.
     */
    @SuppressWarnings("unchecked")
    private Integer calculateKnockoutPoints(Pronostic pronostic, Map<String, Object> matchesPayload) {
        if (pronostic == null || pronostic.getKnockouts() == null) {
            return 0;
        }

        Map<String, Object> knockouts = pronostic.getKnockouts();
        List<Map<String, Object>> koMatches = scoreService.getMapList(knockouts, "koMatches");

        if (koMatches.isEmpty()) {
            return 0;
        }

        List<Map<String, Object>> officialMatches = scoreService.getMapList(matchesPayload, "matches");

        //   agrupo primero los partidos oficiales por stage para poder saber qué equipos aparecen en la siguiente ronda.
        Map<String, List<Map<String, Object>>> officialMatchesByStage = officialMatches.stream()
                .collect(Collectors.groupingBy(match -> {
                    String stage = scoreService.getString(match, "stage");
                    return stage == null ? "" : stage;
                }));

        int totalKnockoutPoints = 0;

        //   recorro todos los partidos KO del pronóstico.
        for (Map<String, Object> predictedKoMatch : koMatches) {
            totalKnockoutPoints += calculateSingleKnockoutMatchPoints(pronostic, predictedKoMatch, officialMatchesByStage);
        }

        return totalKnockoutPoints;
    }

    /**
     *   calculo los puntos de un único cruce eliminatorio del pronóstico.
     */
    private Integer calculateSingleKnockoutMatchPoints(Pronostic pronostic,
                                                       Map<String, Object> predictedKoMatch,
                                                       Map<String, List<Map<String, Object>>> officialMatchesByStage) {

        if (predictedKoMatch == null) {
            return 0;
        }

        String roundKey = getKnockoutRound(predictedKoMatch);

        if (roundKey == null || roundKey.isBlank()) {
            resetKoMatchPoints(predictedKoMatch);
            return 0;
        }

        if ("FINAL".equalsIgnoreCase(roundKey)) {
            return calculateFinalChampionPoints(pronostic, predictedKoMatch, officialMatchesByStage);
        }

        if ("THIRD_PLACE".equalsIgnoreCase(roundKey)) {
            return calculateThirdPlacePoints(predictedKoMatch, officialMatchesByStage);
        }

        Map<String, Object> home = scoreService.getMap(predictedKoMatch, "home");
        Map<String, Object> away = scoreService.getMap(predictedKoMatch, "away");

        String predictedHomeTeam = extractTeamNameFromKnockoutSide(home);
        String predictedAwayTeam = extractTeamNameFromKnockoutSide(away);

        if (predictedHomeTeam == null || predictedAwayTeam == null) {
            resetKoMatchPoints(predictedKoMatch);
            return 0;
        }

        int pointsPerQualifiedTeam = getPointsPerQualifiedTeam(roundKey);

        if (pointsPerQualifiedTeam == 0) {
            resetKoMatchPoints(predictedKoMatch);
            return 0;
        }

        List<String> teamsInSameRound = getQualifiedTeamsInStage(officialMatchesByStage.get(roundKey));

        if (teamsInSameRound.isEmpty()) {
            resetKoMatchPoints(predictedKoMatch);
            return 0;
        }

        int homePoints = teamsInSameRound.contains(predictedHomeTeam) ? pointsPerQualifiedTeam : 0;
        int awayPoints = teamsInSameRound.contains(predictedAwayTeam) ? pointsPerQualifiedTeam : 0;
        int totalPoints = homePoints + awayPoints;

        predictedKoMatch.put("homeMatchPoints", homePoints);
        predictedKoMatch.put("awayMatchPoints", awayPoints);
        predictedKoMatch.put("matchPoints", totalPoints);

        return totalPoints;
    }
    /**si no se puede puntuar una ronda, o falta info, o el partido es inválido, se deja el partido KO limpio y consistente*/
    private void resetKoMatchPoints(Map<String, Object> predictedKoMatch) {
        predictedKoMatch.put("homeMatchPoints", 0);
        predictedKoMatch.put("awayMatchPoints", 0);
        predictedKoMatch.put("matchPoints", 0);
    }

    /**
     *   obtengo la ronda del partido KO contemplando tanto la clave round
     * como una posible roundKey por compatibilidad futura.
     */
    private String getKnockoutRound(Map<String, Object> predictedKoMatch) {
        String round = scoreService.getString(predictedKoMatch, "round");

        if (round != null && !round.isBlank()) {
            return round;
        }

        return scoreService.getString(predictedKoMatch, "roundKey");
    }

    /**
     *   extraigo el nombre del equipo de un lado del cruce.
     */
    private String extractTeamNameFromKnockoutSide(Map<String, Object> side) {
        if (side == null) {
            return null;
        }

        Object directTeam = side.get("team");
        if (directTeam instanceof String teamName && !teamName.isBlank()) {
            return teamName.trim();
        }

        Map<String, Object> nestedTeam = scoreService.getMap(side, "team");
        String nestedName = scoreService.getString(nestedTeam, "name");

        if (nestedName != null && !nestedName.isBlank()) {
            return nestedName.trim();
        }

        return null;
    }

    /**
     *   traduzco la ronda del pronóstico a la siguiente stage oficial que debo consultar.
     */
    private String getNextOfficialStage(String roundKey) {
        if (roundKey == null) {
            return null;
        }

        return switch (roundKey) {
            case "LAST_32" -> "LAST_16";
            case "LAST_16" -> "QUARTER_FINALS";
            case "QUARTER_FINALS" -> "SEMI_FINALS";
            case "SEMI_FINALS" -> "FINAL";
            case "THIRD_PLACE" -> "THIRD_PLACE";
            case "FINAL" -> "FINAL";
            default -> null;
        };
    }

    /**
     *   devuelvo los puntos por cada equipo acertado según la ronda del pronóstico.
     */
    private int getPointsPerQualifiedTeam(String roundKey) {
        if (roundKey == null) {
            return 0;
        }

        return switch (roundKey) {
            case "LAST_32" -> 50;
            case "LAST_16" -> 100;
            case "QUARTER_FINALS" -> 200;
            case "SEMI_FINALS" -> 400;
            case "THIRD_PLACE" -> 600;
            case "FINAL" -> 800;
            default -> 0;
        };
    }

    /**
     *   obtengo los equipos que ya aparecen definidos en una ronda oficial.
     * Si todavía hay cruces con equipos a null, no los tengo en cuenta porque
     * esa clasificación aún no está resuelta y todavía no debo puntuarla.
     */
    private List<String> getQualifiedTeamsInStage(List<Map<String, Object>> stageMatches) {
        if (stageMatches == null || stageMatches.isEmpty()) {
            return List.of();
        }

        List<String> teams = new ArrayList<>();

        for (Map<String, Object> match : stageMatches) {
            Map<String, Object> homeTeam = scoreService.getMap(match, "homeTeam");
            Map<String, Object> awayTeam = scoreService.getMap(match, "awayTeam");

            String homeName = scoreService.getString(homeTeam, "name");
            String awayName = scoreService.getString(awayTeam, "name");

            if (homeName != null && !homeName.isBlank()) {
                teams.add(homeName.trim());
            }

            if (awayName != null && !awayName.isBlank()) {
                teams.add(awayName.trim());
            }
        }

        return teams;
    }

    /**
     *   calculo los puntos de la final comprobando únicamente quién es el campeón oficial.
     */
    private Integer calculateFinalChampionPoints(Pronostic pronostic,
                                                 Map<String, Object> predictedKoMatch,
                                                 Map<String, List<Map<String, Object>>> officialMatchesByStage) {

        private Integer calculateFinalChampionPoints(Pronostic pronostic,
                Map<String, Object> predictedKoMatch,
                Map<String, List<Map<String, Object>>> officialMatchesByStage) {

            List<Map<String, Object>> officialFinals = officialMatchesByStage.get("FINAL");

            if (officialFinals == null || officialFinals.isEmpty()) {
                resetKoMatchPoints(predictedKoMatch);
                return 0;
            }

            Map<String, Object> officialFinal = officialFinals.get(0);

            Map<String, Object> officialHomeTeam = scoreService.getMap(officialFinal, "homeTeam");
            Map<String, Object> officialAwayTeam = scoreService.getMap(officialFinal, "awayTeam");

            String officialHomeName = scoreService.getString(officialHomeTeam, "name");
            String officialAwayName = scoreService.getString(officialAwayTeam, "name");

            if (officialHomeName == null || officialAwayName == null) {
                resetKoMatchPoints(predictedKoMatch);
                return 0;
            }

            Map<String, Object> home = scoreService.getMap(predictedKoMatch, "home");
            Map<String, Object> away = scoreService.getMap(predictedKoMatch, "away");

            String predictedHomeTeam = extractTeamNameFromKnockoutSide(home);
            String predictedAwayTeam = extractTeamNameFromKnockoutSide(away);

            int homePoints = 0;
            int awayPoints = 0;

            // 800 puntos por cada finalista acertado
            if (Objects.equals(predictedHomeTeam, officialHomeName) || Objects.equals(predictedHomeTeam, officialAwayName)) {
                homePoints += 800;
            }

            if (Objects.equals(predictedAwayTeam, officialHomeName) || Objects.equals(predictedAwayTeam, officialAwayName)) {
                awayPoints += 800;
            }

            Map<String, Object> score = scoreService.getMap(officialFinal, "score");
            String winner = scoreService.getString(score, "winner");

            String championName = null;

            if ("HOME_TEAM".equalsIgnoreCase(winner)) {
                championName = officialHomeName;
            } else if ("AWAY_TEAM".equalsIgnoreCase(winner)) {
                championName = officialAwayName;
            }

            if (championName == null) {
                int totalPoints = homePoints + awayPoints;
                predictedKoMatch.put("homeMatchPoints", homePoints);
                predictedKoMatch.put("awayMatchPoints", awayPoints);
                predictedKoMatch.put("matchPoints", totalPoints);
                return totalPoints;
            }

            String predictedChampion = extractChampionTeamName(pronostic, predictedKoMatch);

            // 1500 extra por acertar el campeón
            if (predictedChampion != null && Objects.equals(predictedChampion, championName)) {
                if (Objects.equals(predictedHomeTeam, championName)) {
                    homePoints += 1500;
                } else if (Objects.equals(predictedAwayTeam, championName)) {
                    awayPoints += 1500;
                }
            }

            int totalPoints = homePoints + awayPoints;

            predictedKoMatch.put("homeMatchPoints", homePoints);
            predictedKoMatch.put("awayMatchPoints", awayPoints);
            predictedKoMatch.put("matchPoints", totalPoints);

            return totalPoints;
    }

    /**
     *   calculo los puntos del partido por el tercer puesto comprobando si los equipos
     * pronosticados aparecen realmente en ese partido oficial.
     */
    private Integer calculateThirdPlacePoints(Map<String, Object> predictedKoMatch,
                                              Map<String, List<Map<String, Object>>> officialMatchesByStage) {

        List<Map<String, Object>> officialThirdPlaceMatches = officialMatchesByStage.get("THIRD_PLACE");

        if (officialThirdPlaceMatches == null || officialThirdPlaceMatches.isEmpty()) {
            resetKoMatchPoints(predictedKoMatch);
            return 0;
        }

        Map<String, Object> officialThirdPlace = officialThirdPlaceMatches.get(0);

        Map<String, Object> officialHomeTeam = scoreService.getMap(officialThirdPlace, "homeTeam");
        Map<String, Object> officialAwayTeam = scoreService.getMap(officialThirdPlace, "awayTeam");

        String officialHomeName = scoreService.getString(officialHomeTeam, "name");
        String officialAwayName = scoreService.getString(officialAwayTeam, "name");

        Map<String, Object> home = scoreService.getMap(predictedKoMatch, "home");
        Map<String, Object> away = scoreService.getMap(predictedKoMatch, "away");

        String predictedHomeTeam = extractTeamNameFromKnockoutSide(home);
        String predictedAwayTeam = extractTeamNameFromKnockoutSide(away);

        int homePoints = 0;
        int awayPoints = 0;

        if (Objects.equals(predictedHomeTeam, officialHomeName) || Objects.equals(predictedHomeTeam, officialAwayName)) {
            homePoints += 600;
        }

        if (Objects.equals(predictedAwayTeam, officialHomeName) || Objects.equals(predictedAwayTeam, officialAwayName)) {
            awayPoints += 600;
        }

        Map<String, Object> score = scoreService.getMap(officialThirdPlace, "score");
        String winner = scoreService.getString(score, "winner");

        String officialThirdPlaceWinner = null;

        if ("HOME_TEAM".equalsIgnoreCase(winner)) {
            officialThirdPlaceWinner = officialHomeName;
        } else if ("AWAY_TEAM".equalsIgnoreCase(winner)) {
            officialThirdPlaceWinner = officialAwayName;
        }

        String predictedThirdPlaceWinner = extractWinnerTeamName(predictedKoMatch);

        if (officialThirdPlaceWinner != null
                && predictedThirdPlaceWinner != null
                && Objects.equals(predictedThirdPlaceWinner, officialThirdPlaceWinner)) {

            if (Objects.equals(predictedHomeTeam, predictedThirdPlaceWinner)) {
                homePoints += 1000;
            } else if (Objects.equals(predictedAwayTeam, predictedThirdPlaceWinner)) {
                awayPoints += 1000;
            }
        }

        int totalPoints = homePoints + awayPoints;

        predictedKoMatch.put("homeMatchPoints", homePoints);
        predictedKoMatch.put("awayMatchPoints", awayPoints);
        predictedKoMatch.put("matchPoints", totalPoints);

        return totalPoints;
    }
    /**
     * Extraigo el nombre del equipo ganador de un partido KO del pronóstico.
     * El winner se almacena como un objeto dentro del partido, por lo que necesito
     * acceder a su campo "team" para obtener el nombre del equipo.
     * Si el winner no existe o no tiene un nombre válido, devuelvo null para evitar errores en la puntuación.
     */
    private String extractWinnerTeamName(Map<String, Object> predictedKoMatch) {
        if (predictedKoMatch == null) {
            return null;
        }

        Map<String, Object> winner = scoreService.getMap(predictedKoMatch, "winner");
        if (winner == null) {
            return null;
        }

        String winnerTeam = scoreService.getString(winner, "team");

        if (winnerTeam != null && !winnerTeam.isBlank()) {
            return winnerTeam.trim();
        }

        return null;
    }

    /**
     * Extraigo el nombre del campeón pronosticado.
     * Primero intento obtenerlo del campo "champion" del bloque de knockouts, ya que es la fuente más fiable.
     * Si no existe, hago fallback al partido de la final usando los equipos home/away para mantener compatibilidad con pronósticos antiguos.
     */
    /**
     *   extraigo el nombre del campeón pronosticado.
     * Primero intento obtenerlo del campo "champion" del bloque de knockouts,
     * ya que es la fuente más fiable.
     * Si no existe, hago fallback al partido de la final usando los equipos
     * home/away para mantener compatibilidad con pronósticos antiguos.
     */
    private String extractChampionTeamName(Pronostic pronostic,
                                           Map<String, Object> predictedKoMatch) {

        if (pronostic != null && pronostic.getKnockouts() != null) {
            Map<String, Object> champion = scoreService.getMap(pronostic.getKnockouts(), "champion");
            String championTeam = scoreService.getString(champion, "team");

            if (championTeam != null && !championTeam.isBlank()) {
                return championTeam.trim();
            }
        }

        //   fallback: usar equipos de la final
        Map<String, Object> home = scoreService.getMap(predictedKoMatch, "home");
        Map<String, Object> away = scoreService.getMap(predictedKoMatch, "away");

        String predictedHomeTeam = extractTeamNameFromKnockoutSide(home);
        String predictedAwayTeam = extractTeamNameFromKnockoutSide(away);

        if (predictedHomeTeam != null) {
            return predictedHomeTeam;
        }

        return predictedAwayTeam;
    }

    private Map<String, Object> findOfficialKnockoutMatchInSameRound(List<Map<String, Object>> officialMatchesInSameRound,
                                                                     String predictedHomeTeam,
                                                                     String predictedAwayTeam) {

        if (officialMatchesInSameRound == null || officialMatchesInSameRound.isEmpty()) {
            return null;
        }

        for (Map<String, Object> officialMatch : officialMatchesInSameRound) {
            Map<String, Object> officialHomeTeam = scoreService.getMap(officialMatch, "homeTeam");
            Map<String, Object> officialAwayTeam = scoreService.getMap(officialMatch, "awayTeam");

            String officialHomeName = scoreService.getString(officialHomeTeam, "name");
            String officialAwayName = scoreService.getString(officialAwayTeam, "name");

            if (officialHomeName == null || officialAwayName == null) {
                continue;
            }

            boolean sameOrder =
                    Objects.equals(predictedHomeTeam, officialHomeName)
                            && Objects.equals(predictedAwayTeam, officialAwayName);

            boolean reversedOrder =
                    Objects.equals(predictedHomeTeam, officialAwayName)
                            && Objects.equals(predictedAwayTeam, officialHomeName);

            if (sameOrder || reversedOrder) {
                return officialMatch;
            }
        }

        return null;
    }
}