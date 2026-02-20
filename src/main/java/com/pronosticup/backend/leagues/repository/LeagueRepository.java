package com.pronosticup.backend.leagues.repository;

import com.pronosticup.backend.leagues.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface LeagueRepository extends JpaRepository<League, String> {
    @Query("select l.id from League l where l.owner.id = :ownerId")
    List<String> findLeagueIdsByOwnerId(@Param("ownerId") Long ownerId);
}

