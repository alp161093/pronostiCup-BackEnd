package com.pronosticup.backend.tournaments.repository;

import com.pronosticup.backend.tournaments.model.TournamentSnapshotDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TournamentSnapshotRepository extends MongoRepository<TournamentSnapshotDocument, String> {

    Optional<TournamentSnapshotDocument> findByTournamentAndType(String tournament, String type);
}