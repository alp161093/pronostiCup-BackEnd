package com.pronosticup.backend.users.repository;

import com.pronosticup.backend.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findById(Long id);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
