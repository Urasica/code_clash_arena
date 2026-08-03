package com.battle.code.service;

import com.battle.code.dto.GameErrorMessage;
import com.battle.code.dto.GameNotificationMessage;
import com.battle.code.dto.MatchExecutionResultDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class GameSessionService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;
    private final LandGrabService landGrabService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchService matchService;
    private final MatchStateService stateService;
    private final Executor matchExecutor;
    private final ScheduledExecutorService reconnectScheduler;
    private final Duration reconnectGrace;

    public GameSessionService(
            RedisTemplate<String, Object> redisTemplate,
            LandGrabService landGrabService,
            SimpMessagingTemplate messagingTemplate,
            MatchService matchService,
            MatchStateService stateService,
            @Qualifier("matchExecutionExecutor") Executor matchExecutor,
            ScheduledExecutorService reconnectScheduler,
            @Value("${cca.match.reconnect-grace:5s}") Duration reconnectGrace
    ) {
        this.redisTemplate = redisTemplate;
        this.landGrabService = landGrabService;
        this.messagingTemplate = messagingTemplate;
        this.matchService = matchService;
        this.stateService = stateService;
        this.matchExecutor = matchExecutor;
        this.reconnectScheduler = reconnectScheduler;
        this.reconnectGrace = reconnectGrace;
    }

    public void handleCodeSubmission(String matchId, Long userId, String code, String language) {
        String roomKey = roomKey(matchId);
        if (stateService.current(matchId).filter(MatchStatus.WAITING::equals).isEmpty()) {
            return;
        }

        String p1Id = value(roomKey, "p1");
        String p2Id = value(roomKey, "p2");
        String role;
        if (String.valueOf(userId).equals(p1Id)) {
            role = "p1";
        } else if (String.valueOf(userId).equals(p2Id)) {
            role = "p2";
        } else {
            return;
        }

        redisTemplate.opsForHash().put(roomKey, role + "_code", code);
        redisTemplate.opsForHash().put(roomKey, role + "_lang", language);
        messagingTemplate.convertAndSend(
                gameTopic(matchId),
                GameNotificationMessage.playerSubmitted(role)
        );

        boolean bothReady = Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(roomKey, "p1_code"))
                && Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(roomKey, "p2_code"));
        if (!bothReady || !stateService.transition(matchId, MatchStatus.READY, MatchStatus.WAITING)) {
            return;
        }
        if (!stateService.transition(matchId, MatchStatus.RUNNING, MatchStatus.READY)) {
            return;
        }

        try {
            matchExecutor.execute(() -> runPvPMatch(matchId));
        } catch (RejectedExecutionException exception) {
            stateService.transition(matchId, MatchStatus.FAILED, MatchStatus.RUNNING);
            messagingTemplate.convertAndSend(gameTopic(matchId), GameErrorMessage.executionFailed());
            cleanupMatch(matchId);
        }
    }

    private void runPvPMatch(String matchId) {
        String roomKey = roomKey(matchId);
        try {
            String p1Code = requiredValue(roomKey, "p1_code");
            String p2Code = requiredValue(roomKey, "p2_code");
            String p1Lang = requiredValue(roomKey, "p1_lang");
            String p2Lang = requiredValue(roomKey, "p2_lang");
            String mapDataJson = requiredValue(roomKey, "mapData");
            String p1Id = requiredValue(roomKey, "p1");
            String p2Id = requiredValue(roomKey, "p2");

            MatchExecutionResultDto result = landGrabService.runPvPMatch(
                    matchId, p1Code, p1Lang, p2Code, p2Lang, mapDataJson
            ).asRealtimeResult();
            if (!stateService.transition(matchId, MatchStatus.PERSISTING, MatchStatus.RUNNING)) {
                throw new IllegalStateException("Match state changed before persistence.");
            }
            matchService.savePvPMatchResult(
                    matchId, Long.parseLong(p1Id), Long.parseLong(p2Id), result,
                    p1Code, p1Lang, p2Code, p2Lang
            );
            if (!stateService.transition(matchId, MatchStatus.COMPLETED, MatchStatus.PERSISTING)) {
                throw new IllegalStateException("Match state changed before completion.");
            }
            messagingTemplate.convertAndSend(gameTopic(matchId), result);
        } catch (Exception exception) {
            stateService.transition(
                    matchId,
                    MatchStatus.FAILED,
                    MatchStatus.RUNNING,
                    MatchStatus.PERSISTING
            );
            messagingTemplate.convertAndSend(gameTopic(matchId), GameErrorMessage.executionFailed());
        } finally {
            cleanupMatch(matchId);
        }
    }

    public boolean registerGameSession(String matchId, String sessionId, String userId) {
        String roomKey = roomKey(matchId);
        String p1Id = value(roomKey, "p1");
        String p2Id = value(roomKey, "p2");
        if (!userId.equals(p1Id) && !userId.equals(p2Id)) {
            return false;
        }

        redisTemplate.opsForValue().set("socket_game:" + sessionId, matchId, SESSION_TTL);
        String socketsKey = socketsKey(matchId, userId);
        redisTemplate.opsForSet().add(socketsKey, sessionId);
        redisTemplate.expire(socketsKey, SESSION_TTL);
        return true;
    }

    public void handleSocketDisconnection(String matchId, String userId, String sessionId) {
        String socketsKey = socketsKey(matchId, userId);
        redisTemplate.opsForSet().remove(socketsKey, sessionId);
        redisTemplate.delete("socket_game:" + sessionId);
        if (positive(redisTemplate.opsForSet().size(socketsKey))) {
            return;
        }
        reconnectScheduler.schedule(
                () -> confirmLastSocketDisconnected(matchId, userId),
                reconnectGrace.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    void confirmLastSocketDisconnected(String matchId, String userId) {
        if (positive(redisTemplate.opsForSet().size(socketsKey(matchId, userId)))) {
            return;
        }
        handleDisconnection(matchId, userId);
    }

    public void handleDisconnection(String matchId, String disconnectedUserId) {
        String roomKey = roomKey(matchId);
        String p1Id = value(roomKey, "p1");
        String p2Id = value(roomKey, "p2");
        if (!disconnectedUserId.equals(p1Id) && !disconnectedUserId.equals(p2Id)) {
            return;
        }
        if (!stateService.transition(
                matchId,
                MatchStatus.DISCONNECTED,
                MatchStatus.WAITING,
                MatchStatus.READY
        )) {
            return;
        }

        MatchExecutionResultDto result = MatchExecutionResultDto.disconnected(
                disconnectedUserId.equals(p1Id) ? "p2" : "p1"
        );
        try {
            matchService.savePvPMatchResult(
                    matchId,
                    Long.parseLong(p1Id),
                    Long.parseLong(p2Id),
                    result,
                    value(roomKey, "p1_code"),
                    value(roomKey, "p1_lang"),
                    value(roomKey, "p2_code"),
                    value(roomKey, "p2_lang")
            );
            messagingTemplate.convertAndSend(gameTopic(matchId), result);
        } finally {
            cleanupMatch(matchId);
        }
    }

    private void cleanupMatch(String matchId) {
        String roomKey = roomKey(matchId);
        String p1Id = value(roomKey, "p1");
        String p2Id = value(roomKey, "p2");
        List<String> keys = new ArrayList<>();
        keys.add(roomKey);
        cleanupPlayerMappings(matchId, p1Id, keys);
        cleanupPlayerMappings(matchId, p2Id, keys);
        redisTemplate.delete(keys);
    }

    private void cleanupPlayerMappings(String matchId, String playerId, List<String> keys) {
        if (playerId == null) {
            return;
        }
        String userSessionKey = "user_session:" + playerId;
        if (matchId.equals(redisTemplate.opsForValue().get(userSessionKey))) {
            keys.add(userSessionKey);
        }
        String socketsKey = socketsKey(matchId, playerId);
        Set<Object> sessionIds = redisTemplate.opsForSet().members(socketsKey);
        if (sessionIds != null) {
            sessionIds.stream().filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(sessionId -> "socket_game:" + sessionId)
                    .forEach(keys::add);
        }
        keys.add(socketsKey);
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private String requiredValue(String roomKey, String field) {
        String value = value(roomKey, field);
        if (value == null) {
            throw new IllegalStateException("Missing match field: " + field);
        }
        return value;
    }

    private String value(String roomKey, String field) {
        Object value = redisTemplate.opsForHash().get(roomKey, field);
        return value == null ? null : String.valueOf(value);
    }

    private String roomKey(String matchId) {
        return "match_room:" + matchId;
    }

    private String socketsKey(String matchId, String userId) {
        return "match_sockets:" + matchId + ":" + userId;
    }

    private String gameTopic(String matchId) {
        return "/topic/game/" + matchId;
    }
}
