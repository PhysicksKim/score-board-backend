package com.footballay.core.domain.football.persistence.relations;

import com.footballay.core.domain.football.persistence.League;
import com.footballay.core.domain.football.persistence.Team;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"league_id", "team_id"}))
public class LeagueTeam {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "league_id")
    private League league;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    @Override
    public String toString() {
        return "LeagueTeam{" +
                "league=" + league +
                ", team=" + team +
                '}';
    }
}
