package com.pronosticup.backend.leagues.controller;

import com.pronosticup.backend.leagues.controller.dto.request.AddMemberRequest;
import com.pronosticup.backend.leagues.controller.dto.request.CreateLeagueRequest;
import com.pronosticup.backend.leagues.controller.dto.response.LeagueResponse;
import com.pronosticup.backend.leagues.controller.dto.response.MyLeagueResponse;
import com.pronosticup.backend.leagues.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leagues")
public class LeagueController {

    private final LeagueService leagueService;

    @PostMapping("/create")
    public LeagueResponse create(@RequestBody CreateLeagueRequest req) {
        return leagueService.createLeague(req, req.ownerUserId()
                );
    }

    @PostMapping("/addMembers")
    public void addMember(@RequestBody AddMemberRequest req) {
        leagueService.addMember(req.leagueId(), req.userId(), req.role());
    }

    @GetMapping("/exists/{leagueId}")
    public LeagueResponse getLeague(@PathVariable String leagueId) {
        return leagueService.getLeagueById(leagueId);
    }

    @GetMapping("/myLeagues/{userId}")
    public List<MyLeagueResponse> getMyLeagues(@PathVariable Long userId) {
        return leagueService.getMyLeagues(userId);
    }
    @GetMapping("/canJoinOrCreate/{tournament}")
    public boolean canJoinOrCreateLeague(@PathVariable String tournament) {
        return leagueService.canJoinOrCreateLeague(tournament);
    }

}


