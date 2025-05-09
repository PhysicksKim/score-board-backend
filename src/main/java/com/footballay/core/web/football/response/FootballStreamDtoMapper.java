package com.footballay.core.web.football.response;

import com.footballay.core.domain.football.comparator.StartLineupComparator;
import com.footballay.core.domain.football.dto.*;
import com.footballay.core.domain.football.persistence.live.MatchPlayer;
import com.footballay.core.web.football.response.fixture.FixtureEventsResponse;
import com.footballay.core.web.football.response.fixture.FixtureInfoResponse;
import com.footballay.core.web.football.response.fixture.FixtureLineupResponse;
import com.footballay.core.web.football.response.fixture.FixtureLineupResponse._Lineup;
import com.footballay.core.web.football.response.fixture.FixtureLiveStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.footballay.core.web.football.response.fixture.FixtureInfoResponse._League;
import static com.footballay.core.web.football.response.fixture.FixtureInfoResponse._Team;

@Slf4j
public class FootballStreamDtoMapper {

    public static LeagueResponse toLeagueResponse(LeagueDto league) {
        return new LeagueResponse(
                league.leagueId(),
                league.name(),
                league.koreanName(),
                league.logo(),
                league.currentSeason()
        );
    }

    public static FixtureOfLeagueResponse toFixtureOfLeagueResponse(FixtureInfoDto fixtureInfoDto) {
        validateFixtureData(fixtureInfoDto);

        LiveStatusDto liveStatus = fixtureInfoDto.liveStatus();
        FixtureOfLeagueResponse._Match match = new FixtureOfLeagueResponse._Match(
                fixtureInfoDto.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                fixtureInfoDto.round()
        );
        FixtureOfLeagueResponse._Team home = new FixtureOfLeagueResponse._Team(
                fixtureInfoDto.homeTeam().name(),
                fixtureInfoDto.homeTeam().logo(),
                fixtureInfoDto.homeTeam().koreanName()
        );
        FixtureOfLeagueResponse._Team away = new FixtureOfLeagueResponse._Team(
                fixtureInfoDto.awayTeam().name(),
                fixtureInfoDto.awayTeam().logo(),
                fixtureInfoDto.awayTeam().koreanName()
        );
        FixtureOfLeagueResponse._Status status = new FixtureOfLeagueResponse._Status(
                liveStatus.longStatus(),
                liveStatus.shortStatus(),
                liveStatus.elapsed(),
                new FixtureOfLeagueResponse._Score(
                        liveStatus.homeScore(),
                        liveStatus.awayScore()
                )
        );

        return new FixtureOfLeagueResponse(
                fixtureInfoDto.fixtureId(),
                match,
                home,
                away,
                status,
                fixtureInfoDto.available()
        );
    }

    public static FixtureInfoResponse toFixtureInfoResponse(FixtureInfoDto fixture) {
        OffsetDateTime offsetDateTime = fixture.date().toOffsetDateTime();
        String dateStr = offsetDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        _League league = new _League(
                fixture.league().leagueId(),
                fixture.league().name(),
                fixture.league().koreanName(),
                fixture.league().logo()
        );
        _Team home = new _Team(
                fixture.homeTeam().id(),
                fixture.homeTeam().name(),
                fixture.homeTeam().koreanName(),
                fixture.homeTeam().logo()
        );
        _Team away = new _Team(
                fixture.awayTeam().id(),
                fixture.awayTeam().name(),
                fixture.awayTeam().koreanName(),
                fixture.awayTeam().logo()
        );
        return new FixtureInfoResponse(
                fixture.fixtureId(),
                fixture.referee(),
                dateStr,
                league,
                home,
                away
        );
    }

    public static FixtureEventsResponse toFixtureEventsResponse(long fixtureId, List<FixtureEventWithPlayerDto> eventDtos) {
        List<FixtureEventsResponse._Events> eventsList = new ArrayList<>();
        for (FixtureEventWithPlayerDto dto : eventDtos) {
            FixtureEventsResponse._Player respPlayer = createEventResponsePerson(dto.player());
            FixtureEventsResponse._Player respAssist = createEventResponsePerson(dto.assist());
            FixtureEventsResponse._Team respTeam = createEventResponseTeam(dto.team());

            FixtureEventsResponse._Events _event = createEventResponse(dto, respTeam, respPlayer, respAssist);
            eventsList.add(_event);
        }
        return new FixtureEventsResponse(fixtureId, eventsList);
    }

    public static FixtureLiveStatusResponse toFixtureLiveStatusResponse(long fixtureId, LiveStatusDto liveStatus) {
        FixtureLiveStatusResponse._Score score = new FixtureLiveStatusResponse._Score(
                liveStatus.homeScore(),
                liveStatus.awayScore()
        );
        return new FixtureLiveStatusResponse(
                fixtureId,
                new FixtureLiveStatusResponse._LiveStatus(
                        liveStatus.elapsed(),
                        liveStatus.shortStatus(),
                        liveStatus.longStatus(),
                        score
                )
        );
    }

