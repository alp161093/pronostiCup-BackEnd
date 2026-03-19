package com.pronosticup.backend.tournaments.service;

import com.pronosticup.backend.tournaments.model.TournamentSnapshotDocument;
import com.pronosticup.backend.tournaments.repository.TournamentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TournamentService {

    private final TournamentSnapshotRepository tournamentSnapshotRepository;

    public Map<String, Object> getSnapshotPayload(String tournament, String type) {
        TournamentSnapshotDocument snapshot = tournamentSnapshotRepository
                .findByTournamentAndType(normalizeTournament(tournament), normalizeType(type))
                .orElseThrow(() -> new RuntimeException(
                        "No existe snapshot para tournament=" + tournament + " y type=" + type
                ));

        return snapshot.getPayload();
    }

    public TournamentSnapshotDocument saveOrReplaceSnapshot(String tournament, String type, Map<String, Object> payload) {
        String normalizedTournament = normalizeTournament(tournament);
        String normalizedType = normalizeType(type);

        String id = buildId(normalizedTournament, normalizedType);

        TournamentSnapshotDocument document = TournamentSnapshotDocument.builder()
                .id(id)
                .tournament(normalizedTournament)
                .type(normalizedType)
                .payload(payload)
                .updatedAt(LocalDateTime.now())
                .build();

        return tournamentSnapshotRepository.save(document);
    }

    private String normalizeTournament(String tournament) {
        return tournament == null ? "" : tournament.trim().toUpperCase();
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase();
    }

    private String buildId(String tournament, String type) {
        return tournament + "_" + type.toUpperCase().replace("-", "_");
    }
}