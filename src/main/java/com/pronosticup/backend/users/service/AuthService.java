package com.pronosticup.backend.users.service;

import com.pronosticup.backend.users.controller.dto.request.LoginRequest;
import com.pronosticup.backend.users.controller.dto.request.RegisterRequest;
import com.pronosticup.backend.users.controller.dto.request.UpdateUserRequest;
import com.pronosticup.backend.users.controller.dto.request.ResetPasswordRequest;
import com.pronosticup.backend.users.controller.dto.response.RegisterResponse;
import com.pronosticup.backend.users.controller.dto.response.UserProfileResponse;
import com.pronosticup.backend.users.entity.User;
import com.pronosticup.backend.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.pronosticup.backend.users.service.UserReceiptService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;


/*
 * La lógica de negocio:
 * comprobar si existe username/email, encriptar password,
 * crear el objeto User, guardarlo y construir la respuesta.
 */
@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final UserReceiptService userReceiptService;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, UserReceiptService userReceiptService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.userReceiptService = userReceiptService;
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

        return new RegisterResponse(user.getId(), user.getUsername(), user.getNombre(), user.getApellidos());
    }

    public RegisterResponse login(LoginRequest request) {

        var user = userRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!encoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        return new RegisterResponse(user.getId(), user.getUsername(), user.getNombre(), user.getApellidos());
    }

    /**
     * Obtengo los datos del usuario para mostrarlos en la pantalla de ajustes.
     */
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNombre(),
                user.getApellidos()
        );
    }

    /**
     * Actualizo los datos del usuario.
     * Si la contraseña viene informada, también la actualizo.
     */
    public UserProfileResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String username = safeTrim(request.getUsername());
        String email = safeTrim(request.getEmail());
        String nombre = safeTrim(request.getNombre());
        String apellidos = safeTrim(request.getApellidos());
        String password = safeTrim(request.getPassword());

        if (username == null || username.isBlank()) {
            throw new RuntimeException("El username es obligatorio");
        }

        if (email == null || email.isBlank()) {
            throw new RuntimeException("El email es obligatorio");
        }

        if (nombre == null || nombre.isBlank()) {
            throw new RuntimeException("El nombre es obligatorio");
        }

        if (apellidos == null || apellidos.isBlank()) {
            throw new RuntimeException("Los apellidos son obligatorios");
        }

        if (userRepo.existsByUsernameAndIdNot(username, userId)) {
            throw new RuntimeException("Ya existe otro usuario con ese username");
        }

        if (userRepo.existsByEmailAndIdNot(email, userId)) {
            throw new RuntimeException("Ya existe otro usuario con ese email");
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setNombre(nombre);
        user.setApellidos(apellidos);

        if (password != null && !password.isBlank()) {
            user.setPassword(encoder.encode(password));
        }

        User savedUser = userRepo.save(user);

        return new UserProfileResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getNombre(),
                savedUser.getApellidos()
        );
    }

    /**
     * Limpio espacios para evitar guardar valores sucios.
     */
    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }


    /**
     * Recuperación de contraseña.
     */
    public boolean forgotPassword(String email) {
        System.out.println("### FORGOT_PASSWORD email: "+email+" ###");
        if (email == null || email.isBlank()) {
            System.out.println("### FORGOT_PASSWORD false ###");
            return false;
        }

        Optional<User> userOpt = userRepo.findByEmail(email.trim());

        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        System.out.println("### FORGOT_PASSWORD obtencion usuario "+user.getUsername()+" ###");
        try {

            return userReceiptService.sendForgotPasswordEmail(
                    user.getEmail(),
                    user.getUsername()
            );

        } catch (Exception e) {
            System.out.println("### FORGOT_PASSWORD Error enviando correo de recuperación para usuario ###");
            log.error(
                    "Error enviando correo de recuperación para usuario {}",
                    user.getUsername(),
                    e
            );

            return false;
        }
    }

    public boolean resetPassword(ResetPasswordRequest request) {

        if (request == null) {
            return false;
        }

        String email = safeTrim(request.email());
        String username = safeTrim(request.username());
        String password = safeTrim(request.password());

        if (email == null || email.isBlank()) {
            return false;
        }

        if (username == null || username.isBlank()) {
            return false;
        }

        if (password == null || password.length() < 6) {
            return false;
        }

        Optional<User> userOpt = userRepo.findByEmailAndUsername(email, username);

        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();

        user.setPassword(encoder.encode(password));

        userRepo.save(user);

        return true;
    }
}