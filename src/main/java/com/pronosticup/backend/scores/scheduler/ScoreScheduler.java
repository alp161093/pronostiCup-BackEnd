package com.pronosticup.backend.scores.scheduler;

import com.pronosticup.backend.scores.service.ScoreBatchService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "app.schedulers.score-batch.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ScoreScheduler {

    private final ScoreBatchService scoreBatchService;

    @PostConstruct
    public void init() {
        System.out.println("### SCORE_SCHEDULER Bean creado ###");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void calculateOnStartup() {
        System.out.println("### SCORE_SCHEDULER calculateOnStartup INICIO ###");
        try {
            scoreBatchService.calculateScoresBatchForAllSupportedTournaments();
            System.out.println("### SCORE_SCHEDULER calculateOnStartup FIN ###");
        } catch (Exception ex) {
            System.out.println("### SCORE_SCHEDULER calculateOnStartup ERROR: " + ex.getMessage() + " ###");
            ex.printStackTrace();
        }
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 300000)
    public void calculateEveryFiveMinutes() {
        System.out.println("### SCORE_SCHEDULER cálculo programado INICIO ###");
        try {
            scoreBatchService.calculateScoresBatchForAllSupportedTournaments();
            System.out.println("### SCORE_SCHEDULER cálculo programado FIN ###");
        } catch (Exception ex) {
            System.out.println("### SCORE_SCHEDULER cálculo programado ERROR: " + ex.getMessage() + " ###");
            ex.printStackTrace();
        }
    }
}