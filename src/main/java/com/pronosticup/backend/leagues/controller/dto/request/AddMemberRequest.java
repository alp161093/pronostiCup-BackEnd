package com.pronosticup.backend.leagues.controller.dto.request;

public record AddMemberRequest(
        String leagueId,
        Long userId,
        String role
) {}
