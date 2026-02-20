package com.pronosticup.backend.users.controller;

import com.pronosticup.backend.users.controller.dto.request.RegisterRequest;
import com.pronosticup.backend.users.controller.dto.request.LoginRequest;
import com.pronosticup.backend.users.controller.dto.response.RegisterResponse;
import com.pronosticup.backend.users.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*Es el punto HTTP: donde llegan las rutas tipo: POST /api/auth/register POST /api/auth/login
*Aquí NO debería ir lógica pesada: solo:recibir request DTO, validar, llamar al service, devolver response DTO / status code
* */

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(auth.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<RegisterResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(auth.login(request));
    }
}
