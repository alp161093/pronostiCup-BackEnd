package com.pronosticup.backend.tournaments.scheduler;

import com.pronosticup.backend.tournaments.service.TournamentSyncService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TournamentScheduler {

    private final TournamentSyncService tournamentSyncService;

    @PostConstruct
    public void init() {
        System.out.println("### TOURNAMENT_SCHEDULER Bean creado ###");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        System.out.println("### TOURNAMENT_SCHEDULER syncOnStartup INICIO ###");
        try {
            tournamentSyncService.syncAll();
            System.out.println("### TOURNAMENT_SCHEDULER syncOnStartup FIN ###");
        } catch (Exception ex) {
            System.out.println("### TOURNAMENT_SCHEDULER syncOnStartup ERROR: " + ex.getMessage() + " ###");
            ex.printStackTrace();
        }
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 300000)
    public void syncEveryFiveMinutes() {
        System.out.println("### TOURNAMENT_SCHEDULER sync programado INICIO ###");
        try {
            tournamentSyncService.syncAll();
            System.out.println("### TOURNAMENT_SCHEDULER sync programado FIN ###");
        } catch (Exception ex) {
            System.out.println("### TOURNAMENT_SCHEDULER sync programado ERROR: " + ex.getMessage() + " ###");
            ex.printStackTrace();
        }
    }
}