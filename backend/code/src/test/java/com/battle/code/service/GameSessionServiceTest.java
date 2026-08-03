package com.battle.code.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameSessionServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashes;
    private ValueOperations<String, Object> values;
    private SetOperations<String, Object> sets;
    private LandGrabService landGrabService;
    private SimpMessagingTemplate messagingTemplate;
    private MatchService matchService;
    private MatchStateService stateService;
    private Executor executor;
    private ScheduledExecutorService reconnectScheduler;
    private GameSessionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        hashes = mock(HashOperations.class);
        values = mock(ValueOperations.class);
        sets = mock(SetOperations.class);
        landGrabService = mock(LandGrabService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        matchService = mock(MatchService.class);
        stateService = mock(MatchStateService.class);
        executor = mock(Executor.class);
        reconnectScheduler = mock(ScheduledExecutorService.class);
        when(redisTemplate.opsForHash()).thenReturn(hashes);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.opsForSet()).thenReturn(sets);
        service = new GameSessionService(
                redisTemplate, landGrabService, messagingTemplate, matchService,
                stateService, executor, reconnectScheduler, Duration.ofSeconds(5)
        );
    }

    @Test
    void theSubmissionThatClaimsReadyAndRunningQueuesExecution() {
        String roomKey = "match_room:match-1";
        when(stateService.current("match-1")).thenReturn(Optional.of(MatchStatus.WAITING));
        when(hashes.get(roomKey, "p1")).thenReturn("1");
        when(hashes.get(roomKey, "p2")).thenReturn("2");
        when(hashes.hasKey(roomKey, "p1_code")).thenReturn(true);
        when(hashes.hasKey(roomKey, "p2_code")).thenReturn(true);
        when(stateService.transition("match-1", MatchStatus.READY, MatchStatus.WAITING)).thenReturn(true);
        when(stateService.transition("match-1", MatchStatus.RUNNING, MatchStatus.READY)).thenReturn(true);

        service.handleCodeSubmission("match-1", 1L, "code", "python");

        verify(executor).execute(any(Runnable.class));
        verifyNoInteractions(landGrabService);
    }

    @Test
    void aConcurrentSubmissionCannotQueueASecondExecution() {
        String roomKey = "match_room:match-1";
        when(stateService.current("match-1")).thenReturn(Optional.of(MatchStatus.WAITING));
        when(hashes.get(roomKey, "p1")).thenReturn("1");
        when(hashes.get(roomKey, "p2")).thenReturn("2");
        when(hashes.hasKey(roomKey, "p1_code")).thenReturn(true);
        when(hashes.hasKey(roomKey, "p2_code")).thenReturn(true);
        when(stateService.transition("match-1", MatchStatus.READY, MatchStatus.WAITING)).thenReturn(false);

        service.handleCodeSubmission("match-1", 1L, "code", "python");

        verify(executor, never()).execute(any());
    }

    @Test
    void aNonPlayerCannotRegisterAMatchSocket() {
        when(hashes.get("match_room:match-1", "p1")).thenReturn("1");
        when(hashes.get("match_room:match-1", "p2")).thenReturn("2");

        assertThat(service.registerGameSession("match-1", "socket-1", "9")).isFalse();

        verify(values, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void oneTabDisconnectDoesNotForfeitWhileAnotherMatchSocketExists() {
        when(sets.size("match_sockets:match-1:1")).thenReturn(1L);

        service.handleSocketDisconnection("match-1", "1", "socket-1");

        verify(reconnectScheduler, never()).schedule(any(Runnable.class), anyLong(), any());
        verifyNoInteractions(stateService);
    }

    @Test
    void lastSocketDisconnectStartsReconnectGraceInsteadOfImmediateForfeit() {
        when(sets.size("match_sockets:match-1:1")).thenReturn(0L);

        service.handleSocketDisconnection("match-1", "1", "socket-1");

        verify(reconnectScheduler).schedule(
                any(Runnable.class), eq(5000L), eq(java.util.concurrent.TimeUnit.MILLISECONDS)
        );
        verifyNoInteractions(stateService);
    }

    @Test
    void aDisconnectCannotOverrideRunningState() {
        when(hashes.get("match_room:match-1", "p1")).thenReturn("1");
        when(hashes.get("match_room:match-1", "p2")).thenReturn("2");
        when(stateService.transition(
                "match-1", MatchStatus.DISCONNECTED, MatchStatus.WAITING, MatchStatus.READY
        )).thenReturn(false);

        service.handleDisconnection("match-1", "1");

        verifyNoInteractions(matchService);
        verifyNoInteractions(messagingTemplate);
    }
}
