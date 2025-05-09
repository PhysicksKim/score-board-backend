package com.footballay.core.websocket.domain.scoreboard.remote.autoremote.repository;

import com.footballay.core.websocket.domain.scoreboard.remote.autoremote.entity.AnonymousUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, UUID> {

}
