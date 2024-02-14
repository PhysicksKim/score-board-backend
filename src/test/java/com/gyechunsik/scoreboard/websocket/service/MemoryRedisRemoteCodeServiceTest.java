package com.gyechunsik.scoreboard.websocket.service;

import com.gyechunsik.scoreboard.config.TestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(TestContainerConfig.class)
public class MemoryRedisRemoteCodeServiceTest {

    @Autowired
    private MemoryRedisRemoteCodeService memoryRedisRemoteCodeService ;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private Principal mockPrincipal;

    private static final String REMOTECODE_SET_PREFIX = "remote:";

    @BeforeEach
    void setUp() {
        memoryRedisRemoteCodeService = new MemoryRedisRemoteCodeService(stringRedisTemplate);
        when(mockPrincipal.getName()).thenReturn("testUser");
    }

    @Test
    void testRedisConnect() {
        stringRedisTemplate.opsForValue().set("testKey", "testValue");
    }

    @Test
    void testGenerateAndValidateCode() {
        RemoteCode remoteCode = memoryRedisRemoteCodeService.generateCode(mockPrincipal);
        assertNotNull(remoteCode);
        assertTrue(memoryRedisRemoteCodeService.isValidCode(remoteCode));

        // Cleanup
        stringRedisTemplate.delete(REMOTECODE_SET_PREFIX + remoteCode.getRemoteCode());
    }

    @Test
    void testAddAndRemoveSubscriber() {
        RemoteCode remoteCode = memoryRedisRemoteCodeService.generateCode(mockPrincipal);
        String subscriber = "anotherUser";

        assertTrue(memoryRedisRemoteCodeService.addSubscriber(remoteCode, subscriber));
        assertTrue(memoryRedisRemoteCodeService.getSubscribers(remoteCode).contains(subscriber));
        assertTrue(memoryRedisRemoteCodeService.removeSubscriber(remoteCode, subscriber));
        assertFalse(memoryRedisRemoteCodeService.getSubscribers(remoteCode).contains(subscriber));

        // Cleanup
        stringRedisTemplate.delete(REMOTECODE_SET_PREFIX + remoteCode.getRemoteCode());
    }

    @Test
    void testSetExpiration() throws InterruptedException {
        RemoteCode remoteCode = memoryRedisRemoteCodeService.generateCode(mockPrincipal);
        memoryRedisRemoteCodeService.setExpiration(remoteCode, 1); // 1 second

        Thread.sleep(1500); // Wait for the key to expire

        assertFalse(memoryRedisRemoteCodeService.isValidCode(remoteCode));
    }
}
