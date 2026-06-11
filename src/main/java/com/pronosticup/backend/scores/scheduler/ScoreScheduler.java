package com.pronosticup.backend.scores.scheduler;

import com.pronosticup.backend.scores.service.ScoreBatchService;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        System.out.println("### SCORE_SCHEDULER Bean creado ###");
    }

    /*@EventListener(ApplicationReadyEvent.class)
    public void calculateOnStartup() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            System.out.println("### SCORE_SCHEDULER calculateOnStartup INICIO ###");
            scoreBatchService.calculateScoresBatchForAllSupportedTournaments();
            System.out.println("### SCORE_SCHEDULER calculateOnStartup FIN ###");
        } catch (Exception ex) {
            System.out.println("### SCORE_SCHEDULER calculateOnStartup ERROR: " + ex.getMessage() + " ###");
            ex.printStackTrace();
        } finally {
            running.set(false);
        }
    }*/

    @Scheduled(initialDelay = 600000, fixedDelay = 600000)
    public void calculateEveryFiveMinutes() {
        if (!running.compareAndSet(false, true)) {
            System.out.println("### SCORE_SCHEDULER ya hay un cálculo en ejecución, se omite ###");
            return;
        }

        try {
            System.out.println("### SCORE_SCHEDULER cálculo programado INICIO ###");
            scoreBatchService.calculateScoresBatchForAllSupportedTournaments();
            System.out.println("### SCORE_SCHEDULER cálculo programado FIN ###");
        } catch (Exception ex) {
            System.out.println("### SCORE_SCHEDULER cálculo programado ERROR: " + ex.getMessage() + " ###");
            ex.printStackTrace();
        } finally {
            running.set(false);
        }
    }
}