package com.pronosticup.backend.users.entity;

import jakarta.persistence.*;
import lombok.*;

//Representa cómo se guarda en BD

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username"),
                @UniqueConstraint(columnNames = "email")
        }
)

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = true) // si quieres obligatorio, pon nullable=false
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = true)
    private String nombre;

    @Column(nullable = true)
    private String apellidos;
}
