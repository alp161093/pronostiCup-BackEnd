package com.pronosticup.backend.pronostics.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "pronostics")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Pronostic {

    @Id
    private String id; // ObjectId de Mongo (string)

    @Indexed(unique = true)
    private String pronosticId;

    // duplicamos campos "importantes" arriba para queries rápidas
    private String leagueId;
    private String leagueName;
    private String tournament;
    private Long userId;
    private String pronosticAlias;

    private boolean confirmed;

    private Instant createdAt;
    private Instant updatedAt;

    private Map<String, Object> groupStage;
    private Map<String, Object> knockouts;
}
