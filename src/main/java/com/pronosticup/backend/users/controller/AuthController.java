package com.pronosticup.backend.users.controller;

import com.pronosticup.backend.users.controller.dto.request.LoginRequest;
import com.pronosticup.backend.users.controller.dto.request.RegisterRequest;
import com.pronosticup.backend.users.controller.dto.request.UpdateUserRequest;
import com.pronosticup.backend.users.controller.dto.response.RegisterResponse;
import com.pronosticup.backend.users.controller.dto.response.UserProfileResponse;
import com.pronosticup.backend.users.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * Es el punto HTTP: donde llegan las rutas tipo:
 * POST /api/auth/register
 * POST /api/auth/login
 *
 * Aquí NO debería ir lógica pesada: solo:
 * recibir request DTO, validar, llamar al service, devolver response DTO / status code
 */
@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(auth.register(req));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<RegisterResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }

    /**
     * Devuelvo los datos del usuario para cargar la pantalla de ajustes.
     */
    @GetMapping("/api/users/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(auth.getUserProfile(userId));
    }

    /**
     * Actualizo los datos del usuario desde la pantalla de ajustes.
     */
    @PutMapping("/api/users/{userId}")
    public ResponseEntity<UserProfileResponse> updateUser(@PathVariable Long userId,
                                                          @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(auth.updateUser(userId, request));
    }
}