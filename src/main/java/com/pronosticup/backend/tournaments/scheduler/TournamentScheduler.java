package com.pronosticup.backend.tournaments.scheduler;

import com.pronosticup.backend.tournaments.service.TournamentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TournamentScheduler {

    private final TournamentSyncService tournamentSyncService;

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("Lanzando sincronización inicial de torneos al arrancar la aplicación");
        tournamentSyncService.syncAll();
    }

    @Scheduled(initialDelay = 300000, fixedDelay = 300000)
    public void syncEveryFiveMinutes() {
        log.info("Lanzando sincronización programada de torneos");
        tournamentSyncService.syncAll();
    }
}