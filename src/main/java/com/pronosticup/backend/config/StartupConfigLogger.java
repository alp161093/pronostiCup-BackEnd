package com.pronosticup.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartupConfigLogger {

    @Value("${app.schedulers.tournament-sync.enabled:false}")
    private boolean tournamentSyncEnabled;

    @Value("${app.schedulers.score-batch.enabled:false}")
    private boolean scoreBatchEnabled;

    @Value("${app.scores.use-local-snapshots:false}")
    private boolean useLocalSnapshots;

    @PostConstruct
    public void logConfig() {
        log.info("[CONFIG] app.schedulers.tournament-sync.enabled={}", tournamentSyncEnabled);
        log.info("[CONFIG] app.schedulers.score-batch.enabled={}", scoreBatchEnabled);
        log.info("[CONFIG] app.scores.use-local-snapshots={}", useLocalSnapshots);
    }
}