package com.pronosticup.backend.tournaments.scheduler;

import com.pronosticup.backend.tournaments.service.TournamentSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "app.schedulers.tournament-sync.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TournamentScheduler {

    private final TournamentSyncService tournamentSyncService;

    /**
     * lanzo una sincronización inicial al arrancar la aplicación.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        tournamentSyncService.syncAll();
    }

    /**
     * lanzo la sincronización automática cada cinco minutos.
     */
    @Scheduled(initialDelay = 10000, fixedDelay = 300000)
    public void syncEveryFiveMinutes() {
        tournamentSyncService.syncAll();
    }
}