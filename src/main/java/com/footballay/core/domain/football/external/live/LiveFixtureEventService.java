package com.footballay.core.domain.football.external.live;

import com.footballay.core.domain.football.external.fetch.response.FixtureSingleResponse;
import com.footballay.core.domain.football.persistence.Fixture;
import com.footballay.core.domain.football.persistence.League;
import com.footballay.core.domain.football.persistence.Player;
import com.footballay.core.domain.football.persistence.Team;
import com.footballay.core.domain.football.persistence.live.*;
import com.footballay.core.domain.football.repository.FixtureRepository;
import com.footballay.core.domain.football.repository.LeagueRepository;
import com.footballay.core.domain.football.repository.PlayerRepository;
import com.footballay.core.domain.football.repository.TeamRepository;
import com.footballay.core.domain.football.repository.live.FixtureEventRepository;
import com.footballay.core.domain.football.repository.live.LiveStatusRepository;
import com.footballay.core.domain.football.repository.live.MatchPlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

import static com.footballay.core.domain.football.external.fetch.response.FixtureSingleResponse.*;

// TODO : [TEST] event 와 fixtureEvent 에서 각각 response 와 dbData 에서 player 와 assist 에 대해 isSame 테스트
//  isSameEventPlayer, isSameEventAssist 메서드로 분리해서 테스트 작성
//  둘이 등록 여부 다른 경우 테스트(하나는 unregistered player 일 때, 하나는 registered player 일 때)

// TODO : [TEST] 이벤트 삭제 후 재저장 시 문제 발생 가능성 있는 부분들 테스트 (ex. unregistered player 가 등장하는 경우, 등록된 player 가 등장하는 경우, 라인업에도 없는 unknownPerson 이 등장하는 경우)

