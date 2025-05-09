package com.footballay.core.util;

import com.footballay.core.domain.football.persistence.Fixture;
import com.footballay.core.domain.football.persistence.League;
import com.footballay.core.domain.football.persistence.Player;
import com.footballay.core.domain.football.persistence.Team;
import com.footballay.core.domain.football.persistence.live.LiveStatus;
import com.footballay.core.domain.football.persistence.relations.LeagueTeam;
import com.footballay.core.domain.football.persistence.relations.TeamPlayer;
import com.footballay.core.domain.football.repository.FixtureRepository;
import com.footballay.core.domain.football.repository.LeagueRepository;
import com.footballay.core.domain.football.repository.PlayerRepository;
import com.footballay.core.domain.football.repository.TeamRepository;
import com.footballay.core.domain.football.repository.live.LiveStatusRepository;
import com.footballay.core.domain.football.repository.relations.LeagueTeamRepository;
import com.footballay.core.domain.football.repository.relations.TeamPlayerRepository;
import com.footballay.core.domain.football.util.GeneratePlayersOfTeam;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.footballay.core.domain.football.util.GenerateLeagueTeamFixture.LeagueTeamFixture;
import static com.footballay.core.domain.football.util.GenerateLeagueTeamFixture.generate;

@RequiredArgsConstructor
@Component
public class TestFootballDataInitializer {

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureRepository fixtureRepository;
    private final PlayerRepository playerRepository;
    private final EntityManager em;

    public static final String LEAGUE_ID = "leagueId";
    public static final String HOME_TEAM_ID = "homeTeamId";
    public static final String AWAY_TEAM_ID = "awayTeamId";
    public static final String FIXTURE_ID = "fixtureId";
    private final TeamPlayerRepository teamPlayerRepository;
    private final LiveStatusRepository liveStatusRepository;

    public Map<String, String> generateSingleSet() {
        LeagueTeamFixture generate = generate();
        League league = generate.league;
        Team home = generate.home;
        Team away = generate.away;
        Fixture fixture = generate.fixture;

        LiveStatus liveStatus = liveStatusRepository.save(createLiveStatusFullTime());
        fixture.setLiveStatus(liveStatus);
        League saveLeague = leagueRepository.save(league);
        Team saveHome = teamRepository.save(home);
        Team saveAway = teamRepository.save(away);
        leagueTeamRepository.save(LeagueTeam.builder().league(saveLeague).team(saveHome).build());
        leagueTeamRepository.save(LeagueTeam.builder().league(saveLeague).team(saveAway).build());
        Fixture saveFixture = fixtureRepository.save(fixture);
        liveStatus.setFixture(saveFixture);

        List<Player> homePlayers = GeneratePlayersOfTeam.generatePlayersOfTeam(saveHome);
        List<Player> savedHomePlayers = playerRepository.saveAll(homePlayers);
        for (Player savedHomePlayer : savedHomePlayers) {
            teamPlayerRepository.save(TeamPlayer.builder().player(savedHomePlayer).team(saveHome).build());
        }

        List<Player> awayPlayers = GeneratePlayersOfTeam.generatePlayersOfTeam(saveAway);
        List<Player> savedAwayPlayers = playerRepository.saveAll(awayPlayers);
        for (Player savedAwayPlayer : savedAwayPlayers) {
            teamPlayerRepository.save(TeamPlayer.builder().player(savedAwayPlayer).team(saveAway).build());
        }

        em.flush();
        em.clear();

        return generateIdMap(generate, savedHomePlayers, savedAwayPlayers);
    }

    private LiveStatus createLiveStatusFullTime() {
        return LiveStatus.builder()
                .elapsed(90)
                .longStatus("Match Finished")
                .shortStatus("FT")
                .homeScore(1)
                .awayScore(0)
                .build();
    }

    public List<String> getPlayerIds(Map<String, String> dataIdMap) {
        return dataIdMap.keySet().stream().filter(key -> key.contains("playerId"))
                .map(dataIdMap::get)
                .sorted().toList();
    }

    public List<String> getLeagueId(Map<String, String> dataIdMap) {
        return dataIdMap.keySet().stream().filter(key -> key.contains("leagueId"))
                .map(dataIdMap::get)
                .sorted().toList();
    }

    public List<String> getHomeTeamId(Map<String, String> dataIdMap) {
        return dataIdMap.keySet().stream().filter(key -> key.contains("homeTeamId"))
                .map(dataIdMap::get)
                .sorted().toList();
    }

    public List<String> getAwayTeamId(Map<String, String> dataIdMap) {
        return dataIdMap.keySet().stream().filter(key -> key.contains("awayTeamId"))
                .map(dataIdMap::get)
                .sorted().toList();
    }

    public List<String> getFixtureId(Map<String, String> dataIdMap) {
        return dataIdMap.keySet().stream().filter(key -> key.contains("fixtureId"))
                .map(dataIdMap::get)
                .sorted().toList();
    }

    private Map<String, String> generateIdMap(LeagueTeamFixture generate, List<Player> ...playersArray) {
        long leagueId = generate.league.getLeagueId();
        long homeTeamId = generate.home.getId();
        long awayTeamId = generate.away.getId();
        long fixtureId = generate.fixture.getFixtureId();
        Map<String, String> result = new HashMap<>();

        for (List<Player> players : playersArray) {
            for (int j = 0; j < players.size(); j++) {
                result.put("playerId" + j, String.valueOf(players.get(j).getId()));
            }
        }

        result.put("leagueId", String.valueOf(leagueId));
        result.put("homeTeamId", String.valueOf(homeTeamId));
        result.put("awayTeamId", String.valueOf(awayTeamId));
        result.put("fixtureId", String.valueOf(fixtureId));

        // create Immutable ID map
        return Map.copyOf(result);
    }

}
