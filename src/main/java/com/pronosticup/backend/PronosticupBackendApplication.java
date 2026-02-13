package com.pronosticup.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.pronosticup.backend.users.repository")
@EnableMongoRepositories(basePackages = "com.pronosticup.backend.documents.repo")
public class PronosticupBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(PronosticupBackendApplication.class, args);
    }
}