    /**
     * Fixture -> Lineup -> MatchLineup -> MatchPlayer
     *
     * @param dto
     * @return
     */
    public static FixtureLineupResponse toFixtureLineupResponse(FixtureWithLineupDto dto) {
        _Lineup lineup = null;
        if (dto.homeLineup() != null && dto.awayLineup() != null) {
            LineupDto homeLineup = dto.homeLineup();
            LineupDto awayLineup = dto.awayLineup();
            try {
                List<LineupDto.LineupPlayer> dtoHomePlayers = sortWithStartLineupComparator(homeLineup);
                List<FixtureLineupResponse._LineupPlayer> homeStartXI = new ArrayList<>();
                List<FixtureLineupResponse._LineupPlayer> homeSubstitutes = new ArrayList<>();
                toLineupPlayerList(dtoHomePlayers, homeStartXI, homeSubstitutes);

                List<LineupDto.LineupPlayer> dtoAwayPlayers = sortWithStartLineupComparator(awayLineup);
                List<FixtureLineupResponse._LineupPlayer> awayStartXI = new ArrayList<>();
                List<FixtureLineupResponse._LineupPlayer> awaySubstitutes = new ArrayList<>();
                toLineupPlayerList(dtoAwayPlayers, awayStartXI, awaySubstitutes);

                LineupDto.LineupTeamDto homeTeam = dto.homeLineup().team();
                LineupDto.LineupTeamDto awayTeam = dto.awayLineup().team();

                FixtureLineupResponse._StartLineup homeLineupResponse = new FixtureLineupResponse._StartLineup(
                        homeTeam.teamId(),
                        homeTeam.name(),
                        homeTeam.koreanName(),
                        homeLineup.formation(),
                        homeStartXI,
                        homeSubstitutes
                );
                FixtureLineupResponse._StartLineup awayLineupResponse = new FixtureLineupResponse._StartLineup(
                        awayTeam.teamId(),
                        awayTeam.name(),
                        awayTeam.koreanName(),
                        awayLineup.formation(),
                        awayStartXI,
                        awaySubstitutes
                );

                lineup = new _Lineup(homeLineupResponse, awayLineupResponse);
            } catch (Exception e) {
                log.error("라인업 Response Mapping 중 오류 발생 : {}", e.getMessage(), e);
            }
        }
        return new FixtureLineupResponse(
                dto.fixture().fixtureId(),
                lineup
        );
    }

    public static List<TeamsOfLeagueResponse> toTeamsOfLeagueResponseList(List<TeamDto> teamsOfLeague) {
        List<TeamsOfLeagueResponse> responseList = new ArrayList<>();
        for (TeamDto team : teamsOfLeague) {
            TeamsOfLeagueResponse response = new TeamsOfLeagueResponse(
                    team.id(),
                    team.name(),
                    team.koreanName(),
                    team.logo()
            );
            responseList.add(response);
        }
        return responseList;
    }

    private static void validateFixtureData(FixtureInfoDto fixtureInfoDto) {
        if (fixtureInfoDto.homeTeam() == null || fixtureInfoDto.awayTeam() == null) {
            throw new IllegalArgumentException("홈팀 또는 어웨이팀 정보가 존재하지 않습니다. " +
                    "homeTeamIsNull:" + (fixtureInfoDto.homeTeam() == null) +
                    ", awayTeamIsNull:" + (fixtureInfoDto.awayTeam() == null));
        }
        if (fixtureInfoDto.liveStatus() == null) {
            throw new IllegalArgumentException("라이브 상태 정보가 존재하지 않습니다.");
        }
    }

    private static FixtureEventsResponse._Events createEventResponse(
            FixtureEventWithPlayerDto dto,
            FixtureEventsResponse._Team respTeam,
            FixtureEventsResponse._Player respPlayer,
            FixtureEventsResponse._Player respAssist
    ) {
        return new FixtureEventsResponse._Events(
                dto.sequence(),
                dto.elapsed(),
                dto.extraTime(),
                respTeam,
                respPlayer,
                respAssist,
                dto.type(),
                dto.detail(),
                dto.comments()
        );
    }

    private static FixtureEventsResponse._Team createEventResponseTeam(FixtureEventWithPlayerDto.EventTeamDto teamDto) {
        return new FixtureEventsResponse._Team(
                teamDto.teamId(),
                teamDto.name(),
                teamDto.koreanName()
        );
    }

    private static FixtureEventsResponse._Player createEventResponsePerson(FixtureEventWithPlayerDto.EventPlayerDto eventPlayer) {
        if (eventPlayer == null) {
            return null;
        }
        return new FixtureEventsResponse._Player(
                eventPlayer.playerId(),
                eventPlayer.name(),
                eventPlayer.koreanName(),
                eventPlayer.number(),
                eventPlayer.tempId()
        );
    }

