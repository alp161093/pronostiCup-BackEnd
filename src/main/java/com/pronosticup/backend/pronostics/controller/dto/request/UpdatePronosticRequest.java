package com.pronosticup.backend.pronostics.controller.dto.request;

import java.util.Map;

public record UpdatePronosticRequest(
        String pronosticAlias,
        Map<String, Object> groupStage,
        Map<String, Object> knockouts
) {}
