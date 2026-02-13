package com.pronosticup.backend.users.controller.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String password
) {}