    private static @NotNull List<LineupDto.LineupPlayer> sortWithStartLineupComparator(LineupDto lineupDto) {
        return lineupDto.players().stream().sorted(new StartLineupComparator()).toList();
    }

    private static void toLineupPlayerList(
            List<LineupDto.LineupPlayer> findAwayPlayers,
            List<FixtureLineupResponse._LineupPlayer> awayStartXI,
            List<FixtureLineupResponse._LineupPlayer> awaySubstitutes
    ) {
        for (LineupDto.LineupPlayer findAwayPlayer : findAwayPlayers) {
            FixtureLineupResponse._LineupPlayer responsePlayer
                    = lineupPlayerToResponseDtoElement(findAwayPlayer);

            if (responsePlayer.substitute()) {
                awaySubstitutes.add(responsePlayer);
            } else {
                awayStartXI.add(responsePlayer);
            }
        }
    }

    private static FixtureLineupResponse._LineupPlayer lineupPlayerToResponseDtoElement(LineupDto.LineupPlayer findAwayPlayer) {
        if (isUnregisteredPlayer(findAwayPlayer)) {
            return new FixtureLineupResponse._LineupPlayer(
                    0,
                    "",
                    findAwayPlayer.unregisteredPlayerName(),
                    findAwayPlayer.unregisteredPlayerNumber(),
                    MatchPlayer.UNREGISTERED_PLAYER_PHOTO_URL,
                    findAwayPlayer.position(),
                    findAwayPlayer.grid(),
                    findAwayPlayer.substitute(),
                    findAwayPlayer.tempId() != null ? findAwayPlayer.tempId().toString() : ""
            );
        } else {
            return new FixtureLineupResponse._LineupPlayer(
                    findAwayPlayer.playerId(),
                    findAwayPlayer.koreanName(),
                    findAwayPlayer.name(),
                    findAwayPlayer.number(),
                    findAwayPlayer.photoUrl(),
                    findAwayPlayer.position(),
                    findAwayPlayer.grid(),
                    findAwayPlayer.substitute(),
                    findAwayPlayer.tempId() != null ? findAwayPlayer.tempId().toString() : ""
            );
        }
    }

    private static boolean isUnregisteredPlayer(LineupDto.LineupPlayer lp) {
        return lp.playerId() == null || lp.playerId() == 0;
    }

    private static boolean isUnregisteredPlayer(FixtureEventWithPlayerDto.EventPlayerDto dto) {
        return dto.playerId() == null || dto.playerId() == 0;
    }

    /**
     * Preference PlayerCustomPhoto 를 덮어씌워서 FixtureLineupResponse 를 업데이트합니다.
     *
     * @param response
     * @param photoMap
     * @return
     */
    public static FixtureLineupResponse copyWithCustomPhotos(
            FixtureLineupResponse response,
            Map<Long, String> photoMap
    ) {
        assert response.lineup() != null;
        assert response.lineup().home() != null;
        assert response.lineup().away() != null;

        FixtureLineupResponse._StartLineup home = copyWithCustomPhotosForStartLineup(
                response.lineup().home(),
                photoMap
        );
        FixtureLineupResponse._StartLineup away = copyWithCustomPhotosForStartLineup(
                response.lineup().away(),
                photoMap
        );

        FixtureLineupResponse._Lineup updatedLineup = new FixtureLineupResponse._Lineup(home, away);
        return new FixtureLineupResponse(response.fixtureId(), updatedLineup);
    }

    private static FixtureLineupResponse._StartLineup copyWithCustomPhotosForStartLineup(
            FixtureLineupResponse._StartLineup original,
            Map<Long, String> photoMap
    ) {
        if (original == null) {
            return null;
        }
        List<FixtureLineupResponse._LineupPlayer> updatedPlayers = original.players().stream()
                .map(player -> copyWithCustomPhotosForPlayer(player, photoMap))
                .toList();
        List<FixtureLineupResponse._LineupPlayer> updatedSubs = original.substitutes().stream()
                .map(player -> copyWithCustomPhotosForPlayer(player, photoMap))
                .toList();

        return new FixtureLineupResponse._StartLineup(
                original.teamId(),
                original.teamName(),
                original.teamKoreanName(),
                original.formation(),
                updatedPlayers,
                updatedSubs
        );
    }

    private static FixtureLineupResponse._LineupPlayer copyWithCustomPhotosForPlayer(
            FixtureLineupResponse._LineupPlayer player,
            Map<Long, String> photoMap
    ) {
        if (player == null) {
            return null;
        }
        // photoMap에 플레이어 id가 있으면 해당 URL로 교체, 없으면 원본 그대로
        String customPhoto = photoMap.getOrDefault(player.id(), player.photo());
        return new FixtureLineupResponse._LineupPlayer(
                player.id(),
                player.koreanName(),
                player.name(),
                player.number(),
                customPhoto,
                player.position(),
                player.grid(),
                player.substitute(),
                player.tempId()
        );
    }

}
