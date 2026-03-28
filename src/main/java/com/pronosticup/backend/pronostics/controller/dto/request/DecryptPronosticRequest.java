package com.pronosticup.backend.pronostics.controller.dto.request;

/**
 * Recibo el texto cifrado del pronóstico y el torneo
 * en el que el usuario quiere cargarlo.
 */
public record DecryptPronosticRequest(
        String encryptedPronostic,
        String tournament
) {
}