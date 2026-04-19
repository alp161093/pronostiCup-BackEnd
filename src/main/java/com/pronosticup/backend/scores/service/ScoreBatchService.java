package com.pronosticup.backend.scores.service;

import com.pronosticup.backend.leagues.entity.League;
import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.pronostics.entity.Pronostic;
import com.pronosticup.backend.tournaments.model.TournamentSnapshotDocument;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreBatchService {

    private static final Logger scoreBatchLogger = LoggerFactory.getLogger("SCORE_BATCH");

    private final ScoreService scoreService;

    public void calculateScoresBatchForAllSupportedTournaments() {
        scoreBatchLogger.info("[SCORE_BATCH] Inicio cálculo batch global");
        calculateScoresBatchForTournament("mundial");
        calculateScoresBatchForTournament("eurocopa");
        scoreBatchLogger.info("[SCORE_BATCH] Fin cálculo batch global");
    }

    public void calculateScoresBatchForTournament(String tournament) {
        String normalizedTournament = scoreService.normalizeTournament(tournament);

        scoreBatchLogger.info("[SCORE_BATCH] Iniciando batch de puntuación para torneo {}", normalizedTournament);

        TournamentSnapshotDocument matchesSnapshot = scoreService.getMatchesKnockoutsSnapshot(normalizedTournament);
        Map<String, Object> matchesPayload = scoreService.getPayloadOrFail(
                matchesSnapshot,
                normalizedTournament + "_MATCHES_KNOCKOUTS"
        );

        Instant firstDate = scoreService.getFirstDateFromMatchesPayload(matchesPayload);
        Instant lastDate = scoreService.getLastDateFromMatchesPayload(matchesPayload);

        boolean shouldCalculate = scoreService.isInsideCalculationWindow(firstDate, lastDate);
        //alp:IMPORTANTE QUITAR ESTE IF PORQUE SOLO SON PARA PRUEBAS EN CALIENTE
        /*if (!shouldCalculate && tournament.equals("mundial")) {
            shouldCalculate = true;
        }*/
        scoreBatchLogger.info(
                "[SCORE_BATCH] Ventana de cálculo torneo {} -> firstDate={} lastDate={} shouldCalculate={}",
                normalizedTournament,
                firstDate,
                lastDate,
                shouldCalculate
        );

        if (!shouldCalculate) {
            scoreBatchLogger.info(
                    "[SCORE_BATCH] No ejecuto el batch del torneo {} porque la fecha actual está fuera de la ventana válida",
                    normalizedTournament
            );
            return;
        }

        TournamentSnapshotDocument standingsSnapshot = scoreService.getStandingsSnapshot(normalizedTournament);
        Map<String, Object> standingsPayload = scoreService.getPayloadOrFail(
                standingsSnapshot,
                normalizedTournament + "_STANDINGS"
        );

        List<League> eligibleLeagues = scoreService.getEligibleLeagues(normalizedTournament, firstDate);

        scoreBatchLogger.info(
                "[SCORE_BATCH] Las ligas elegibles para puntuar del torneo {} son: {}",
                normalizedTournament,
                eligibleLeagues.stream().map(League::getId).toList()
        );

        for (League league : eligibleLeagues) {
            processLeague(normalizedTournament, league, matchesPayload, standingsPayload);
        }

        scoreBatchLogger.info("[SCORE_BATCH] Batch de puntuación finalizado correctamente para torneo {}", normalizedTournament);
    }

    private void processLeague(String tournament,
                               League league,
                               Map<String, Object> matchesPayload,
                               Map<String, Object> standingsPayload) {

        if (league == null || league.getId() == null || league.getId().isBlank()) {
            return;
        }

        scoreBatchLogger.info("[SCORE_BATCH] Procesando liga {} del torneo {}", league.getId(), tournament);

        List<LeagueMember> confirmedMembers = scoreService.getConfirmedLeagueMembers(league.getId());

        scoreBatchLogger.info(
                "[SCORE_BATCH] La liga {} del torneo {} tiene los siguientes pronósticos confirmados: {}",
                league.getId(),
                tournament,
                confirmedMembers.stream()
                        .map(LeagueMember::getPronosticId)
                        .filter(Objects::nonNull)
                        .toList()
        );

        for (LeagueMember member : confirmedMembers) {
            processLeagueMember(tournament, member, matchesPayload, standingsPayload);
        }
    }

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
            Pronostic pronostic = scoreService.getPronosticOrFail(pronosticId);

            Integer totalPoints = calculatePronosticTotalPoints(pronostic, matchesPayload, standingsPayload);

            pronostic.setTotalPoints(totalPoints);
            scoreService.savePronostic(pronostic);

            scoreBatchLogger.info(
                    "[SCORE_BATCH] Pronóstico {} recalculado correctamente con {} puntos para torneo {}",
                    pronosticId,
                    totalPoints,
                    tournament
            );
        } catch (Exception e) {
            scoreBatchLogger.error(
                    "[SCORE_BATCH] Error recalculando el pronóstico {} del torneo {}: {}",
                    pronosticId,
                    tournament,
                    e.getMessage(),
                    e
            );
        }
    }

    private Integer calculatePronosticTotalPoints(Pronostic pronostic,
                                                  Map<String, Object> matchesPayload,
                                                  Map<String, Object> standingsPayload) {

        int groupMatchPoints = calculateGroupStageMatchPoints(pronostic, matchesPayload);
        int groupStandingsPoints = calculateGroupStandingsPoints(pronostic, standingsPayload);
        int knockoutPoints = calculateKnockoutPoints(pronostic, matchesPayload);

        return groupMatchPoints + groupStandingsPoints + knockoutPoints;
    }

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

        for (Map.Entry<String, Object> entry : matchesByGroup.entrySet()) {
            Object rawMatches = entry.getValue();

            if (!(rawMatches instanceof List<?> predictedMatches)) {
                continue;
            }

            for (Object rawPredictedMatch : predictedMatches) {
                if (!(rawPredictedMatch instanceof Map<?, ?>)) {
                    continue;
                }

                Map<String, Object> predictedMatch = (Map<String, Object>) rawPredictedMatch;

                int pointsForMatch = calculateSingleGroupMatchPoints(predictedMatch, officialMatches);

                predictedMatch.put("matchPoints", pointsForMatch);

                totalGroupMatchPoints += pointsForMatch;
            }
        }

        return totalGroupMatchPoints;
    }

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

        Map<String, Object> officialMatch = findOfficialGroupMatch(officialMatches, officialGroup, homeName, awayName);

        if (officialMatch == null) {
            return 0;
        }

        Map<String, Object> score = scoreService.getMap(officialMatch, "score");
        Map<String, Object> fullTime = scoreService.getMap(score, "fullTime");

        Integer officialHomeGoals = scoreService.getInteger(fullTime, "home");
        Integer officialAwayGoals = scoreService.getInteger(fullTime, "away");

        if (officialHomeGoals == null || officialAwayGoals == null) {
            return 0;
        }

        int points = 0;

        if (sameMatchOutcome(predictedHomeGoals, predictedAwayGoals, officialHomeGoals, officialAwayGoals)) {
            points += 10;
        }

        if (Objects.equals(predictedHomeGoals, officialHomeGoals)) {
            points += 3;
        }

        if (Objects.equals(predictedAwayGoals, officialAwayGoals)) {
            points += 3;
        }

        if (Objects.equals(predictedHomeGoals, officialHomeGoals)
                && Objects.equals(predictedAwayGoals, officialAwayGoals)) {
            points += 5;
        }

        return points;
    }

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
                    totalStandingsPoints += 15;
                }
            }
        }

        return totalStandingsPoints;
    }

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

        Map<String, List<Map<String, Object>>> officialMatchesByStage = officialMatches.stream()
                .collect(Collectors.groupingBy(match -> {
                    String stage = scoreService.getString(match, "stage");
                    return stage == null ? "" : stage;
                }));

        int totalKnockoutPoints = 0;

        for (Map<String, Object> predictedKoMatch : koMatches) {
            totalKnockoutPoints += calculateSingleKnockoutMatchPoints(pronostic, predictedKoMatch, officialMatchesByStage);
        }

        return totalKnockoutPoints;
    }

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

    private void resetKoMatchPoints(Map<String, Object> predictedKoMatch) {
        predictedKoMatch.put("homeMatchPoints", 0);
        predictedKoMatch.put("awayMatchPoints", 0);
        predictedKoMatch.put("matchPoints", 0);
    }

    private String getKnockoutRound(Map<String, Object> predictedKoMatch) {
        String round = scoreService.getString(predictedKoMatch, "round");

        if (round != null && !round.isBlank()) {
            return round;
        }

        return scoreService.getString(predictedKoMatch, "roundKey");
    }

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

    private String extractChampionTeamName(Pronostic pronostic,
                                           Map<String, Object> predictedKoMatch) {

        if (pronostic != null && pronostic.getKnockouts() != null) {
            Map<String, Object> champion = scoreService.getMap(pronostic.getKnockouts(), "champion");
            String championTeam = scoreService.getString(champion, "team");

            if (championTeam != null && !championTeam.isBlank()) {
                return championTeam.trim();
            }
        }

        Map<String, Object> home = scoreService.getMap(predictedKoMatch, "home");
        Map<String, Object> away = scoreService.getMap(predictedKoMatch, "away");

        String predictedHomeTeam = extractTeamNameFromKnockoutSide(home);
        String predictedAwayTeam = extractTeamNameFromKnockoutSide(away);

        if (predictedHomeTeam != null) {
            return predictedHomeTeam;
        }

        return predictedAwayTeam;
    }
}