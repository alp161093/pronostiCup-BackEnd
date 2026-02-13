package com.pronosticup.backend.users.service;

import com.pronosticup.backend.users.controller.dto.request.RegisterRequest;
import com.pronosticup.backend.users.controller.dto.response.RegisterResponse;
import com.pronosticup.backend.users.entity.User;
import com.pronosticup.backend.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
                .password(encoder.encode(req.getPassword()))
                .build();

        userRepo.save(user);

        return new RegisterResponse(user.getId(), user.getUsername());
    }
}
