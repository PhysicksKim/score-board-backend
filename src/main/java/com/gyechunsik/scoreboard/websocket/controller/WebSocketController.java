package com.gyechunsik.scoreboard.websocket.controller;

import com.gyechunsik.scoreboard.websocket.controller.dto.NicknameEnrollRequest;
import com.gyechunsik.scoreboard.websocket.controller.dto.NicknameResponse;
import com.gyechunsik.scoreboard.websocket.messages.RequestRemoteCode;
import com.gyechunsik.scoreboard.websocket.service.MemorySocketUserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate template;
    private final MemorySocketUserService socketService;

    @MessageMapping("/hello")
    public void greeting(String message) {
        log.info("greetings : {}", message);
        template.convertAndSend("/topic/greetings", message); // topic/market/{마켓아이디}를 듣고있는 client에 전송
    }

    @PostMapping("/app/username")
    public ResponseEntity<?> enrollUsername(
            @RequestBody NicknameEnrollRequest request,
            HttpSession httpSession) {
        String nickname = request.getNickname();
        if (!StringUtils.hasText(nickname)) {
            log.info("nickname : {}", nickname);
            throw new IllegalArgumentException("닉네임을 입력해 주세요.");
        }
        log.info("httpSession : {}", httpSession);
        log.info("httpSession getId : {}", httpSession.getId());

        String prevNickname = (String) httpSession.getAttribute("username");
        log.info("nickname : {}", nickname);
        log.info("prevNickname : {}", prevNickname);
        if (prevNickname != null && prevNickname.equals(nickname)) {
            throw new IllegalArgumentException("이전 닉네임과 동일합니다");
        }

        if (!socketService.registerNickname(nickname, prevNickname)) {
            throw new IllegalArgumentException("이미 존재하는 닉네임입니다.");
        }
        httpSession.setAttribute("username", nickname);
        log.info("username : {}", nickname);
        return ResponseEntity.ok().body("Nickname enrolled: " + nickname);
    }

    // /app/remote.issueCode
    @MessageMapping("/remote.issueCode")
    public void issueCode(@Payload RequestRemoteCode message,
                                Message<Object> messageObject,
                                @Header("simpSessionId") String sessionId,
                                SimpMessageHeaderAccessor headerAccessor,
                                Principal principal) {
        log.info("sessionId : {}", sessionId);
        log.info("user header : {}", headerAccessor.getUser());
        log.info("user header : {}", messageObject.getHeaders().get(SimpMessageHeaderAccessor.USER_HEADER, Principal.class));

        template.convertAndSendToUser(sessionId, "/topic/code", "test-code");
    }
    /*
    1) template 으로 보낸 메세지 : principal 로 인식 된다
    destination:/user/topic/code
    content-type:text/plain;charset=UTF-8
    subscription:sub-1
    message-id:6674bbdf-4393-3597-b674-c8458675eb30-0
    content-length:9

    test-code
    ------
    2) @SendToUser 로 보낸 메세지 : 알아서 자체적으로 처리한다
    destination:/user/topic/code
    content-type:application/json
    subscription:sub-1
    message-id:6674bbdf-4393-3597-b674-c8458675eb30-1
    content-length:24

    {"code":"1234test-code"}


     */

    @MessageExceptionHandler
    public String handleException(Exception e) {
        log.error("error : {}", e.getMessage());
        return e.getMessage();
    }
}
