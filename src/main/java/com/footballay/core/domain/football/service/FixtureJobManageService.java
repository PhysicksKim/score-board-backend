package com.footballay.core.domain.football.service;

import com.footballay.core.domain.football.persistence.Fixture;
import com.footballay.core.domain.football.scheduler.lineup.PreviousMatchJobSchedulerService;
import com.footballay.core.domain.football.scheduler.live.LiveMatchJobSchedulerService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FixtureJobManageService {

    private final PreviousMatchJobSchedulerService previousMatchJobSchedulerService;
    private final LiveMatchJobSchedulerService liveMatchJobSchedulerService;

    private final FixtureDataIntegrityService dataIntegrityService;

    private final static int LINEUP_ANNOUNCE_BEFORE_HOUR = 1;

    /**
     * 특정 Fixture 관련 Job 들을 등록합니다. <br>
     * 실제 quartz job 등록은 위임하며 이 메서드는 Job 등록 전에 데이터 정합성을 체크하고, Job 등록 후에 Fixture 의 상태를 변경합니다.
     *
     * @see LiveMatchJobSchedulerService
     * @param fixture Fixture 연관 데이터를 모두 사용하므로 Fetch Join 으로 load 된 Fixture 가 아니면 N+1 이 발생할 수 있습니다.
     */
    public void addFixtureJobs(@NotNull Fixture fixture) {
        long fixtureId = fixture.getFixtureId();
        try {
            dataIntegrityService.cleanUpFixtureLiveData(fixture);
            enrollFixtureJobs(fixture, fixtureId);

            fixture.setAvailable(true);
            log.info("add job finished for fixtureId={}", fixtureId);
        } catch (SchedulerException e) {
            log.error("Failed to add fixture jobs for fixtureId={}", fixtureId, e);
        }
    }

    /**
     * Fixture 에 대한 Job 을 등록합니다. <br>
     * job 등록 전에 기존에 존재하던 데이터들은 삭제하기 위해 cleanup 작업을 수행합니다.
     * @param fixture
     * @param fixtureId
     * @throws SchedulerException
     */
    private void enrollFixtureJobs(Fixture fixture, long fixtureId) throws SchedulerException {
        ZonedDateTime kickOffTime = toSeoulZonedDateTime(
                fixture.getDate(), fixture.getTimezone(), fixture.getTimestamp()
        );
        ZonedDateTime lineupAnnounceTime = kickOffTime.minusHours(LINEUP_ANNOUNCE_BEFORE_HOUR);

        addPreviousMatchJobIfMatchNotStarted(fixtureId, lineupAnnounceTime, kickOffTime);
        liveMatchJobSchedulerService.addJob(fixtureId, kickOffTime);

        log.info("Fixture jobs added for fixtureId={}", fixtureId);
    }

    private void addPreviousMatchJobIfMatchNotStarted(long fixtureId, ZonedDateTime lineupAnnounceTime, ZonedDateTime kickOffTime) throws SchedulerException {
        if(ZonedDateTime.now().isBefore(kickOffTime)) {
            previousMatchJobSchedulerService.addJob(fixtureId, lineupAnnounceTime);
        }
    }

    public void removeFixtureJobs(Fixture fixture) throws SchedulerException {
        long fixtureId = fixture.getFixtureId();

        previousMatchJobSchedulerService.removeJob(fixtureId);
        liveMatchJobSchedulerService.removeJob(fixtureId);
        liveMatchJobSchedulerService.removePostJob(fixtureId);

        log.info("Fixture jobs removed for fixtureId={}", fixtureId);
        fixture.setAvailable(false);
    }

    private ZonedDateTime toSeoulZonedDateTime(LocalDateTime kickoffTime, String timeZone, long timestamp) {
        try {
            ZoneId zoneId = ZoneId.of(timeZone);
            return ZonedDateTime.of(kickoffTime, zoneId)
                    .withZoneSameInstant(ZoneId.of("Asia/Seoul"));
        } catch (ZoneRulesException e) {
            Instant instant = Instant.ofEpochSecond(timestamp);
            return ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Seoul"));
        }
    }
}
