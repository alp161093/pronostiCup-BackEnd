package com.pronosticup.backend.scores.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pronosticup.backend.leagues.entity.League;
import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.leagues.repository.LeagueMemberRepository;
import com.pronosticup.backend.leagues.repository.LeagueRepository;
import com.pronosticup.backend.pronostics.entity.Pronostic;
import com.pronosticup.backend.pronostics.repository.PronosticRepository;
import com.pronosticup.backend.tournaments.model.TournamentSnapshotDocument;
import com.pronosticup.backend.tournaments.repository.TournamentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final TournamentSnapshotRepository tournamentSnapshotRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository leagueMemberRepository;
    private final PronosticRepository pronosticRepository;
    @Value("${app.scores.use-local-snapshots:false}")
    private boolean useLocalSnapshots;

    /**
     *   obtengo el snapshot de partidos y cruces del torneo indicado.
     */
    public TournamentSnapshotDocument getMatchesKnockoutsSnapshot(String tournament) {
        String snapshotId = resolveMatchesKnockoutsSnapshotId(tournament);

        if (useLocalSnapshots) {
            return loadLocalSnapshot(snapshotId);
        }

        return tournamentSnapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new RuntimeException("No existe el snapshot " + snapshotId));
    }

    /**
     *   obtengo el snapshot de clasificación oficial del torneo indicado.
     */
    public TournamentSnapshotDocument getStandingsSnapshot(String tournament) {
        String snapshotId = resolveStandingsSnapshotId(tournament);

        if (useLocalSnapshots) {
            return loadLocalSnapshot(snapshotId);
        }

        return tournamentSnapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new RuntimeException("No existe el snapshot " + snapshotId));
    }

    /**
     *   resuelvo el id del snapshot de partidos y cruces según el torneo.
     */
    private String resolveMatchesKnockoutsSnapshotId(String tournament) {
        return switch (normalizeTournament(tournament)) {
            case "mundial" -> "MUNDIAL_MATCHES_KNOCKOUTS";
            case "eurocopa" -> "EUROCOPA_MATCHES_KNOCKOUTS";
            default -> throw new RuntimeException("Torneo no soportado para matches/knockouts: " + tournament);
        };
    }

    /**
     *   resuelvo el id del snapshot de clasificación según el torneo.
     */
    private String resolveStandingsSnapshotId(String tournament) {
        return switch (normalizeTournament(tournament)) {
            case "mundial" -> "MUNDIAL_STANDINGS";
            case "eurocopa" -> "EUROCOPA_STANDINGS";
            default -> throw new RuntimeException("Torneo no soportado para standings: " + tournament);
        };
    }

    /**
     *   normalizo el nombre del torneo para trabajar siempre con el mismo formato.
     */
    public String normalizeTournament(String tournament) {
        if (tournament == null) {
            return "";
        }

        return tournament.trim().toLowerCase();
    }

    /**
     *   extraigo el payload de un snapshot y valido que no venga vacío.
     */
    public Map<String, Object> getPayloadOrFail(TournamentSnapshotDocument snapshot, String snapshotName) {
        if (snapshot == null) {
            throw new RuntimeException("El snapshot " + snapshotName + " es null");
        }

        Map<String, Object> payload = snapshot.getPayload();

        if (payload == null || payload.isEmpty()) {
            throw new RuntimeException("El payload del snapshot " + snapshotName + " está vacío");
        }

        return payload;
    }

    /**
     *   obtengo la fecha first del resultSet del snapshot de partidos.
     * Esta fecha me sirve para saber si toca calcular y también para filtrar ligas elegibles.
     */
    public Instant getFirstDateFromMatchesPayload(Map<String, Object> matchesPayload) {
        Map<String, Object> resultSet = getMap(matchesPayload, "resultSet");
        String firstDate = getString(resultSet, "first");

        if (firstDate == null || firstDate.isBlank()) {
            throw new RuntimeException("No existe payload.resultSet.first en el snapshot de matches");
        }

        return LocalDate.parse(firstDate).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /**
     *   compruebo si la fecha actual está dentro de la ventana válida de cálculo.
     * La ventana empieza en resultSet.first y termina 1 día después.
     */
    public boolean isInsideCalculationWindow(Instant firstDate) {
        Instant now = Instant.now();
        Instant endDate = firstDate.plusSeconds(24 * 60 * 60);

        return !now.isBefore(firstDate) && !now.isAfter(endDate);
    }

    /**
     *   traigo las ligas del torneo indicado creadas antes o en la fecha límite.
     */
    public List<League> getEligibleLeagues(String tournament, Instant limitDate) {
        return leagueRepository.findEligibleLeaguesByTournamentAndCreatedAt(
                normalizeTournament(tournament),
                limitDate
        );
    }

    /**
     *   traigo solo los miembros confirmados de una liga, porque solo ellos deben entrar en el cálculo.
     */
    public List<LeagueMember> getConfirmedLeagueMembers(String leagueId) {
        return leagueMemberRepository.findByLeagueIdAndConfirmedTrue(leagueId);
    }

    /**
     *   busco un pronóstico por su pronosticId y lanzo error si no existe.
     */
    public Pronostic getPronosticOrFail(String pronosticId) {
        return pronosticRepository.findByPronosticId(pronosticId)
                .orElseThrow(() -> new RuntimeException("No existe el pronóstico con id: " + pronosticId));
    }

    /**
     *   guardo el pronóstico ya recalculado con sus nuevos puntos.
     */
    public Pronostic savePronostic(Pronostic pronostic) {
        return pronosticRepository.save(pronostic);
    }

    /**
     *   convierto un valor a String de forma segura.
     */
    public String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }

        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     *   convierto un valor a Integer de forma segura.
     */
    public Integer getInteger(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }

        Object value = map.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     *   convierto un valor a mapa de forma segura.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }

        Object value = map.get(key);

        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }

        return null;
    }

    /**
     *   convierto un valor a lista de mapas de forma segura.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMapList(Map<String, Object> map, String key) {
        if (map == null) {
            return List.of();
        }

        Object value = map.get(key);

        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        if (value instanceof Map<?, ?> valueMap) {
            return new ArrayList<>((Collection<? extends Map<String, Object>>) valueMap.values());
        }


        return List.of();
    }

    private TournamentSnapshotDocument loadLocalSnapshot(String snapshotId) {
        try {
            String fileName = switch (snapshotId) {
                case "MUNDIAL_MATCHES_KNOCKOUTS" -> "test-snapshots/MUNDIAL_MATCHES_KNOCKOUTS.json";
                case "MUNDIAL_STANDINGS" -> "test-snapshots/standings_mundial.json";
                case "EUROCOPA_MATCHES_KNOCKOUTS" -> "test-snapshots/EUROCOPA_MATCHES_KNOCKOUTS.json";
                case "EUROCOPA_STANDINGS" -> "test-snapshots/EUROCOPA_STANDINGS.json";
                default -> throw new RuntimeException("No hay snapshot local para " + snapshotId);
            };

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.findAndRegisterModules();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

            if (inputStream == null) {
                throw new RuntimeException("No se encuentra el archivo " + fileName);
            }

            return objectMapper.readValue(inputStream, TournamentSnapshotDocument.class);

        } catch (Exception e) {
            throw new RuntimeException("Error cargando snapshot local " + snapshotId, e);
        }
    }
}