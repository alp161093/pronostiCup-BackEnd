package com.pronosticup.backend.tournaments.service;

import com.pronosticup.backend.tournaments.integration.FootballDataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class TournamentSyncService {

    private final TournamentService tournamentService;
    private final FootballDataClient footballDataClient;

    public void syncAll() {
        syncMundial();
        syncEurocopa();
    }

    public void syncMundial() {
        syncTournamentStandings("MUNDIAL", footballDataClient::getWorldCupStandings);
        syncTournamentMatches("MUNDIAL", footballDataClient::getWorldCupMatches);
    }

    public void syncEurocopa() {
        syncTournamentStandings("EUROCOPA", footballDataClient::getEuroCupStandings);
        syncTournamentMatches("EUROCOPA", footballDataClient::getEuroCupMatches);
    }

    private void syncTournamentStandings(String tournament, Supplier<Map<String, Object>> payloadSupplier) {
        final String type = "standings";

        log.info("Iniciando sincronización torneo={} type={}", tournament, type);

        try {
            Map<String, Object> payload = payloadSupplier.get();
            tournamentService.saveOrReplaceSnapshot(tournament, type, payload);
            log.info("OK sincronización torneo={} type={} guardada en MongoDB", tournament, type);
        } catch (Exception ex) {
            log.error("KO sincronización torneo={} type={} error={}", tournament, type, ex.getMessage(), ex);
        }
    }

    private void syncTournamentMatches(String tournament, Supplier<Map<String, Object>> payloadSupplier) {
        final String type = "matches-knockouts";

        log.info("Iniciando sincronización torneo={} type={}", tournament, type);

        try {
            Map<String, Object> payload = payloadSupplier.get();
            tournamentService.saveOrReplaceSnapshot(tournament, type, payload);
            log.info("OK sincronización torneo={} type={} guardada en MongoDB", tournament, type);
        } catch (Exception ex) {
            log.error("KO sincronización torneo={} type={} error={}", tournament, type, ex.getMessage(), ex);
        }
    }
}