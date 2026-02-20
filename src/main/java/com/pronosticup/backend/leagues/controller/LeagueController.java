package com.pronosticup.backend.leagues.controller;

import com.pronosticup.backend.leagues.controller.dto.request.AddMemberRequest;
import com.pronosticup.backend.leagues.controller.dto.request.CreateLeagueRequest;
import com.pronosticup.backend.leagues.controller.dto.response.LeagueResponse;
import com.pronosticup.backend.leagues.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


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

}


