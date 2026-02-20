package com.pronosticup.backend.leagues.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "league_members")
@IdClass(LeagueMemberId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeagueMember {

    @Id
    @Column(name = "league_id", nullable = false)
    private String leagueId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "pronostic_id", nullable = false)
    private String pronosticId;

    @Column(nullable = false)
    private String role; // OWNER / MEMBER
}
