package com.pronosticup.backend.pronostics.controller.dto.response;

public record SavePronosticResponse(
        String pronosticId,
        boolean confirmed
) {}

