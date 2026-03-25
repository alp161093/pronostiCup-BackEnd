package com.pronosticup.backend.leagues.service;

import com.pronosticup.backend.leagues.controller.dto.request.CreateLeagueRequest;
import com.pronosticup.backend.leagues.controller.dto.response.LeagueResponse;
import com.pronosticup.backend.leagues.controller.dto.response.MyLeagueResponse;
import com.pronosticup.backend.leagues.controller.dto.response.PendingConfirmationResponse;
import com.pronosticup.backend.leagues.entity.League;
import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.leagues.repository.LeagueMemberRepository;
import com.pronosticup.backend.leagues.repository.LeagueRepository;
import com.pronosticup.backend.leagues.repository.MyLeagueRow;
import com.pronosticup.backend.leagues.repository.PendingConfirmationRow;
import com.pronosticup.backend.pronostics.controller.dto.response.MyPronosticResponse;
import com.pronosticup.backend.tournaments.model.TournamentSnapshotDocument;
import com.pronosticup.backend.users.entity.User;
import com.pronosticup.backend.users.repository.UserRepository;
import com.pronosticup.backend.tournaments.repository.TournamentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository leagueMemberRepository;
    private final UserRepository userRepository;
    private final TournamentSnapshotRepository tournamentSnapshotRepository;

    @Transactional
    public LeagueResponse createLeague(CreateLeagueRequest req, Long ownerUserId) {
        if (leagueRepository.existsById(req.idLeague())) {
            throw new RuntimeException("League already exists");
        }

        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        League league = new League();
        league.setId(req.idLeague());
        league.setName(req.name());
        league.setTournament(req.tournament());
        league.setOwner(owner);

        leagueRepository.save(league);

        return new LeagueResponse(
                league.getId(),
                league.getName(),
                league.getTournament(),
                owner.getUsername()
        );
    }

    @Transactional
    public void addMember(String leagueId, Long userId, String role) {
        if (leagueId == null || leagueId.isBlank()) throw new RuntimeException("leagueId required");
        if (userId == null) throw new RuntimeException("userId required");

        String id = leagueId.trim();
        if (!leagueRepository.existsById(id)) throw new RuntimeException("League not found");

        String r = (role == null || role.isBlank()) ? "MEMBER" : role.trim().toUpperCase();
        if (!r.equals("OWNER") && !r.equals("MEMBER")) throw new RuntimeException("Invalid role");

        if (leagueMemberRepository.existsByLeagueIdAndUserId(id, userId)) return;

        leagueMemberRepository.save(LeagueMember.builder()
                .leagueId(id)
                .userId(userId)
                .role(r)
                .build());
    }

    public LeagueResponse getLeagueById(String leagueId) {

        if (leagueId == null || leagueId.isBlank()) {
            return null; // 👈 no lanzamos error
        }

        League league = leagueRepository.findById(leagueId.trim())
                .orElse(null);

        if (league == null) {
            return null; // si no existe → null
        }

        // username del owner
        String ownerUsername = userRepository.findById(league.getOwner().getId())
                .map(User::getUsername)
                .orElse("unknown");

        return new LeagueResponse(
                league.getId(),
                league.getName(),
                league.getTournament(),
                ownerUsername
        );
    }

    public List<MyLeagueResponse> getMyLeagues(Long userId) {

        List<MyLeagueRow> rows = leagueMemberRepository.findMyLeaguesRows(userId);
        if (rows.isEmpty()) return List.of();

        Map<String, List<MyLeagueRow>> byLeague =
                rows.stream().collect(Collectors.groupingBy(MyLeagueRow::getLeagueId));

        List<MyLeagueResponse> result = new ArrayList<>();

        // mini record interno para dedupe (alias + confirmed)
        record PronosticMini(String alias, boolean confirmed) {}

        for (var entry : byLeague.entrySet()) {

            String leagueId = entry.getKey();
            List<MyLeagueRow> leagueRows = entry.getValue();
            MyLeagueRow first = leagueRows.get(0);

            String leagueName = first.getLeagueName();
            String tournament = first.getTournament();

            // role del usuario en esa liga
            String role = leagueRows.stream().anyMatch(r -> "OWNER".equals(r.getRole()))
                    ? "OWNER"
                    : "MEMBER";

            // ✅ calculamos una sola vez si el torneo sigue siendo editable
            // Todos los pronósticos de la liga pertenecen al mismo torneo
            String firstMatchDate = getFirstMatchDateByTournament(tournament);
            boolean editable = isBeforeTournamentStart(firstMatchDate);

            // ✅ pronósticos del usuario en esa liga (id + alias + confirmed)
            // dedupe por pronosticId (por si hay filas repetidas)
            Map<String, PronosticMini> pronosticMap = new LinkedHashMap<>();

            for (MyLeagueRow r : leagueRows) {
                String pid = r.getPronosticId();
                if (pid == null || pid.isBlank()) continue;

                // confirmed puede venir null (projection). Lo normalizamos a false
                boolean confirmed = Boolean.TRUE.equals(r.getConfirmed());
                String alias = r.getPronosticAlias();

                // Si ya existe, no lo pisamos.
                // Pero si el primero no tenía alias y este sí, lo completamos
                PronosticMini existing = pronosticMap.get(pid);
                if (existing == null) {
                    pronosticMap.put(pid, new PronosticMini(alias, confirmed));
                } else {
                    String finalAlias = existing.alias();
                    if ((finalAlias == null || finalAlias.isBlank()) && alias != null && !alias.isBlank()) {
                        pronosticMap.put(pid, new PronosticMini(alias, existing.confirmed()));
                    }
                }
            }

            List<MyPronosticResponse> pronostics = pronosticMap.entrySet().stream()
                    .map(e -> new MyPronosticResponse(
                            e.getKey(),
                            e.getValue().alias(),
                            e.getValue().confirmed(),
                            editable
                    ))
                    .toList();

            // pendientes solo si OWNER
            List<PendingConfirmationResponse> pending = List.of();

            if ("OWNER".equals(role)) {
                List<PendingConfirmationRow> pendingRows =
                        leagueMemberRepository.findPendingByLeague(leagueId);

                pending = pendingRows.stream()
                        .map(p -> new PendingConfirmationResponse(
                                p.getUsername(),
                                p.getPronosticId(),
                                p.getPronosticAlias()
                        ))
                        .toList();
            }

            result.add(new MyLeagueResponse(
                    leagueId,
                    leagueName,
                    tournament,
                    role,
                    pronostics,
                    pending
            ));
        }

        return result;
    }

    private String getFirstMatchDateByTournament(String tournament) {
        // Se determina el ID del documento en MongoDB según el torneo
        String documentId;

        if ("eurocopa".equalsIgnoreCase(tournament)) {
            documentId = "EUROCOPA_MATCHES_KNOCKOUTS";
        } else {
            documentId = "MUNDIAL_MATCHES_KNOCKOUTS";
        }

        // Se busca el documento en MongoDB mediante el repository
        Optional<TournamentSnapshotDocument> docOpt =
                tournamentSnapshotRepository.findById(documentId);

        // Si no existe el documento significa que todavía no se ha sincronizado el torneo con la API externa
        if (docOpt.isEmpty()) {
            throw new IllegalStateException("No se encontró snapshot para torneo: " + tournament);
        }

        //Obtener el payload del documento.
        Map<String, Object> payload = docOpt.get().getPayload();

        if (payload == null) {
            throw new IllegalStateException("Payload vacío para torneo: " + tournament);
        }

        //Dentro del payload existe un array llamado "matches" que contiene todos los partidos del torneo
        List<Map<String, Object>> matches =
                (List<Map<String, Object>>) payload.get("matches");

        if (matches == null || matches.isEmpty()) {
            throw new IllegalStateException("No hay partidos disponibles para torneo: " + tournament);
        }

        // Obtengo el primer partido del array que es el partido innagural
        Map<String, Object> firstMatch = matches.get(0);

        // Se extrae la fecha del partido (campo utcDate)
        Object utcDate = firstMatch.get("utcDate");

        if (utcDate == null) {
            throw new IllegalStateException("El primer partido no tiene fecha utcDate");
        }
        return utcDate.toString();
    }

    private boolean isBeforeTournamentStart(String firstMatchDate) {
        try {
            Instant tournamentStart = Instant.parse(firstMatchDate);
            Instant dateNow = Instant.now();
            return dateNow.isBefore(tournamentStart);
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Compruebo si todavía se permite crear una liga o unirse a una existente
     * para el torneo indicado.
     * De momento dejo el método preparado para conectar después con la lógica
     * real que leerá el snapshot del torneo y calculará la fecha límite.
     */
    public boolean canJoinOrCreateLeague(String tournament) {
        //valido el nombre y si no corresponde con ningun torneo mando exception
        String normalizedTournament = validateTournamentRequired(tournament);

        //obtengo la fecha del primer partido
        var firstMatchDate = getFirstMatchDateByTournament(normalizedTournament);
        //obtengo la fecha del ultimo partido
        var lastMatchDate = getLastMatchDateByTournament(normalizedTournament);

        // Parseo a Instant para comparar correctamente con zona horaria UTC
        Instant now = Instant.now();
        Instant firstMatch = Instant.parse(firstMatchDate);
        //al ultimo partido se le suma un día más para asegurar que haya terminado el partido
        Instant lastMatch = Instant.parse(lastMatchDate).plus(1, ChronoUnit.DAYS);

        // Evaluo si estamos fuera del rango del torneo
        boolean isBeforeTournament = now.isBefore(firstMatch);
        boolean isAfterTournament = now.isAfter(lastMatch);

        return isBeforeTournament || isAfterTournament;
    }

    /**
     * Valido que el torneo venga informado y con un valor soportado por la aplicación.
     */
    private String validateTournamentRequired(String tournament) {
        if (tournament == null || tournament.isBlank()) {
            throw new IllegalArgumentException("tournament required");
        }

        String normalizedTournament = tournament.trim().toLowerCase();

        if (!normalizedTournament.equals("mundial") && !normalizedTournament.equals("eurocopa")) {
            throw new IllegalArgumentException("invalid tournament");
        }
        return normalizedTournament;
    }
    /**
     * Obtengo la fecha del último partido del torneo (la final).
     * Reutilizo la misma lógica que el primer partido pero accediendo
     * al último elemento del array de matches.
     */
    private String getLastMatchDateByTournament(String tournament) {

        // Se determina el ID del documento en MongoDB según el torneo
        String documentId;

        if ("eurocopa".equalsIgnoreCase(tournament)) {
            documentId = "EUROCOPA_MATCHES_KNOCKOUTS";
        } else {
            documentId = "MUNDIAL_MATCHES_KNOCKOUTS";
        }

        // Se busca el documento en MongoDB mediante el repository
        Optional<TournamentSnapshotDocument> docOpt =
                tournamentSnapshotRepository.findById(documentId);

        // Si no existe el documento significa que todavía no se ha sincronizado el torneo con la API externa
        if (docOpt.isEmpty()) {
            throw new IllegalStateException("No se encontró snapshot para torneo: " + tournament);
        }

        // Obtener el payload del documento
        Map<String, Object> payload = docOpt.get().getPayload();

        if (payload == null) {
            throw new IllegalStateException("Payload vacío para torneo: " + tournament);
        }

        // Obtener lista de partidos
        List<Map<String, Object>> matches =
                (List<Map<String, Object>>) payload.get("matches");

        if (matches == null || matches.isEmpty()) {
            throw new IllegalStateException("No hay partidos disponibles para torneo: " + tournament);
        }

        // Obtengo el último partido del array (la final)
        Map<String, Object> lastMatch = matches.get(matches.size() - 1);

        // Se extrae la fecha del partido
        Object utcDate = lastMatch.get("utcDate");

        if (utcDate == null) {
            throw new IllegalStateException("El último partido no tiene fecha utcDate");
        }

        return utcDate.toString();
    }


}

