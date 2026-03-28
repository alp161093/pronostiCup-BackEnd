package com.pronosticup.backend.pronostics.controller.dto.response;

import java.util.Map;

/**
 * Devuelvo la información descifrada del pronóstico lista
 * para que el frontend pueda rellenar grupos y eliminatorias.
 */
public record DecryptPronosticResponse(
        String tournament,
        Map<String, Object> groupStage,
        Map<String, Object> knockouts
) {
}