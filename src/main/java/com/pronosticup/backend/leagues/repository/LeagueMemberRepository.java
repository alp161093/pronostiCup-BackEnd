package com.pronosticup.backend.leagues.repository;

import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.leagues.entity.LeagueMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeagueMemberRepository extends JpaRepository<LeagueMember, LeagueMemberId> {

    boolean existsByLeagueIdAndUserId(String leagueId, Long userId);

    // opcional, por si quieres ver si ya existe ese pronosticId asociado
    boolean existsByLeagueIdAndUserIdAndPronosticId(String leagueId, Long userId, String pronosticId);
}
