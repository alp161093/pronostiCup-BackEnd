package com.pronosticup.backend.tournaments.controller;

import com.pronosticup.backend.tournaments.model.TournamentSnapshotDocument;
import com.pronosticup.backend.tournaments.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class TournamentController {

    private final TournamentService tournamentService;

    @GetMapping("/{tournament}/standings")
    public ResponseEntity<Map<String, Object>> getStandings(@PathVariable String tournament) {
        return ResponseEntity.ok(
                tournamentService.getSnapshotPayload(tournament, "standings")
        );
    }

    @GetMapping("/{tournament}/matches-knockouts")
    public ResponseEntity<Map<String, Object>> getMatchesKnockouts(@PathVariable String tournament) {
        return ResponseEntity.ok(
                tournamentService.getSnapshotPayload(tournament, "matches-knockouts")
        );
    }

}