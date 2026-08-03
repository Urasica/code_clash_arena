package com.battle.code.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceLeaseServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashes;
    private WorkspaceLeaseService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        hashes = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashes);
        service = new WorkspaceLeaseService(
                redisTemplate,
                Duration.ofMinutes(30),
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createsAnOwnedExpiringLease() {
        service.create("match-1", 7L);

        verify(hashes).putAll("ai_workspace:match-1", java.util.Map.of(
                "owner", "7",
                "status", "READY",
                "expiresAt", "2026-08-04T00:30:00Z"
        ));
        verify(redisTemplate).expire("ai_workspace:match-1", Duration.ofMinutes(30));
    }

    @Test
    void rejectsAnotherUserWithoutRefreshingTheLease() {
        when(hashes.get("ai_workspace:match-1", "owner")).thenReturn("7");

        assertThatThrownBy(() -> service.requireOwnerAndTouch(
                "match-1", 9L, WorkspaceLeaseService.WorkspaceStatus.COMPILED
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsExpiredLeases() {
        when(hashes.get("ai_workspace:match-1", "owner")).thenReturn(null);

        assertThatThrownBy(() -> service.requireOwnerAndTouch(
                "match-1", 7L, WorkspaceLeaseService.WorkspaceStatus.COMPILED
        )).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void preventsASecondRunClaim() {
        when(hashes.get("ai_workspace:match-1", "owner")).thenReturn("7");
        when(hashes.get("ai_workspace:match-1", "status")).thenReturn("RUNNING");

        assertThatThrownBy(() -> service.requireOwnerAndTouch(
                "match-1", 7L, WorkspaceLeaseService.WorkspaceStatus.RUNNING
        )).isInstanceOf(IllegalStateException.class);
    }
}
