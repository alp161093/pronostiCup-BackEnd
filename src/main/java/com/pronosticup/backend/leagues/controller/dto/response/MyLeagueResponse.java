package com.pronosticup.backend.leagues.controller.dto.response;

import com.pronosticup.backend.pronostics.controller.dto.response.MyPronosticResponse;

import java.util.List;

public record MyLeagueResponse(
        String leagueId,
        String leagueName,
        String tournament,
        String role, // "OWNER" | "MEMBER"
        List<MyPronosticResponse> listPronosticsId,
        List<PendingConfirmationResponse> listPendingConfirmations
) {}