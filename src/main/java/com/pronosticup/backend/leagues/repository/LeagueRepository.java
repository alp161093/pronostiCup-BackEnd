package com.pronosticup.backend.leagues.repository;

import com.pronosticup.backend.leagues.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

@Repository
public interface LeagueRepository extends JpaRepository<League, String> {
    @Query("select l.id from League l where l.owner.id = :ownerId")
    List<String> findLeagueIdsByOwnerId(@Param("ownerId") Long ownerId);

    @Query("select l from League l where lower(l.tournament) = lower(:tournament) and l.createdAt <= :limitDate ")
    List<League> findEligibleLeaguesByTournamentAndCreatedAt(@Param("tournament") String tournament, @Param("limitDate") Instant limitDate);
}

