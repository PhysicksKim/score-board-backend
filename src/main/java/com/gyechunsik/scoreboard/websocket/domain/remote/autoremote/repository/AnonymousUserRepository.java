package com.gyechunsik.scoreboard.websocket.domain.remote.autoremote.repository;

import com.gyechunsik.scoreboard.websocket.domain.remote.autoremote.entity.AnonymousUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, UUID> {

}
