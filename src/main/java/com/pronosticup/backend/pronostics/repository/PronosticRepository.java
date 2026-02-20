package com.pronosticup.backend.pronostics.repository;

import com.pronosticup.backend.pronostics.entity.Pronostic;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PronosticRepository extends MongoRepository<Pronostic, String> {
    boolean existsByPronosticId(String pronosticId);
    Optional<Pronostic> findByPronosticId(String pronosticId);

    // para el futuro (owner revisa pendientes)
    List<Pronostic> findByLeagueIdAndConfirmedFalse(String leagueId);
}

