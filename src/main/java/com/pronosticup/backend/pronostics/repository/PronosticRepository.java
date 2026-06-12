package com.pronosticup.backend.pronostics.repository;

import com.pronosticup.backend.pronostics.entity.Pronostic;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.pronosticup.backend.pronostics.controller.dto.PronosticClassificationProjection;
import java.util.List;
import java.util.Optional;

public interface PronosticRepository extends MongoRepository<Pronostic, String> {
    boolean existsByPronosticId(String pronosticId);
    Optional<Pronostic> findByPronosticId(String pronosticId);
    //se busca un listado de pronosticos
    List<PronosticClassificationProjection> findByPronosticIdIn(List<String> pronosticIds);
    // para el futuro (owner revisa pendientes)
    List<Pronostic> findByLeagueIdAndConfirmedFalse(String leagueId);

    void deleteByPronosticId(String pronosticId);

    @Query(
            value = "{ 'pronosticId': { $in: ?0 } }",
            fields = "{ 'pronosticId': 1, 'totalPoints': 1 }"
    )
    List<PronosticClassificationProjection> findClassificationByPronosticIdIn(List<String> pronosticIds);

}

