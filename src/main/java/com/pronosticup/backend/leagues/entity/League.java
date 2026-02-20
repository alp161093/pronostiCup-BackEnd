package com.pronosticup.backend.leagues.entity;

import com.pronosticup.backend.users.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "leagues")
public class League {

    @Id
    @Column(name = "id", length = 120, nullable = false)
    private String id; // leagueId string

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "tournament", length = 20, nullable = false)
    private String tournament; // "mundial" | "eurocopa"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner; // tu entidad User ya existe en tu proyecto

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    // getters/setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTournament() {
        return tournament;
    }

    public void setTournament(String tournament) {
        this.tournament = tournament;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

