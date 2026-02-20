package com.pronosticup.backend.leagues.controller.dto.response;

public record LeagueResponse(
        String id,
        String name,
        String tournament,
        String ownerUsername
) {}
