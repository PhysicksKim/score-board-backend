package com.gyechunsik.scoreboard.websocket.domain.scoreboard.remote.autoremote.service;

import com.gyechunsik.scoreboard.websocket.domain.scoreboard.remote.autoremote.entity.AnonymousUser;
import com.gyechunsik.scoreboard.websocket.domain.scoreboard.remote.autoremote.entity.AutoRemoteGroup;
import com.gyechunsik.scoreboard.websocket.domain.scoreboard.remote.autoremote.repository.AnonymousUserRepository;
import com.gyechunsik.scoreboard.websocket.domain.scoreboard.remote.autoremote.repository.AutoRemoteGroupRepository;
import com.gyechunsik.scoreboard.websocket.domain.scoreboard.remote.autoremote.repository.AutoRemoteRedisRepository;
import com.gyechunsik.scoreboard.websocket.domain.scoreboard.remote.code.service.RedisRemoteCodeService;
import com.gyechunsik.scoreboard.websocket.domain.scoreboard.remote.code.RemoteCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class AutoRemoteService {

    private final RedisRemoteCodeService remoteCodeService;
    private final AutoRemoteRedisRepository autoRemoteRedisRepository;

    private final AutoRemoteGroupRepository groupRepository;
    private final AnonymousUserRepository userRepository;

    /**
     * 익명 유저를 생성하고 저장합니다.
     * @param autoRemoteGroup
     * @return
     */
    public AnonymousUser createAndSaveAnonymousUser(AutoRemoteGroup autoRemoteGroup) {
        if(autoRemoteGroup == null) {
            throw new IllegalArgumentException("AutoRemoteGroup 이 존재하지 않습니다.");
        }

        AnonymousUser anonymousUser = new AnonymousUser(); // UUID는 자동으로 생성됩니다.
        anonymousUser.setAutoRemoteGroup(autoRemoteGroup); // AutoRemoteGroup을 설정합니다.
        anonymousUser.setLastConnectedAt(LocalDateTime.now());
        return userRepository.save(anonymousUser);
    }

    /**
     * <pre>
     * 이전에 형성했던 자동 원격 그룹에 연결합니다.
     * 1) 비활성 상태 - 아직 그룹에 원격 코드가 발급되지 않은 경우
     * 해당 자동 원격 그룹에 RemoteCode 를 발급해주고, Redis 의 ActiveAutoRemoteGroups Hash 에 해당 자동 원격 그룹을 추가합니다.
     * 2) 활성된 상태 - 발급된 원격 코드가 있는 경우
     * RemoteCode 에 해당 사용자를 subscriber 로 추가합니다.
     * </pre>
     * @param principal
     * @param nickname
     * @return
     */
    public RemoteCode connectToPrevFormedAutoRemoteGroup(Principal principal, String nickname) {
        if (principal == null || !StringUtils.hasText(nickname)) {
            throw new IllegalArgumentException("잘못된 요청입니다. 사용자 UUID 또는 Principal 이 존재하지 않습니다.");
        }

        String userUuid = findPreCachedUserUUID(principal);
        if (userUuid == null) {
            throw new IllegalArgumentException("존재하지 않는 익명 유저 UUID 입니다.");
        }
        UUID uuid = UUID.fromString(userUuid);
        AnonymousUser findUser = userRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("일치하는 익명 유저가 없습니다."));

        AutoRemoteGroup autoRemoteGroup = findUser.getAutoRemoteGroup();
        String findRemoteCode = getActiveRemoteCodeFrom(autoRemoteGroup);
        RemoteCode remoteCode;

        if (isActiveRemoteCodeExist(findRemoteCode)) {
            log.info("RemoteCode 가 존재합니다. RemoteCode: {}", findRemoteCode);
            remoteCode = RemoteCode.of(findRemoteCode);
            remoteCodeService.addSubscriber(remoteCode, principal.getName(), nickname);
        } else {
            log.info("RemoteCode 가 존재하지 않습니다. RemoteCode 를 발급합니다.");
            remoteCode = remoteCodeService.generateCodeAndSubscribe(principal.getName(), nickname);
            activateAutoRemoteGroup(remoteCode, autoRemoteGroup.getId());
        }
        return remoteCode;
    }

    /**
     * 자동 원격 연결을 위해 사용합니다. 원격 연결 이전/이후에 본 메서드를 통해서 PrincipalId 와 UUID 를 이어주는 정보를 redis 에 캐싱합니다.
     * 클라이언트는 Cookie 로
     * Cookie 에서 얻은 User UUID 값을 검증하고, 통과하면 Redis 에 Value {key=Principal.Name, value=UUID} 로 캐싱합니다.
     * Cookie 에서 얻은 User UUID 가 DB 에 존재하지 않으면, IllegalArgumentException 을 던집니다.
     * @param principal
     * @param userUUID
     */
    public void validateAndCacheUserToRedis(Principal principal, String userUUID) {
        if(principal == null || !StringUtils.hasText(userUUID)) {
            throw new IllegalArgumentException("잘못된 요청입니다. 사용자 UUID 또는 Principal 이 존재하지 않습니다.");
        }

        UUID uuid = UUID.fromString(userUUID);
        AnonymousUser findUser = userRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 익명 유저입니다."));

        autoRemoteRedisRepository
                .setUserPreCacheForCookie(principal.getName(), userUUID);

        findUser.setLastConnectedAt(LocalDateTime.now());
    }

    public AutoRemoteGroup createAutoRemoteGroup() {
        AutoRemoteGroup created = new AutoRemoteGroup();
        created.setLastActiveAt(LocalDateTime.now());
        created.setExpiredAt(LocalDateTime.now().plusDays(30));
        groupRepository.save(created);
        return created;
    }

    public Optional<AutoRemoteGroup> getAutoRemoteGroup(Long id) {
        return groupRepository.findById(id);
    }

    /**
     * key : Remote:{remoteCode}_AutoRemoteGroupId
     * value : {AutoRemoteGroupId}
     * @param remoteCode
     * @param groupId
     */
    public void activateAutoRemoteGroup(RemoteCode remoteCode, long groupId) {
        autoRemoteRedisRepository.setActiveAutoRemoteKeyPair(Long.toString(groupId), remoteCode.getRemoteCode());
    }

    public Optional<Long> getActiveGroupIdBy(RemoteCode remoteCode) {
        Optional<String> activeAutoGroupId = autoRemoteRedisRepository.findAutoGroupIdFromRemoteCode(remoteCode.getRemoteCode());
        return activeAutoGroupId.map(Long::parseLong);
    }

    private String getActiveRemoteCodeFrom(AutoRemoteGroup autoRemoteGroup) {
        return autoRemoteRedisRepository
                .findRemoteCodeFromAutoGroupId(autoRemoteGroup.getId().toString());
    }

    public String findPreCachedUserUUID(Principal principal) {
        return autoRemoteRedisRepository
                .findPrincipalToUuid(principal.getName()).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Principal 입니다."));
    }
    public String findPreCachedUserUUID(String username) {
        return autoRemoteRedisRepository
                .findPrincipalToUuid(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("존재하지 않는 Principal 입니다.")
                );
    }

    private boolean isActiveRemoteCodeExist(String remoteCode) {
        if (remoteCode == null) {
            return false;
        }
        return StringUtils.hasText(remoteCode);
    }
}