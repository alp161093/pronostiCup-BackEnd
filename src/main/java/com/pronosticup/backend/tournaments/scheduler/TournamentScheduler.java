package com.pronosticup.backend.tournaments.scheduler;

import com.pronosticup.backend.tournaments.service.TournamentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        value = "app.schedulers.tournament-sync.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TournamentScheduler {

    private final TournamentSyncService tournamentSyncService;

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("[TOURNAMENT_SCHEDULER] Lanzando sync inicial al arrancar");
        tournamentSyncService.syncAll();
        log.info("[TOURNAMENT_SCHEDULER] Finalizado sync inicial al arrancar");
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 300000)
    public void syncEveryFiveMinutes() {
        log.info("[TOURNAMENT_SCHEDULER] Lanzando sync programado");
        tournamentSyncService.syncAll();
        log.info("[TOURNAMENT_SCHEDULER] Finalizado sync programado");
    }
}