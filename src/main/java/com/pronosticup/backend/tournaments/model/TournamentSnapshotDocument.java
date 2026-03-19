package com.pronosticup.backend.tournaments.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tournament_snapshots")
public class TournamentSnapshotDocument {

    @Id
    private String id;

    private String tournament;
    private String type;

    //JSON exacto que consume el frontend. Es exactamente lo mismo que se recibe de la API externa
    private Map<String, Object> payload;

    private LocalDateTime updatedAt;
}
