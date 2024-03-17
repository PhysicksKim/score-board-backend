package com.gyechunsik.scoreboard.domain.football.data.fetch;

import com.gyechunsik.scoreboard.domain.football.constant.LeagueId;
import com.gyechunsik.scoreboard.domain.football.constant.TeamId;
import com.gyechunsik.scoreboard.domain.football.data.fetch.response.LeagueInfoResponse;
import com.gyechunsik.scoreboard.domain.football.data.fetch.response.LeagueResponse;
import com.gyechunsik.scoreboard.domain.football.data.fetch.response.PlayerSquadResponse;
import com.gyechunsik.scoreboard.domain.football.data.fetch.response.TeamInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Slf4j
@Transactional
@ActiveProfiles("mockapi")
@SpringBootTest
class MockApiCallServiceImplTest {

    @Autowired
    private MockApiCallServiceImpl mockApiCallService;

    @DisplayName("Mock Api 로 League 반환")
    @Test
    void success_league() {
        // given
        long epl = LeagueId.EPL;

        // when
        LeagueInfoResponse leagueInfoResponse = mockApiCallService.leagueInfo(epl);

        // then
        assertThat(leagueInfoResponse).isNotNull();
        assertThat(leagueInfoResponse.getResponse().get(0)).isNotNull();

        LeagueResponse leagueResponse = leagueInfoResponse.getResponse().get(0).getLeague();
        log.info("League Response : {}", leagueResponse);
    }


    @DisplayName("Mock Api 로 team 의 현재 leagues 를 반환")
    @Test
    void success_teamCurrentLeagues() {
        // given
        long manutd = TeamId.MANUTD;

        // when
        LeagueInfoResponse leagueInfoResponse = mockApiCallService.teamCurrentLeaguesInfo(manutd);

        // then
        assertThat(leagueInfoResponse).isNotNull();
        assertThat(leagueInfoResponse.getResponse()).size().isGreaterThan(2);
        assertThat(leagueInfoResponse.getResponse().get(0)).isNotNull();
        for (LeagueInfoResponse.Response response : leagueInfoResponse.getResponse()) {
            log.info("League Response : {}", response);
        }
    }

    @DisplayName("Mock Api 로 Team 반환")
    @Test
    void success_team() {
        // given
        long mancity = TeamId.MANCITY;

        // when
        TeamInfoResponse teamInfoResponse = mockApiCallService.teamInfo(mancity);

        // then
        assertThat(teamInfoResponse).isNotNull();
        assertThat(teamInfoResponse.getResponse().get(0)).isNotNull();

        TeamInfoResponse.TeamResponse team = teamInfoResponse.getResponse().get(0).getTeam();
        log.info("Team Response : {}", team);
    }

    @DisplayName("Mock Api 로 player Squad 반환")
    @Test
    void success_playerSquad() {
        // given
        long mancity = TeamId.MANCITY;

        // when
        PlayerSquadResponse playerSquadResponse = mockApiCallService.playerSquad(mancity);

        // then
        assertThat(playerSquadResponse).isNotNull();
        assertThat(playerSquadResponse.getResponse().get(0)).isNotNull();

        List<PlayerSquadResponse.PlayerData> players = playerSquadResponse.getResponse().get(0).getPlayers();
        for (PlayerSquadResponse.PlayerData player : players) {
            log.info("player : {}", player);
        }
    }
}