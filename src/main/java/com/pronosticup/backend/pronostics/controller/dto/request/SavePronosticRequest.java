package com.pronosticup.backend.pronostics.controller.dto.request;

import java.util.Map;

public record SavePronosticRequest(
        Meta meta,
        Map<String, Object> groupStage,
        Map<String, Object> knockouts
) {
    public record Meta(
            String leagueId,
            String leagueName,
            Long userId,
            String tournament,
            String pronosticId,   // vendrá null, pero da igual
            Boolean confirmed     // vendrá false, pero da igual
    ) {}
}