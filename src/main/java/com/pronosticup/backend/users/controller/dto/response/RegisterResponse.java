package com.pronosticup.backend.users.controller.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterResponse {
    private Long id;
    private String username;
    private String nombre;
    private String apellidos;
}
