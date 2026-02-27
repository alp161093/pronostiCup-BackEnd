package com.pronosticup.backend.pronostics.service;

import com.pronosticup.backend.leagues.entity.League;
import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.leagues.repository.LeagueMemberRepository;
import com.pronosticup.backend.leagues.repository.LeagueRepository;
import com.pronosticup.backend.pronostics.controller.dto.request.SavePronosticRequest;
import com.pronosticup.backend.pronostics.controller.dto.response.SavePronosticResponse;
import com.pronosticup.backend.pronostics.entity.Pronostic;
import com.pronosticup.backend.pronostics.repository.PronosticRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PronosticService {

    private final LeagueRepository leagueRepository;             // Postgres
    private final LeagueMemberRepository leagueMemberRepository; // Postgres
    private final PronosticRepository pronosticRepository;       // Mongo

    public SavePronosticResponse saveFirstTime(SavePronosticRequest req) {

        // 1) Validaciones mínimas
        if (req == null || req.meta() == null) throw new RuntimeException("meta required");

        String leagueId = req.meta().leagueId();
        String leagueName = req.meta().leagueName();
        String tournament = req.meta().tournament();
        Long userId = longVal(req.meta().userId());
        String alias = req.meta().pronosticAlias();
        if (alias != null) alias = alias.trim();
        if (alias != null && alias.isBlank()) alias = null;

        if (leagueId == null || leagueId.isBlank()) throw new RuntimeException("leagueId required");
        if (userId == null) throw new RuntimeException("userId required");
        if (tournament == null || tournament.isBlank()) throw new RuntimeException("tournament required");

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
        //todo-alp: aqui tenemos que comprobar el owner de la liga, y si es asi el role ponemos menber o owner
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
                "confirmed", confirmed
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
    }

    @Transactional
    public void rejectPronostic(String leagueId, String pronosticId, Long ownerUserId) {

        // 1) Validar owner
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new RuntimeException("League not found"));

        if (!Objects.equals(league.getOwner().getId(), ownerUserId)) {
            throw new RuntimeException("Only owner can reject");
        }

        // 2) Postgres: borrar fila del league_members (esa participación)
        LeagueMember lm = leagueMemberRepository.findByLeagueIdAndPronosticId(leagueId, pronosticId)
                .orElseThrow(() -> new RuntimeException("LeagueMember not found for pronostic"));

        leagueMemberRepository.delete(lm);

        // 3) Mongo: borrar documento del pronóstico
        pronosticRepository.deleteByPronosticId(pronosticId);
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
