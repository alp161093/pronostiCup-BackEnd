package com.pronosticup.backend.pronostics.controller.dto.response;

import java.time.Instant;

public record UpdatePronosticResponse(
        String pronosticId,
        String pronosticAlias,
        Instant updatedAt,
        String message
) {}