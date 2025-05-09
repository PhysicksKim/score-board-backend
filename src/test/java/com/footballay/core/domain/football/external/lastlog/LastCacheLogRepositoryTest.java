package com.footballay.core.domain.football.external.lastlog;

import com.footballay.core.domain.football.constant.LeagueId;
import com.footballay.core.domain.football.constant.TeamId;
import com.footballay.core.domain.football.persistence.apicache.ApiCacheType;
import com.footballay.core.domain.football.persistence.apicache.LastCacheLog;
import com.footballay.core.domain.football.repository.apicache.LastCacheLogRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DataJpaTest
class LastCacheLogRepositoryTest {

    @Autowired
    private LastCacheLogRepository repository;

    @Autowired
    private EntityManager em;

    @DisplayName("LastCacheLog 엔티티를 저장하고 이를 다시 가져옵니다.")
    @Test
    void success_API_CACHE() {
        // given
        ZonedDateTime cachedAt = ZonedDateTime.now();
        LastCacheLog lastCacheLog = LastCacheLog.builder()
                .apiCacheType(ApiCacheType.LEAGUE)
                .parametersJson(Map.of("leagueId", (int)LeagueId.EPL))
                .lastCachedAt(cachedAt)
                .build();

        // when
        LastCacheLog save = repository.save(lastCacheLog);
        log.info("saved api cache :: {}", save);
        em.clear();
        List<LastCacheLog> all = repository.findAll();
        log.info("All Cached LastCacheLog List");
        for (LastCacheLog cache : all) {
            log.info("cache : {}", cache);
        }

        // then
        assertThat(lastCacheLog).isEqualTo(all.get(0));
        assertThat(all.get(0)).isEqualTo(save).isEqualTo(lastCacheLog);
    }

    @DisplayName("여러 LastCacheLog 엔티티를 저장하고, 저장한 엔티티와 각각 찾은 엔티티가 동일해야 합니다.")
    @Test
    void success_multipleApiCacheEntity_And_FindOne() {
        // given
        ZonedDateTime leagueCacheLDT = ZonedDateTime.now();
        LastCacheLog leagueCache = LastCacheLog.builder()
                .apiCacheType(ApiCacheType.LEAGUE)
                .parametersJson(Map.of("leagueId", (int) LeagueId.EPL))
                .lastCachedAt(leagueCacheLDT)
                .build();
        ZonedDateTime teamCacheLDT = ZonedDateTime.now();
        LastCacheLog teamCache = LastCacheLog.builder()
                .apiCacheType(ApiCacheType.TEAM)
                .parametersJson(Map.of("teamId", (int)TeamId.MANCITY))
                .lastCachedAt(teamCacheLDT)
                .build();

        List<LastCacheLog> leagueCacheList = List.of(leagueCache, teamCache);

        // when
        List<LastCacheLog> savedLastCacheLog = repository.saveAll(leagueCacheList);
        em.clear();
        Optional<LastCacheLog> teamFind
                = repository.findLastCacheLogByApiCacheTypeAndParametersJson(ApiCacheType.TEAM, Map.of("teamId", (int) TeamId.MANCITY));
        Optional<LastCacheLog> leagueFind
                = repository.findLastCacheLogByApiCacheTypeAndParametersJson(ApiCacheType.LEAGUE, Map.of("leagueId", (int) LeagueId.EPL));
        List<LastCacheLog> findAll = repository.findAll();

        // then
        log.info("Cache _League :: {}", leagueCache);
        log.info("Cache _Team :: {}", teamCache);
        log.info("Find _League :: {}", leagueFind);
        log.info("Find _Team :: {}", teamFind);

        assertThat(savedLastCacheLog).hasSize(2);
        assertThat(savedLastCacheLog).containsAll(findAll);

        assertThat(leagueFind).isNotEmpty();
        assertThat(leagueFind.get()).isEqualTo(leagueCache);
        assertThat(teamFind).isNotEmpty();
        assertThat(teamFind.get()).isEqualTo(teamCache);

        assertThat(savedLastCacheLog).contains(leagueFind.get(), teamFind.get());
        assertThat(findAll).contains(leagueCache, teamCache);
    }

}