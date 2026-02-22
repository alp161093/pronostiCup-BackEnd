package com.pronosticup.backend.leagues.service;

import com.pronosticup.backend.leagues.controller.dto.request.CreateLeagueRequest;
import com.pronosticup.backend.leagues.controller.dto.response.LeagueResponse;
import com.pronosticup.backend.leagues.controller.dto.response.MyLeagueResponse;
import com.pronosticup.backend.leagues.controller.dto.response.PendingConfirmationResponse;
import com.pronosticup.backend.leagues.entity.League;
import com.pronosticup.backend.leagues.entity.LeagueMember;
import com.pronosticup.backend.leagues.repository.LeagueMemberRepository;
import com.pronosticup.backend.leagues.repository.LeagueRepository;
import com.pronosticup.backend.leagues.repository.MyLeagueRow;
import com.pronosticup.backend.leagues.repository.PendingConfirmationRow;
import com.pronosticup.backend.users.entity.User;
import com.pronosticup.backend.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    public List<MyLeagueResponse> getMyLeagues(Long userId) {

        List<MyLeagueRow> rows = leagueMemberRepository.findMyLeaguesRows(userId);
        if (rows.isEmpty()) return List.of();

        // agrupar por liga
        Map<String, List<MyLeagueRow>> byLeague =
                rows.stream().collect(Collectors.groupingBy(MyLeagueRow::getLeagueId));

        List<MyLeagueResponse> result = new ArrayList<>();

        for (var entry : byLeague.entrySet()) {

            String leagueId = entry.getKey();
            List<MyLeagueRow> leagueRows = entry.getValue();
            MyLeagueRow first = leagueRows.get(0);

            String leagueName = first.getLeagueName();
            String tournament = first.getTournament();

            // role del usuario en esa liga
            String role = leagueRows.stream()
                    .anyMatch(r -> "OWNER".equals(r.getRole()))
                    ? "OWNER"
                    : "MEMBER";

            // pronósticos del usuario en esa liga
            List<String> pronosticIds = leagueRows.stream()
                    .map(MyLeagueRow::getPronosticId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            // pendientes solo si OWNER
            List<PendingConfirmationResponse> pending = List.of();

            if ("OWNER".equals(role)) {
                List<PendingConfirmationRow> pendingRows =
                        leagueMemberRepository.findPendingByLeague(leagueId);

                pending = pendingRows.stream()
                        .map(p -> new PendingConfirmationResponse(
                                p.getUsername(),
                                p.getPronosticId()
                        ))
                        .toList();
            }

            result.add(new MyLeagueResponse(
                    leagueId,
                    leagueName,
                    tournament,
                    role,
                    pronosticIds,
                    pending
            ));
        }

        return result;
    }
}

