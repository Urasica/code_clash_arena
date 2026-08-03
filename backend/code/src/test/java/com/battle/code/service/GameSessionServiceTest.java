package com.battle.code.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameSessionServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private ValueOperations<String, Object> valueOperations;
    private LandGrabService landGrabService;
    private SimpMessagingTemplate messagingTemplate;
    private MatchService matchService;
    private GameSessionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        valueOperations = mock(ValueOperations.class);
        landGrabService = mock(LandGrabService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        matchService = mock(MatchService.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new GameSessionService(redisTemplate, landGrabService, messagingTemplate, matchService);
    }

    @Test
    void onlyTheSubmissionThatAcquiresResolutionCanStartTheEngine() {
        String roomKey = "match_room:match-1";
        when(hashOperations.hasKey(roomKey, "p1")).thenReturn(true);
        when(hashOperations.hasKey(roomKey, "resolution")).thenReturn(false);
        when(hashOperations.get(roomKey, "p1")).thenReturn("1");
        when(hashOperations.get(roomKey, "p2")).thenReturn("2");
        when(hashOperations.hasKey(roomKey, "p1_code")).thenReturn(true);
        when(hashOperations.hasKey(roomKey, "p2_code")).thenReturn(true);
        when(hashOperations.putIfAbsent(roomKey, "resolution", "RUNNING")).thenReturn(false);

        service.handleCodeSubmission("match-1", 1L, "code", "python");

        verifyNoInteractions(landGrabService);
        verify(hashOperations, never()).put(roomKey, "status", "RUNNING");
    }

    @Test
    void aNonPlayerCannotRegisterAMatchSocket() {
        when(hashOperations.get("match_room:match-1", "p1")).thenReturn("1");
        when(hashOperations.get("match_room:match-1", "p2")).thenReturn("2");

        boolean registered = service.registerGameSession("match-1", "socket-1", "9");

        assertThat(registered).isFalse();
        verify(valueOperations, never()).set(any(), any(), any(java.time.Duration.class));
    }

    @Test
    void aDisconnectCannotOverrideAnExecutionThatAlreadyStarted() {
        String roomKey = "match_room:match-1";
        when(hashOperations.hasKey(roomKey, "p1")).thenReturn(true);
        when(hashOperations.get(roomKey, "p1")).thenReturn("1");
        when(hashOperations.get(roomKey, "p2")).thenReturn("2");
        when(hashOperations.putIfAbsent(roomKey, "resolution", "DISCONNECTED:1")).thenReturn(false);

        service.handleDisconnection("match-1", "1");

        verifyNoInteractions(matchService);
        verifyNoInteractions(messagingTemplate);
    }
}
