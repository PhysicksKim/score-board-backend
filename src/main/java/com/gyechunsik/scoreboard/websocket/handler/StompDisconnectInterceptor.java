package com.gyechunsik.scoreboard.websocket.handler;

import com.gyechunsik.scoreboard.websocket.domain.remote.code.RemoteCode;
import com.gyechunsik.scoreboard.websocket.domain.remote.code.RemoteCodeService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompDisconnectInterceptor implements ChannelInterceptor {

    private final SimpMessagingTemplate messagingTemplate;
    private final RemoteCodeService remoteCodeService;

    /**
     * 주의 : DISCONNECT 는 두 번 발생합니다.
     * Spring 은 안전한 종료를 보장하기 위해서, 사용자의 DISCONNECT 요청 뿐만 아니라, Websocket 종료시에도 DISCONNECT command 를 실행시킵니다.
     * 따라서 StompHeaderAccessor.getCommand() == DISCONNECT 를 다룰 때에는
     * 항상 두 번 실행될 가능성이 더 높음을 인지하고 작성해야 합니다.
     * @param message
     * @param channel
     * @param sent
     */
    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        HttpSession webSession = (HttpSession) sessionAttributes.get("webSession");

        if(accessor.getCommand() == null)
            return;

        switch ((accessor.getCommand())) {
            case CONNECT:
                log.info("세션 연결됨 :: {}", sessionId);
                break;
            case DISCONNECT:
                log.info("세션 끊음 :: {}", sessionId);
                Principal user = accessor.getUser();
                String remoteCode = (String) sessionAttributes.get("remoteCode");
                if(user == null) {
                    log.info("Principal User IS NULL");
                    break;
                }
                if(remoteCode == null) {
                    log.info("remoteCode IS NULL");
                    break;
                }

                log.info("remoteCode :: {} , Principal :: {}", remoteCode, user.getName());
                remoteCodeService.removeSubscriber(RemoteCode.of(remoteCode), user.getName());
                break;
            case SUBSCRIBE:
                log.info("구독 WebSession :: {}", sessionId);
                log.info("구독 주소 :: {}", destination);
                break;
            default:
                log.info("세션 상태 변경 command {} :: websocket {} , WebSession {}", accessor.getCommand(), sessionId, webSession);
                break;
        }
    }

}
