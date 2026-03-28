package com.pronosticup.backend.pronostics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pronosticup.backend.leagues.entity.League;
import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.leagues.repository.LeagueMemberRepository;
import com.pronosticup.backend.leagues.repository.LeagueRepository;
import com.pronosticup.backend.pronostics.controller.dto.request.DecryptPronosticRequest;
import com.pronosticup.backend.pronostics.controller.dto.request.SavePronosticRequest;
import com.pronosticup.backend.pronostics.controller.dto.request.UpdatePronosticRequest;
import com.pronosticup.backend.pronostics.controller.dto.response.*;
import com.pronosticup.backend.pronostics.entity.Pronostic;
import com.pronosticup.backend.pronostics.repository.PronosticRepository;
import com.pronosticup.backend.security.service.EncryptionService;
import com.pronosticup.backend.tournaments.model.TournamentSnapshotDocument;
import com.pronosticup.backend.users.entity.User;
import com.pronosticup.backend.users.repository.UserRepository;
import com.pronosticup.backend.tournaments.repository.TournamentSnapshotRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PronosticService {

    private final LeagueRepository leagueRepository;             // Postgres
    private final LeagueMemberRepository leagueMemberRepository; // Postgres
    private final PronosticRepository pronosticRepository;       // Mongo
    private final UserRepository userRepository;
    private final TournamentSnapshotRepository tournamentSnapshotRepository;
    private final PronosticReceiptService pronosticReceiptService;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public SavePronosticResponse saveFirstTime(SavePronosticRequest req) {

        // 1) Validaciones mínimas
        if (req == null || req.meta() == null)
            throw new RuntimeException("meta required");

        String leagueId = req.meta().leagueId();
        String leagueName = req.meta().leagueName();
        String tournament = req.meta().tournament();
        Long userId = longVal(req.meta().userId());
        String alias = req.meta().pronosticAlias();
        Integer  totalPoints = req.meta().totalPoints();

        if (alias != null)
            alias = alias.trim();

        if (alias != null && alias.isBlank())
            alias = null;

        if (leagueId == null || leagueId.isBlank())
            throw new RuntimeException("leagueId required");

        if (userId == null)
            throw new RuntimeException("userId required");

        if (tournament == null || tournament.isBlank())
            throw new RuntimeException("tournament required");

        if (totalPoints == null)
            totalPoints = 0;

        leagueId = leagueId.trim();

        // 2) Validar liga existe (Postgres)
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));


        // 3) Generar pronosticId (permitiendo varios por misma liga/usuario)
        String pronosticId = generatePronosticId(tournament, leagueId, userId);
        while (pronosticRepository.existsByPronosticId(pronosticId)) {
            pronosticId = generatePronosticId(tournament, leagueId, userId);
        }
        //4) Comprobar si el usuario es owner o menber, para ello se comprueba que usuario es el owner de la liga, si tiene el mismo id el usuario owner que este entonces se le pone owner
        boolean isOwner = Objects.equals(league.getOwner().getId(), userId);
        String role = isOwner ? "OWNER" : "MEMBER";
        boolean confirmed = isOwner; //si es owner entonces esta confirmado si no no

        // 4) Guardar Mongo
        Map<String, Object> metaMap = Map.of(
                "leagueId", leagueId,
                "leagueName", leagueName,
                "userId", userId,
                "tournament", tournament,
                "pronosticId", pronosticId,
                "confirmed", confirmed,
                "totalPoints", totalPoints

        );
        Instant now = Instant.now();

        Pronostic doc = Pronostic.builder()
                .pronosticId(pronosticId)
                .leagueId(leagueId)
                .leagueName(leagueName)
                .tournament(tournament)
                .userId(userId)
                .confirmed(confirmed)
                .createdAt(now)
                .updatedAt(now)
                .pronosticAlias(alias)
                .totalPoints(totalPoints)
                .groupStage(req.groupStage() == null ? Map.of() : req.groupStage())
                .knockouts(req.knockouts() == null ? Map.of() : req.knockouts())
                .build();

        pronosticRepository.save(doc);

        // 5) Guardar relación en Postgres league_members con ESTE pronosticId
        //    (si ya existía membership “genérica”, aquí igual te interesa mantener solo esta tabla como “pronostic entries”)
        //    Role: si el user ya es OWNER en esa liga, deberías recuperarlo de otra tabla/columna.
        //    Como de momento no lo tenemos, guardamos MEMBER por defecto:



        LeagueMember lm = LeagueMember.builder()
                .leagueId(leagueId)
                .userId(userId)
                .pronosticId(pronosticId)
                .role(role)
                .confirmed(confirmed)
                .pronosticAlias(alias)
                .build();

        leagueMemberRepository.save(lm);

        /**
         * Envío el comprobante cifrado solo cuando el pronóstico y la relación con la liga ya se han guardado correctamente.
         * Si el correo falla no revierto el guardado, solo dejo trazado el error.
         */
        try {
            String userEmail = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"))
                    .getEmail();

            pronosticReceiptService.sendEncryptedPronosticReceipt(
                    doc,
                    userEmail,
                    leagueName,
                    tournament,
                    alias
            );

        } catch (Exception e) {
            throw new RuntimeException("No he podido enviar el comprobante por email para pronosticId=" + pronosticId);
        }

        return new SavePronosticResponse(pronosticId, false);
    }


    @Transactional
    public void confirmPronostic(String leagueId, String pronosticId, Long ownerUserId) {

        // 1) Validar owner: la liga existe y ownerUserId coincide
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        if (!Objects.equals(league.getOwner().getId(), ownerUserId)) {
            throw new RuntimeException("Only owner can confirm");
        }

        // 2) Postgres: marcar confirmed=true en league_members de ese pronóstico
        LeagueMember lm = leagueMemberRepository.findByLeagueIdAndPronosticId(leagueId, pronosticId)
                .orElseThrow(() -> new RuntimeException("LeagueMember not found for pronostic"));

        lm.setConfirmed(true);
        leagueMemberRepository.save(lm);

        // 3) Mongo: mantener coherencia
        Pronostic doc = pronosticRepository.findByPronosticId(pronosticId)
                .orElseThrow(() -> new RuntimeException("Pronostic not found in Mongo"));

        doc.setConfirmed(true);
        doc.setUpdatedAt(Instant.now());
        pronosticRepository.save(doc);

        //Envío el correo de aceptación cuando la confirmación  ya se ha guardado correctamente en PostgreSQL y Mongo.
        try {
            String userEmail = userRepository.findById(doc.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"))
                    .getEmail();

            pronosticReceiptService.sendPronosticAcceptedEmail(
                    userEmail,
                    doc.getLeagueName(),
                    doc.getTournament(),
                    doc.getPronosticAlias()
            );

        } catch (Exception e) {
            throw new RuntimeException("No he podido enviar el email de aceptación para pronosticId= "+ pronosticId);
        }
    }

    @Transactional
    public void rejectPronostic(String leagueId, String pronosticId, Long ownerUserId) {

        // 1) Validar owner
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        if (!Objects.equals(league.getOwner().getId(), ownerUserId)) {
            throw new RuntimeException("Only owner can reject");
        }

        // 2) Postgres: obtener fila del league_members
        LeagueMember lm = leagueMemberRepository.findByLeagueIdAndPronosticId(leagueId, pronosticId)
                .orElseThrow(() -> new RuntimeException("LeagueMember not found for pronostic"));

        // 3) Mongo: obtener documento antes de borrarlo para reutilizar sus datos en el email
        Pronostic doc = pronosticRepository.findByPronosticId(pronosticId)
                .orElseThrow(() -> new RuntimeException("Pronostic not found in Mongo"));

        String leagueName = doc.getLeagueName();
        String tournament = doc.getTournament();
        String alias = doc.getPronosticAlias();
        Long userId = doc.getUserId();

        // 4) Postgres: borrar fila del league_members (esa participación)
        leagueMemberRepository.delete(lm);

        // 5) Mongo: borrar documento del pronóstico
        pronosticRepository.deleteByPronosticId(pronosticId);

        /**
         * Envío el correo de rechazo cuando el pronóstico
         * ya ha sido eliminado correctamente.
         */
        try {
            String userEmail = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"))
                    .getEmail();

            pronosticReceiptService.sendPronosticRejectedEmail(
                    userEmail,
                    leagueName,
                    tournament,
                    alias
            );

        } catch (Exception e) {
            throw new RuntimeException("No he podido enviar el email de rechazo para pronosticId= " + pronosticId);
        }
    }

    public PronosticDetailResponse getPronosticDetail(String pronosticId) {
        Pronostic pronostic = pronosticRepository.findByPronosticId(pronosticId)
                .orElseThrow(() -> new RuntimeException("Pronóstico no encontrado: " + pronosticId));

        String firstMatchDate = getFirstMatchDateByTournament(pronostic.getTournament());

        boolean editable = isBeforeTournamentStart(firstMatchDate);
        boolean canEditAlias = editable;

        return new PronosticDetailResponse(
                new PronosticDetailResponse.MetaResponse(
                        pronostic.getPronosticId(),
                        pronostic.getPronosticAlias(),
                        pronostic.getLeagueId(),
                        pronostic.getLeagueName(),
                        pronostic.getTournament(),
                        pronostic.isConfirmed(),
                        editable,
                        canEditAlias,
                        firstMatchDate,
                        pronostic.getTotalPoints()
                ),
                pronostic.getGroupStage(),
                pronostic.getKnockouts()
        );
    }

    public UpdatePronosticResponse updatePronostic(String pronosticId, UpdatePronosticRequest request) {
        Pronostic pronostic = pronosticRepository.findByPronosticId(pronosticId)
                .orElseThrow(() -> new RuntimeException("Pronóstico no encontrado: " + pronosticId));

        String firstMatchDate = getFirstMatchDateByTournament(pronostic.getTournament());
        boolean editable = isBeforeTournamentStart(firstMatchDate);

        if (!editable) {
            throw new RuntimeException("El pronóstico ya no puede editarse porque el torneo ha comenzado.");
        }

        // Guardamos el alias final para reutilizarlo también en PostgreSQL
        String newAlias = pronostic.getPronosticAlias();

        if (request.pronosticAlias() != null && !request.pronosticAlias().isBlank()) {
            newAlias = request.pronosticAlias().trim();
            pronostic.setPronosticAlias(newAlias);
        }

        if (request.groupStage() != null) {
            pronostic.setGroupStage(request.groupStage());
        }

        if (request.knockouts() != null) {
            pronostic.setKnockouts(request.knockouts());
        }

        pronostic.setUpdatedAt(Instant.now());

        // 1. Guardar cambios en Mongo
        Pronostic saved = pronosticRepository.save(pronostic);

        // 2. Sincronizar alias en PostgreSQL (league_members)
        if (newAlias != null && !newAlias.isBlank()) {
            leagueMemberRepository.updatePronosticAliasByPronosticId(saved.getPronosticId(), newAlias);
        }

        /**
         * Envío el comprobante cifrado de la actualización una vez guardados
         * los cambios en Mongo y sincronizado el alias en PostgreSQL.
         * Si el email falla no rompo la actualización del pronóstico.
         */
        try {
            String userEmail = userRepository.findById(saved.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"))
                    .getEmail();

            pronosticReceiptService.sendUpdatedPronosticReceipt(
                    saved,
                    userEmail,
                    saved.getLeagueName(),
                    saved.getTournament(),
                    saved.getPronosticAlias()
            );

        } catch (Exception e) {
            throw new RuntimeException("No he podido enviar el comprobante de actualización para pronosticId = " + saved.getPronosticId());
        }

        return new UpdatePronosticResponse(
                saved.getPronosticId(),
                saved.getPronosticAlias(),
                saved.getUpdatedAt(),
                "Pronóstico actualizado correctamente"
        );
    }

    public List<PronosticClasificacionResponse> getLeagueClassification(String leagueId) {
        List<LeagueMember> members = leagueMemberRepository.findByLeagueIdAndConfirmedTrue(leagueId);

        List<PronosticClasificacionResponse> result = new ArrayList<>();

        for (LeagueMember member : members) {
            String pronosticId = member.getPronosticId();
            if (pronosticId == null || pronosticId.isBlank()) {
                continue;
            }

            Optional<Pronostic> pronosticOpt = pronosticRepository.findByPronosticId(pronosticId);
            if (pronosticOpt.isEmpty()) {
                continue;
            }

            Pronostic pronostic = pronosticOpt.get();

            String username = userRepository.findById(member.getUserId())
                    .map(User::getUsername)
                    .orElse("Usuario");

            Integer totalPoints = pronostic.getTotalPoints() != null ? pronostic.getTotalPoints() : 0;

            result.add(new PronosticClasificacionResponse(
                    pronosticId,
                    totalPoints,
                    username,
                    member.getPronosticAlias()
            ));
        }

        result.sort(Comparator.comparing(
                PronosticClasificacionResponse::totalPoints,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        return result;
    }

    public DeletePronosticResponse deletePronostic(String pronosticId) {

        Pronostic pronostic = pronosticRepository.findByPronosticId(pronosticId)
                .orElseThrow(() -> new RuntimeException("Pronóstico no encontrado: " + pronosticId));

        // 1. Borrar primero la referencia relacional en PostgreSQL
        leagueMemberRepository.deleteByPronosticId(pronosticId);

        // 2. Borrar después el documento en MongoDB
        pronosticRepository.delete(pronostic);

        return new DeletePronosticResponse(
                pronosticId,
                "Pronóstico eliminado correctamente"
        );
    }

    /**
     * Descifro un pronóstico recibido desde el frontend y valido
     * que el torneo del contenido coincida con el torneo indicado.
     */
    public DecryptPronosticResponse decryptPronostic(DecryptPronosticRequest request) {

        if (request == null) {
            throw new RuntimeException("request required");
        }

        if (request.encryptedPronostic() == null || request.encryptedPronostic().isBlank()) {
            throw new RuntimeException("encryptedPronostic required");
        }

        if (request.tournament() == null || request.tournament().isBlank()) {
            throw new RuntimeException("tournament required");
        }

        try {
            // 1. Descifro el texto recibido
            String decryptedJson = encryptionService.decrypt(request.encryptedPronostic().trim());

            // 2. Lo convierto al documento Pronostic real
            Pronostic pronostic = objectMapper.readValue(decryptedJson, Pronostic.class);

            if (pronostic == null) {
                throw new RuntimeException("No se ha podido reconstruir el pronóstico");
            }

            // 3. Valido que el torneo coincida con el que el usuario está usando
            String requestTournament = request.tournament().trim().toLowerCase();
            String pronosticTournament = pronostic.getTournament() == null
                    ? ""
                    : pronostic.getTournament().trim().toLowerCase();

            if (!requestTournament.equals(pronosticTournament)) {
                throw new RuntimeException("El pronóstico cifrado no pertenece al torneo seleccionado");
            }

            // 4. Devuelvo solo la parte que el frontend necesita para rellenar la vista
            return new DecryptPronosticResponse(
                    pronostic.getTournament(),
                    pronostic.getGroupStage(),
                    pronostic.getKnockouts()
            );

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se ha podido descifrar el pronóstico", e);
        }
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

    private String generatePronosticId(String tournament, String leagueId, Long userId) {
        return "PR-" + tournament + "-" + leagueId + "-" + userId + "-" + UUID.randomUUID();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long longVal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o).trim()); }
        catch (Exception e) { return null; }
    }
}
