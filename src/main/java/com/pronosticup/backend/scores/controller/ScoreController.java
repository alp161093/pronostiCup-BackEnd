package com.pronosticup.backend.scores.controller;

import com.pronosticup.backend.scores.service.ScoreBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreBatchService scoreBatchService;

    /**
     * Permito lanzar manualmente el batch de puntuación para un torneo concreto.
     */
    @PostMapping("/recalculate/{tournament}")
    public void recalculateTournamentScores(@PathVariable String tournament) {
        scoreBatchService.calculateScoresBatchForTournament(tournament);
    }

    /**
     * Permito lanzar manualmente el batch de puntuación para todos los torneos soportados.
     */
    @PostMapping("/recalculate-all")
    public void recalculateAllScores() {
        scoreBatchService.calculateScoresBatchForAllSupportedTournaments();
    }
}