package com.pronosticup.backend.leagues.entity;

import lombok.*;
import java.io.Serializable;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class LeagueMemberId implements Serializable {
    private String leagueId;
    private Long userId;
    private String pronosticId;
}
