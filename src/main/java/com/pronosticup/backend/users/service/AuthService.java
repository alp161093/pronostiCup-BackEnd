package com.pronosticup.backend.users.service;

import com.pronosticup.backend.users.controller.dto.request.RegisterRequest;
import com.pronosticup.backend.users.controller.dto.request.LoginRequest;
import com.pronosticup.backend.users.controller.dto.response.RegisterResponse;
import com.pronosticup.backend.users.entity.User;
import com.pronosticup.backend.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/*La lógica de negocio:
comprobar si existe username/email, encriptar password. crear el objeto User, guardarlo, construir la respuesta*/


@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.encoder = encoder;
    }

    public RegisterResponse register(RegisterRequest req) {

        if (userRepo.findByUsername(req.getUsername()).isPresent()) {
            throw new RuntimeException("Usuario ya existe");
        }

        User user = User.builder()
                .nombre(req.getNombre())
                .apellidos(req.getApellidos())
                .username(req.getUsername())
                .email(req.getEmail())
                .password(encoder.encode(req.getPassword()))
                .build();

        userRepo.save(user);

        return new RegisterResponse(user.getId(), user.getUsername(), user.getNombre(),  user.getApellidos());
    }

    public RegisterResponse login(LoginRequest request) {

        var user = userRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!encoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        return new RegisterResponse(user.getId(), user.getUsername(), user.getNombre(),  user.getApellidos());
    }
}