// TODO : [TEST] 전부 테스트 작성 재점검 필요
@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class LiveFixtureEventService {

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final LiveStatusRepository liveStatusRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final FixtureEventRepository fixtureEventRepository;

    private static final List<String> FINISHED_STATUSES
            = List.of("TBD", "FT", "AET", "PEN", "PST", "CANC", "ABD", "AWD", "WO");

    public void saveLiveEvent(FixtureSingleResponse response) {
        if (response.getResponse().isEmpty()) {
            throw new IllegalArgumentException("API _Response 데이터가 없습니다.");
        }

        ResponseValues data = new ResponseValues(response);

        Long fixtureId = data.fixtureId;
        Long leagueId = data.leagueId;
        Long homeId = data.homeId;
        Long awayId = data.awayId;

        log.info("started to save live event fixtureId={}", fixtureId);

        Fixture fixture = findFixtureOrThrow(fixtureId);
        League league = findLeagueOrThrow(leagueId);
        Team home = findTeamOrThrow(homeId);
        Team away = findTeamOrThrow(awayId);
        log.info("found all fixture league home/away entities of fixtureId={}", fixtureId);

        List<_Events> events = data.events;
        if (events.isEmpty()) {
            log.info("이벤트가 없습니다. fixtureId={}", fixtureId);
            return;
        }

        List<FixtureEvent> fixtureEventList
                = fixtureEventRepository.findByFixtureOrderBySequenceDesc(fixture);
        log.info("found events. fixtureId={}, size={}", fixtureId, fixtureEventList.size());

        // 이벤트 취소로 인한 이벤트응답 사이즈 감소 처리 (ex. 카드 취소, 골 취소)
        if (events.size() < fixtureEventList.size()) {
            log.info("이벤트 사이즈 감소 처리. API 응답 size={}, 저장된 Event size={}, fixtureId={}",
                    events.size(), fixtureEventList.size(), fixtureId);

            deleteReducedEvents(events, fixtureEventList);
            fixtureEventList = fixtureEventList.subList(0, events.size());
        }

        // 기존 이벤트 업데이트
        log.info("try to update existing events. fixtureId={}", fixtureId);
        updateExistingEvents(events, fixtureEventList);

        // 새로운 이벤트 업데이트
        int startSequence = fixtureEventList.size();
        log.info("try to save events from start sequence. fixtureId={}, startSequence={}", fixtureId, startSequence);
        saveEventsFromStartSequence(startSequence, events, fixture);
        log.info("saved live events fixtureId={}", fixtureId);
    }

    /**
     * 기존에 저장된 fixture 의 live event 에 문제가 감지된 경우 기존 데이터를 삭제하고 다시 저장합니다.
     *
     * @param response API 응답
     */
    public void resolveFixtureEventIntegrityError(FixtureSingleResponse response) {
        log.info("try to resolve fixture event integrity error");
        try {
            this.deleteExisingFixtureEventsAndReSaveAllEvents(response);
        } catch (Exception e) {
            log.error("failed to resolve fixture event integrity error", e);
        }
    }

    /**
     * LiveStatus 엔티티를 업데이트합니다
     *
     * @return 경기가 끝났는지 여부
     */
    public boolean updateLiveStatus(FixtureSingleResponse response) {
        _FixtureSingle fixtureSingle = response.getResponse().get(0);
        Long fixtureId = fixtureSingle.getFixture().getId();
        _Status status = fixtureSingle.getFixture().getStatus();
        _Goals goals = fixtureSingle.getGoals();
        log.info("started to update live status. fixtureId={}, status={}", fixtureId, status.getShortStatus());

        Fixture fixture = fixtureRepository.findById(fixtureId).orElseThrow();
        LiveStatus liveStatus = liveStatusRepository.findLiveStatusByFixture(fixture).orElseThrow();
        updateLiveStatusEntity(liveStatus, status, goals);
        status.getElapsed();
        log.info("updated live status. fixtureId={}, status={}, timeElapsed={}",
                fixtureId, status.getShortStatus(), status.getElapsed());
        return isFixtureFinished(status.getShortStatus());
    }

    private void deleteExisingFixtureEventsAndReSaveAllEvents(FixtureSingleResponse response) {
        Fixture fixture = fixtureRepository.findById(response.getResponse().get(0).getFixture().getId())
                .orElseThrow(() -> new IllegalArgumentException("기존에 캐싱된 fixture 정보가 없습니다."));

        deleteEvents(fixture);

        saveLiveEvent(response);
    }

    private void deleteEvents(Fixture fixture) {
        List<FixtureEvent> eventsList = fixtureEventRepository.findByFixtureOrderBySequenceDesc(fixture);

        for (FixtureEvent fixtureEvent : eventsList) {
            detachAndDeleteMatchPlayerIfNoLineup(fixtureEvent);
        }

        fixtureEventRepository.deleteAll(eventsList);
    }

    private League findLeagueOrThrow(Long leagueId) {
        return leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League 정보가 없습니다. leagueId=" + leagueId));
    }

    private Team findTeamOrThrow(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Home/Away Team 정보가 없습니다. teamId=" + teamId));
    }

    private Fixture findFixtureOrThrow(Long fixtureId) {
        return fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new IllegalArgumentException("Fixture 정보가 없습니다. fixtureId=" + fixtureId));
    }

    private void deleteReducedEvents(List<_Events> events, List<FixtureEvent> fixtureEventList) {
        for (int i = events.size(); i < fixtureEventList.size(); i++) {
            FixtureEvent fixtureEvent = fixtureEventList.get(i);
            detachAndDeleteMatchPlayerIfNoLineup(fixtureEvent);
            fixtureEventRepository.delete(fixtureEventList.get(i));
        }
    }

    /**
     * id : null 인 이벤트를 고려해서 MatchPlayer 에서 Player 연관관계 맺을지 아니면 unregistered player 로 처리할지 분기처리 해야함
     *
     * @param events
     * @param fixtureEventList
     */
    private void updateExistingEvents(List<_Events> events, List<FixtureEvent> fixtureEventList) {
        for (int i = 0; i < fixtureEventList.size(); i++) {
            _Events event = events.get(i);
            FixtureEvent fixtureEvent = fixtureEventList.get(i);
            // 이벤트가 다르다면 업데이트
            if (!isSameEvent(event, fixtureEvent)) {
                log.info("이벤트 업데이트 fixtureId={},sequence={}", fixtureEvent.getFixture().getFixtureId(), fixtureEvent.getSequence());

                detachAndDeleteMatchPlayerIfNoLineup(fixtureEvent);

                Fixture fixture = fixtureEvent.getFixture();
                Team team = teamRepository.findById(event.getTeam().getId())
                        .orElseThrow(() -> new IllegalArgumentException("팀 정보가 없습니다. teamId=" + event.getTeam().getId()));

                long fixtureId = fixture.getFixtureId();
                long teamId = team.getId();
                Long eventPlayerId = event.getPlayer().getId();
                String eventPlayerName = event.getPlayer().getName();
                Long eventAssistId = event.getAssist().getId();
                String eventAssistName = event.getAssist().getName();

                MatchPlayer eventPlayer;
                if (existEventPerson(eventPlayerId, eventPlayerName)) {
                    eventPlayer = getOrCreateMatchPlayerFromEventPlayer(
                            eventPlayerId,
                            eventPlayerName,
                            fixtureId,
                            teamId
                    );
                } else {
                    log.warn("eventPlayer 가 null 입니다. Player 가 Null 인 이벤트에 대한 조사가 필요합니다. fixtureId={}, eventsResponse={}", fixtureId, event);
                    eventPlayer = null;
                }

                MatchPlayer eventAssist = null;
                if (existEventPerson(eventAssistId, eventAssistName)) {
                    eventAssist = getOrCreateMatchPlayerFromEventPlayer(
                            eventAssistId,
                            eventAssistName,
                            fixtureId,
                            teamId
                    );
                }

                logIfEventPersonIsUnexpected(eventPlayer, eventAssist, fixtureId);

                updateEvent(event, fixtureEvent, team, eventPlayer, eventAssist);
            }
        }
    }

    /**
     * 새롭게 등장한 이벤트 들을 저장합니다. <br>
     * startSequence 는 response 의 event List 에서 새롭게 저장하기 시작해야 하는 지점의 index 입니다.
     *
     * @param startSequence 새롭게 저장하기 시작해야 하는 index
     * @param events        API 응답의 event List
     * @param fixture       fixture entity
     */
    private void saveEventsFromStartSequence(int startSequence, List<_Events> events, Fixture fixture) {
        Long fixtureId = fixture.getFixtureId();

        if (events.size() <= startSequence) {
            log.info("새로운 이벤트가 없습니다. fixtureId={}", fixtureId);
            return;
        }

        for (int sequence = startSequence; sequence < events.size(); sequence++) {
            _Events event = events.get(sequence);
            FixtureEvent fixtureEvent;

            try {
                Team team = teamRepository.findById(event.getTeam().getId())
                        .orElseThrow(() -> new IllegalArgumentException("팀 정보가 없습니다. teamId=" + event.getTeam().getId()));
                Long playerId = event.getPlayer().getId();
                String playerName = event.getPlayer().getName();
                MatchPlayer player = findOrCreateEventPlayer(playerId, playerName, fixtureId, team.getId());

                Long assistId = event.getAssist().getId();
                String assistName = event.getAssist().getName();
                MatchPlayer assist = findOrCreateEventPlayer(assistId, assistName, fixtureId, team.getId());

                fixtureEvent = FixtureEvent.builder()
                        .fixture(fixture)
                        .team(team)
                        .player(player)
                        .assist(assist)
                        .sequence(sequence)
                        .timeElapsed(event.getTime().getElapsed())
                        .extraTime(event.getTime().getExtra() == null ? 0 : event.getTime().getExtra())
                        .type(EventType.valueOf(event.getType().toUpperCase()))
                        .detail(event.getDetail())
                        .comments(event.getComments())
                        .build();
                fixtureEventRepository.save(fixtureEvent);
            } catch (Exception e) {
                log.error("이벤트 변환 실패. 빈 FixtureEvent 로 대체합니다. fixtureId={}, sequence={}, event={}", fixtureId, sequence, event, e);
                try {
                    fixtureEvent = FixtureEvent.builder()
                            .fixture(fixture)
                            .team(null)
                            .player(null)
                            .assist(null)
                            .sequence(sequence)
                            .timeElapsed(0)
                            .extraTime(0)
                            .type(EventType.UNKNOWN)
                            .detail("알 수 없는 이벤트")
                            .comments("알 수 없는 이벤트")
                            .build();
                    fixtureEventRepository.save(fixtureEvent);
                } catch (Exception exception) {
                    log.error("빈 이벤트로 저장 실패. fixtureId={}, sequence={}, event={}", fixtureId, sequence, event);
                }
            }
        }
    }

    private void updateEvent(_Events event, FixtureEvent fixtureEvent, Team team, @Nullable MatchPlayer player, @Nullable MatchPlayer assist) {
        fixtureEvent.setTeam(team);
        fixtureEvent.setPlayer(player);
        fixtureEvent.setAssist(assist);
        fixtureEvent.setTimeElapsed(event.getTime().getElapsed());
        fixtureEvent.setExtraTime(event.getTime().getExtra() == null ? 0 : event.getTime().getExtra());
        fixtureEvent.setType(EventType.valueOf(event.getType().toUpperCase()));
        fixtureEvent.setDetail(event.getDetail());
        fixtureEvent.setComments(event.getComments());
    }

    /**
     * 제공된 id 와 name 을 기반으로 일치하는 선수를 라인업에서 찾고, 라인업에 없다면 적절한 MatchPlayer 객체를 생성해 반환합니다. <br>
     * 제공된 선수 정보는 id 존재 여부에 따라 미등록 선수/등록 선수 조회로 나뉩니다. <br>
     * 미등록/등록 선수 두 케이스 모두 이상적인 경우는 {@link MatchLineup} 과 연관관계를 갖고 있는 {@link MatchPlayer} 조회에 성공하는 경우입니다. <br>
     * {@link MatchLineup} 과 연관관계를 맺은 {@link MatchPlayer} 가 없다면, 연관관계를 맺지 않고 일회용 {@link MatchPlayer} 를 생성합니다. <br>
     * 이외에 제공된 데이터에 문제가 있는 경우 null 을 반환합니다.
     *
     * @param playerId 이벤트 데이터에서 제공된 선수 id
     * @param playerName 이벤트 데이터에서 제공된 선수 이름
     * @param fixtureId 경기 id
     * @param teamId 이벤트 대상 team id
     * @return 조회된 MatchPlayer 객체
     */
    protected @Nullable MatchPlayer getOrCreateMatchPlayerFromEventPlayer(
            @Nullable Long playerId,
            @Nullable String playerName,
            long fixtureId,
            long teamId
    ) {
        if (playerId == null) {
            // CASE 미등록 선수
            //  ; ID 가 없는 미등록 선수를 제공합니다
            if (playerName == null) {
                log.warn("event 에 player id 와 name 이 모두 null 이므로 MatchPlayer 를 생성하지 않습니다. fixtureId={}", fixtureId);
                return null;
            }

            Optional<MatchPlayer> findUnregistered = findUnregisteredPlayerInLineup(playerName, fixtureId, teamId);
            if (findUnregistered.isPresent()) {
                log.info("라인업에 이름이 일치하는 unregistered player 가 존재합니다. fixtureId={}, name={}", fixtureId, playerName);
                return findUnregistered.get();
            }

            MatchPlayer unregisteredPlayer = MatchPlayer.builder()
                    .unregisteredPlayerName(playerName)
                    .build();
            log.info("이벤트에서 unregistered MatchPlayer 생성 name={}, fixtureId={}", playerName, fixtureId);
            return matchPlayerRepository.save(unregisteredPlayer);
        }

        // CASE 등록 선수
        //  ; ID 가 존재하는 등록 선수를 제공합니다
        Optional<Player> findPlayer = playerRepository.findById(playerId);
        if (findPlayer.isEmpty()) {
            MatchPlayer unregisteredPlayerButIdExist = MatchPlayer.builder()
                    .unregisteredPlayerName(playerName)
                    .build();
            log.warn("event 에 등장했고 id 가 주어졌으나 db에 존재하지 않는 선수이므로 unregistered MatchPlayer 로 저장합니다. " +
                    "playerId={}, name={}, fixtureId={}", playerId, playerName, fixtureId);
            return matchPlayerRepository.save(unregisteredPlayerButIdExist);
        }

        Player player = findPlayer.get();
        Optional<MatchPlayer> findMatchPlayer = matchPlayerRepository
                .findMatchPlayerByFixtureTeamAndPlayer(fixtureId, teamId, playerId);
        if (findMatchPlayer.isPresent()) {
            return findMatchPlayer.get();
        }

        log.warn("""
                event 에 등장한 [id={},name={}] 선수가 Player Entity 는 존재하지만 라인업에는 없습니다.
                Lineup 에 연관관계를 맺지 않은 등록선수로 MatchPlayer 를 생성합니다.
                fixtureId={}, teamId={}
                """, playerId, playerName, fixtureId, teamId);
        MatchPlayer matchPlayerNotRelatedWithLineup = MatchPlayer.builder().player(player).build();
        return matchPlayerRepository.save(matchPlayerNotRelatedWithLineup);
    }

    private @Nullable MatchPlayer findOrCreateEventPlayer(@Nullable Long id, @Nullable String name, Long fixtureId, long teamId) {
        boolean isEmptyPlayer = id == null && name == null;
        if (isEmptyPlayer) {
            return null;
        }

        boolean isUnregisteredPlayer = id == null;
        if (isUnregisteredPlayer) {
            Optional<MatchPlayer> findUnregistered = matchPlayerRepository.findUnregisteredPlayerByName(fixtureId, teamId, name);
            if (findUnregistered.isPresent()) {
                log.info("라인업에 이름이 일치하는 unregistered player 가 존재합니다. fixtureId={}, name={}", fixtureId, name);
                return findUnregistered.get();
            }

            log.info("unregistered event player 지만, Lineup 에 이름이 일치하는 선수가 없습니다. fixtureId={}, name={}", fixtureId, name);
            return matchPlayerRepository.save(MatchPlayer.builder()
                    .unregisteredPlayerName(name)
                    .build());
        }

        Optional<MatchPlayer> findMatchPlayer = matchPlayerRepository.findMatchPlayerByFixtureTeamAndPlayer(fixtureId, teamId, id);
        if (findMatchPlayer.isEmpty()) {
            Optional<Player> findPlayer = playerRepository.findById(id);
            if (findPlayer.isEmpty()) {
                log.warn("event player id 가 존재하지만 일치하는 player 가 db 에 존재하지 않습니다. unregistered player 로 MatchPlayer 를 생성합니다. fixtureId={}, playerId={}, name={}", fixtureId, id, name);
                return matchPlayerRepository.save(MatchPlayer.builder()
                        .unregisteredPlayerName(name)
                        .build());
            }

            log.warn("event player id 가 존재하지만 MatchPlayer 가 존재하지 않습니다. Lineup 연관관계를 맺지 않은 registered MatchPlayer 를 생성합니다. fixtureId={}, playerId={}, name={}", fixtureId, id, name);
            return matchPlayerRepository.save(MatchPlayer.builder()
                    .player(findPlayer.get())
                    .substitute(false)
                    .build());
        }

        return findMatchPlayer.get();
    }

    private void detachAndDeleteMatchPlayerIfNoLineup(FixtureEvent fixtureEvent) {
        MatchPlayer eventPlayer = fixtureEvent.getPlayer();
        MatchPlayer eventAssist = fixtureEvent.getAssist();

        if (eventPlayer != null && eventPlayer.getMatchLineup() == null) {
            fixtureEvent.setPlayer(null);
            matchPlayerRepository.delete(eventPlayer);
        }
        if (eventAssist != null && eventAssist.getMatchLineup() == null) {
            fixtureEvent.setAssist(null);
            matchPlayerRepository.delete(eventAssist);
        }
    }

    private Optional<MatchPlayer> findUnregisteredPlayerInLineup(String playerName, long fixtureId, long teamId) {
        if (!StringUtils.hasText(playerName)) {
            return Optional.empty();
        }
        return matchPlayerRepository.findUnregisteredPlayerByName(fixtureId, teamId, playerName);
    }

    protected boolean isSameEvent(_Events event, FixtureEvent fixtureEvent) {
        boolean isResponsePlayerNull = event.getPlayer() == null || (event.getPlayer().getId() == null && event.getPlayer().getName() == null);
        boolean isDbEntityPlayerNull = fixtureEvent.getPlayer() == null;
        if (isResponsePlayerNull != isDbEntityPlayerNull) {
            log.info("event player null 여부가 다릅니다. isResponsePlayerNull={}, isDbEntityPlayerNull={}", isResponsePlayerNull, isDbEntityPlayerNull);
            return false;
        }

        boolean bothNotNullPlayer = !isResponsePlayerNull;
        if (bothNotNullPlayer) {
            MatchPlayer matchPlayer = fixtureEvent.getPlayer();
            Long responsePlayerId = event.getPlayer().getId();
            String responsePlayerName = event.getPlayer().getName();
            if (isNotSameEventPlayer(responsePlayerId, responsePlayerName, matchPlayer)) {
                log.info("player 가 다릅니다. responsePlayerId={}, responsePlayerName={}, matchPlayer={}", responsePlayerId, responsePlayerName, matchPlayer);
                return false;
            }
        }

        boolean isResponseAssistNull = event.getAssist() == null || (event.getAssist().getId() == null && event.getAssist().getName() == null);
        boolean isDbEntityAssistNull = fixtureEvent.getAssist() == null;
        if (isResponseAssistNull != isDbEntityAssistNull) {
            log.info("event assist null 여부가 다릅니다. isResponseAssistNull={}, isDbEntityAssistNull={}", isResponseAssistNull, isDbEntityAssistNull);
            return false;
        }

        boolean bothNotNullAssist = !isResponseAssistNull;
        if (bothNotNullAssist) {
            MatchPlayer matchAssist = fixtureEvent.getAssist();
            Long responseAssistId = event.getAssist().getId();
            String responseAssistName = event.getAssist().getName();
            if (isNotSameEventPlayer(responseAssistId, responseAssistName, matchAssist)) {
                log.info("assist 가 다릅니다. responseAssistId={}, responseAssistName={}, matchAssist={}", responseAssistId, responseAssistName, matchAssist);
                return false;
            }
        }

        return isSameEventData(event, fixtureEvent);
    }

    /**
     * event 응답의 person 필드들(player, assist)은 완전히 null 일 수도 있습니다. <br>
     * 따라서 완전히 비어있는 matchPlayer 도 존재할 수 있음을 상정하고 비교해야 합니다.
     *
     * @param responsePlayerId
     * @param responsePlayerName
     * @param matchPlayer
     * @return 다를 경우 true 반환
     */
    private boolean isNotSameEventPlayer(
            @Nullable Long responsePlayerId,
            @Nullable String responsePlayerName,
            @Nullable MatchPlayer matchPlayer
    ) {
        final boolean SAME_PLAYER = false;
        final boolean NOT_SAME_PLAYER = true;

        boolean existResponsePlayer = existEventPerson(responsePlayerId, responsePlayerName);
        boolean existMatchPlayer = matchPlayer != null;

        // 둘 다 존재하지 않는다면
        if (!existResponsePlayer && !existMatchPlayer) {
            return SAME_PLAYER;
        }
        // 서로 존재 여부가 일치하지 않는 경우
        if (existResponsePlayer != existMatchPlayer) {
            return NOT_SAME_PLAYER;
        }

        boolean isResponseNotRegistered = responsePlayerId == null;
        boolean isMatchPlayerNotRegistered = matchPlayer.getPlayer() == null;
        // registeredPlayer 여부가 일치하지 않는 경우
        if (isResponseNotRegistered != isMatchPlayerNotRegistered) {
            return NOT_SAME_PLAYER;
        }

        boolean bothRegistered = !isResponseNotRegistered;
        if (bothRegistered) {
            if (Objects.equals(responsePlayerId, matchPlayer.getPlayer().getId())) {
                return SAME_PLAYER;
            } else {
                return NOT_SAME_PLAYER;
            }
        } else {
            if (Objects.equals(responsePlayerName, matchPlayer.getUnregisteredPlayerName())) {
                return SAME_PLAYER;
            } else {
                return NOT_SAME_PLAYER;
            }
        }
        // Unreachable code
    }

    protected boolean isSameEventData(_Events event, FixtureEvent fixtureEvent) {
        return Objects.equals(event.getTime().getElapsed(), fixtureEvent.getTimeElapsed())
                && isSafeSameExtraTime(event.getTime().getExtra(), fixtureEvent.getExtraTime())
                && event.getType().equalsIgnoreCase(fixtureEvent.getType().name())
                && event.getDetail().equalsIgnoreCase(fixtureEvent.getDetail())
                && Objects.equals(event.getComments(), fixtureEvent.getComments())
                && event.getTeam().getId().equals(fixtureEvent.getTeam().getId());
    }

    private boolean isSafeSameExtraTime(Integer apiExtraTime, Integer dbExtraTime) {
        int safeExtraTime = apiExtraTime == null || apiExtraTime == 0 ? 0 : apiExtraTime;
        int dbExtraTimeSafe = dbExtraTime == null || dbExtraTime == 0 ? 0 : dbExtraTime;
        return safeExtraTime == dbExtraTimeSafe;
    }

    private void updateLiveStatusEntity(LiveStatus liveStatus, _Status status, _Goals goals) {
        liveStatus.setElapsed(status.getElapsed());
        liveStatus.setLongStatus(status.getLongStatus());
        liveStatus.setShortStatus(status.getShortStatus());
        liveStatus.setHomeScore(goals.getHome());
        liveStatus.setAwayScore(goals.getAway());
    }

    private boolean isFixtureFinished(String shortStatus) {
        return FINISHED_STATUSES.contains(shortStatus);
    }

    private static boolean existEventPerson(Long eventAssistId, String eventAssistName) {
        return eventAssistId != null || eventAssistName != null;
    }

    private static void logIfEventPersonIsUnexpected(@Nullable MatchPlayer eventPlayer, @Nullable MatchPlayer eventAssist, long fixtureId) {
        if (eventPlayer == null) {
            log.warn("eventPlayer 가 null 입니다. fixtureId={}", fixtureId);
        }
        if (eventPlayer != null && eventPlayer.getPlayer() == null) {
            log.warn("eventPlayer 가 unregistered player 입니다. eventPlayer(name={}), fixtureId={}", eventPlayer.getUnregisteredPlayerName(), fixtureId);
        }
        if (eventAssist != null && eventAssist.getPlayer() == null) {
            log.warn("eventAssist 가 unregistered player 입니다. eventAssist(name={}), fixtureId={}", eventAssist.getUnregisteredPlayerName(), fixtureId);
        }
    }

    /**
     * FixtureSingleResponse 에서 필요한 데이터를 추출하여 간략하고 명료하게 값에 접근할 수 있도록 합니다.
     */
    protected static class ResponseValues {
        private final long fixtureId;
        private final long leagueId;
        private final long homeId;
        private final long awayId;
        private final List<_Events> events;

        private final Set<Long> homeLineupPlayerIds;
        private final Set<Long> awayLineupPlayerIds;
        private final List<_Lineups._StartPlayer> homeUnregisteredPlayers;
        private final List<_Lineups._StartPlayer> awayUnregisteredPlayers;

        @Nullable
        private final _Lineups homeLineup;
        @Nullable
        private final _Lineups awayLineup;

        private ResponseValues(FixtureSingleResponse response) {
            try {
                _FixtureSingle fixtureSingle = response.getResponse().get(0);
                this.fixtureId = fixtureSingle.getFixture().getId();
                this.leagueId = fixtureSingle.getLeague().getId();
                this.homeId = fixtureSingle.getTeams().getHome().getId();
                this.awayId = fixtureSingle.getTeams().getAway().getId();
                this.homeLineupPlayerIds = new HashSet<>();
                this.awayLineupPlayerIds = new HashSet<>();
                this.homeUnregisteredPlayers = new ArrayList<>();
                this.awayUnregisteredPlayers = new ArrayList<>();

                this.events = fixtureSingle.getEvents();

                _Lineups homeLineup = null;
                _Lineups awayLineup = null;
                for (_Lineups lineup : fixtureSingle.getLineups()) {
                    if (lineup.getTeam().getId() == homeId) {
                        homeLineup = lineup;
                    } else {
                        awayLineup = lineup;
                    }
                }
                if (homeLineup != null && awayLineup != null) {
                    this.homeLineup = homeLineup;
                    this.awayLineup = awayLineup;

                    List<_Lineups._StartPlayer> homeStartXI = homeLineup.getStartXI();
                    List<_Lineups._StartPlayer> homeSubstitutes = homeLineup.getSubstitutes();
                    List<_Lineups._StartPlayer> awayStartXI = awayLineup.getStartXI();
                    List<_Lineups._StartPlayer> awaySubstitutes = awayLineup.getSubstitutes();

                    addPlayerIds(homeStartXI, homeLineupPlayerIds, homeUnregisteredPlayers);
                    addPlayerIds(homeSubstitutes, homeLineupPlayerIds, homeUnregisteredPlayers);
                    addPlayerIds(awayStartXI, awayLineupPlayerIds, awayUnregisteredPlayers);
                    addPlayerIds(awaySubstitutes, awayLineupPlayerIds, awayUnregisteredPlayers);
                } else {
                    this.homeLineup = null;
                    this.awayLineup = null;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("FixtureSingleResponse 에서 필요한 데이터를 추출하는데 실패했습니다. " +
                        "API 응답 구조가 예상과 다르거나 FixtureId 및 home/away team 데이터가 API Response 에 존재하지 않습니다.", e);
            }
        }

        private void addPlayerIds(List<_Lineups._StartPlayer> playerList, Set<Long> idSet, List<_Lineups._StartPlayer> unregisteredPlayers) {
            for (_Lineups._StartPlayer startPlayer : playerList) {
                if (startPlayer.getPlayer().getId() == null) {
                    unregisteredPlayers.add(startPlayer);
                } else {
                    idSet.add(startPlayer.getPlayer().getId());
                }
            }
        }
    }
}
