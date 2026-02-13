package com.pronosticup.backend.users.controller.dto.request;

import lombok.Data;

@Data
public class RegisterRequest {
    private String nombre;
    private String apellidos;
    private String username;
    private String password;
}
