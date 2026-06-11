package com.pronosticup.backend.users.controller.dto.request;

public record ResetPasswordRequest(
        String email,
        String username,
        String password
) {}
