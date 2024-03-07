package com.gyechunsik.scoreboard.websocket.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * <pre>
 * {
 *   nickname: "gyechunhoe"
 * }
 * </pre>
 */
@Getter
@ToString
@AllArgsConstructor
public class RemoteIssueRequestMessage {

    protected String nickname;
    protected boolean autoRemote;

}