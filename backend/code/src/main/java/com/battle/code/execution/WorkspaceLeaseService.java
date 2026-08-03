package com.battle.code.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class WorkspaceLeaseService {

    private static final String KEY_PREFIX = "ai_workspace:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration idleTtl;
    private final Clock clock;

    @Autowired
    public WorkspaceLeaseService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${cca.engine.workspace-idle-ttl:30m}") Duration idleTtl
    ) {
        this(redisTemplate, idleTtl, Clock.systemUTC());
    }

    WorkspaceLeaseService(RedisTemplate<String, Object> redisTemplate, Duration idleTtl, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.idleTtl = idleTtl;
        this.clock = clock;
    }

    public void create(String matchId, long ownerId) {
        String key = key(matchId);
        redisTemplate.opsForHash().putAll(key, Map.of(
                "owner", String.valueOf(ownerId),
                "status", WorkspaceStatus.READY.name(),
                "expiresAt", expiresAt()
        ));
        redisTemplate.expire(key, idleTtl);
    }

    public void requireOwnerAndTouch(String matchId, long ownerId, WorkspaceStatus nextStatus) {
        String key = key(matchId);
        Object owner = redisTemplate.opsForHash().get(key, "owner");
        if (owner == null) {
            throw new NoSuchElementException("Match workspace not found or expired.");
        }
        if (!String.valueOf(ownerId).equals(String.valueOf(owner))) {
            throw new AccessDeniedException("Match workspace belongs to another user.");
        }

        Object current = redisTemplate.opsForHash().get(key, "status");
        if (WorkspaceStatus.RUNNING.name().equals(current)) {
            throw new IllegalStateException("Match execution is already running.");
        }
        redisTemplate.opsForHash().put(key, "status", nextStatus.name());
        redisTemplate.opsForHash().put(key, "expiresAt", expiresAt());
        redisTemplate.expire(key, idleTtl);
    }

    public boolean exists(String matchId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(matchId)));
    }

    public void release(String matchId) {
        redisTemplate.delete(key(matchId));
    }

    private String expiresAt() {
        return Instant.now(clock).plus(idleTtl).toString();
    }

    private String key(String matchId) {
        return KEY_PREFIX + matchId;
    }

    public enum WorkspaceStatus {
        READY,
        COMPILED,
        RUNNING
    }
}
