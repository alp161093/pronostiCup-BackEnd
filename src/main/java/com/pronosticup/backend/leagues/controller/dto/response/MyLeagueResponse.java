package com.pronosticup.backend.leagues.controller.dto.response;

import java.util.List;

public record MyLeagueResponse(
        String leagueId,
        String leagueName,
        String tournament,
        String role, // "OWNER" | "MEMBER"
        List<String> listPronosticsId,
        List<PendingConfirmationResponse> listPendingConfirmations
) {}