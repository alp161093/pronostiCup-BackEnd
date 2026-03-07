package com.pronosticup.backend.pronostics.controller.dto.response;

public record PronosticClasificacionResponse(
        String pronosticId,
        Integer totalPoints,
        String username,
        String pronosticAlias
) {}
