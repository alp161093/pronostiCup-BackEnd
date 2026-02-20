package com.pronosticup.backend.leagues.controller.dto.request;

public record CreateLeagueRequest(
        String idLeague,
        String name,
        String tournament,
        Long ownerUserId
) {}

