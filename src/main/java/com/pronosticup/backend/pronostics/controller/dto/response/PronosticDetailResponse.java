package com.pronosticup.backend.pronostics.controller.dto.response;

import java.util.List;
import java.util.Map;

public record PronosticDetailResponse(
        MetaResponse meta,
        Map<String, Object> groupStage,
        Map<String, Object> knockouts,
        ScoreResponse score
) {
    public record MetaResponse(
            String pronosticId,
            String pronosticAlias,
            String leagueId,
            String leagueName,
            String tournament,
            boolean confirmed,
            boolean editable,
            boolean canEditAlias,
            String firstMatchDate
    ) {}

    public record ScoreResponse(
            Integer totalScore,
            List<ScoreRuleResponse> rules
    ) {}

    public record ScoreRuleResponse(
            String label,
            Integer points
    ) {}
}
