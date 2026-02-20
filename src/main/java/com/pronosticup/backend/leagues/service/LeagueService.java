package com.pronosticup.backend.leagues.service;

import com.pronosticup.backend.leagues.controller.dto.request.CreateLeagueRequest;
import com.pronosticup.backend.leagues.controller.dto.response.LeagueResponse;
import com.pronosticup.backend.leagues.entity.League;
import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.leagues.repository.LeagueMemberRepository;
import com.pronosticup.backend.leagues.repository.LeagueRepository;
import com.pronosticup.backend.users.entity.User;
import com.pronosticup.backend.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository leagueMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public LeagueResponse createLeague(CreateLeagueRequest req, Long ownerUserId) {
        if (leagueRepository.existsById(req.idLeague())) {
            throw new RuntimeException("League already exists");
        }

        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        League league = new League();
        league.setId(req.idLeague());
        league.setName(req.name());
        league.setTournament(req.tournament());
        league.setOwner(owner);

        leagueRepository.save(league);

        return new LeagueResponse(
                league.getId(),
                league.getName(),
                league.getTournament(),
                owner.getUsername()
        );
    }

    @Transactional
    public void addMember(String leagueId, Long userId, String role) {
        if (leagueId == null || leagueId.isBlank()) throw new RuntimeException("leagueId required");
        if (userId == null) throw new RuntimeException("userId required");

        String id = leagueId.trim();
        if (!leagueRepository.existsById(id)) throw new RuntimeException("League not found");

        String r = (role == null || role.isBlank()) ? "MEMBER" : role.trim().toUpperCase();
        if (!r.equals("OWNER") && !r.equals("MEMBER")) throw new RuntimeException("Invalid role");

        if (leagueMemberRepository.existsByLeagueIdAndUserId(id, userId)) return;

        leagueMemberRepository.save(LeagueMember.builder()
                .leagueId(id)
                .userId(userId)
                .role(r)
                .build());
    }

    public LeagueResponse getLeagueById(String leagueId) {

        if (leagueId == null || leagueId.isBlank()) {
            return null; // 👈 no lanzamos error
        }

        League league = leagueRepository.findById(leagueId.trim())
                .orElse(null);

        if (league == null) {
            return null; // si no existe → null
        }

        // username del owner
        String ownerUsername = userRepository.findById(league.getOwner().getId())
                .map(User::getUsername)
                .orElse("unknown");

        return new LeagueResponse(
                league.getId(),
                league.getName(),
                league.getTournament(),
                ownerUsername
        );
    }
}

