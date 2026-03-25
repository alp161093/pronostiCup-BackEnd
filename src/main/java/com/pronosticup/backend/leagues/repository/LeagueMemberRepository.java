package com.pronosticup.backend.leagues.repository;

import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.leagues.entity.LeagueMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface LeagueMemberRepository extends JpaRepository<LeagueMember, LeagueMemberId> {

    boolean existsByLeagueIdAndUserId(String leagueId, Long userId);

    // opcional, por si quieres ver si ya existe ese pronosticId asociado
    boolean existsByLeagueIdAndUserIdAndPronosticId(String leagueId, Long userId, String pronosticId);

    // ligas del usuario + pronósticos del usuario
    @Query("""
    select 
      lm.leagueId as leagueId,
      l.name as leagueName,
      l.tournament as tournament,
      lm.role as role,
      lm.pronosticId as pronosticId,
      lm.pronosticAlias as pronosticAlias,
      lm.confirmed as confirmed
    from LeagueMember lm
    join League l on l.id = lm.leagueId
    where lm.userId = :userId
    """)
    List<MyLeagueRow> findMyLeaguesRows(@Param("userId") Long userId);

    // pendientes de confirmar en una liga (solo para OWNER)
    @Query("""
    select 
      lm.pronosticId as pronosticId,
      lm.pronosticAlias as pronosticAlias,
      u.username as username
    from LeagueMember lm
    join User u on u.id = lm.userId
    where lm.leagueId = :leagueId
      and lm.confirmed = false
    """)
    List<PendingConfirmationRow> findPendingByLeague(@Param("leagueId") String leagueId);

    Optional<LeagueMember> findByLeagueIdAndPronosticId(String leagueId, String pronosticId);

    List<LeagueMember> findByLeagueIdAndConfirmedTrue(String leagueId);

    @Modifying
    @Transactional
    @Query("""
       UPDATE LeagueMember lm
       SET lm.pronosticAlias = :pronosticAlias
       WHERE lm.pronosticId = :pronosticId
       """)
    int updatePronosticAliasByPronosticId(@Param("pronosticId") String pronosticId,
                                          @Param("pronosticAlias") String pronosticAlias);

    @Modifying
    @Transactional
    @Query("""
       DELETE FROM LeagueMember lm
       WHERE lm.pronosticId = :pronosticId
       """)
    int deleteByPronosticId(@Param("pronosticId") String pronosticId);


}
