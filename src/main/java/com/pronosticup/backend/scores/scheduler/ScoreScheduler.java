package com.pronosticup.backend.scores.scheduler;

import com.pronosticup.backend.scores.service.ScoreBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger scoreBatchLogger = LoggerFactory.getLogger("SCORE_BATCH");

    private final ScoreBatchService scoreBatchService;

    /**
     * lanzo un cálculo inicial al arrancar la aplicación para no depender solo del scheduler.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void calculateOnStartup() {
        scoreBatchLogger.info("Lanzando cálculo inicial de puntuaciones al arrancar la aplicación");
        scoreBatchService.calculateScoresBatchForAllSupportedTournaments();
    }

    /**
     * lanzo el batch de puntuación cada cinco minutos.
     */
    /*@Scheduled(initialDelay = 300000, fixedDelay = 300000)
    public void calculateEveryFiveMinutes() {
        scoreBatchLogger.info("Lanzando cálculo programado de puntuaciones");
        scoreBatchService.calculateScoresBatchForAllSupportedTournaments();
    }*/
}