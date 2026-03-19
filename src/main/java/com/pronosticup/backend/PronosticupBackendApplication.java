package com.pronosticup.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.pronosticup.backend")
@EnableMongoRepositories(basePackages = "com.pronosticup.backend")
//EnableScheduling sirve para ejecutar automáticamente los métodos anotados con @Scheduled
@EnableScheduling
public class PronosticupBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PronosticupBackendApplication.class, args);
    }
}
