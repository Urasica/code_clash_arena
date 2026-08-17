package com.battle.code.service;

import com.battle.code.observability.MatchTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class MatchingService {

    private static final Duration MATCH_TTL = Duration.ofMinutes(30);
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(1);
    private static final DefaultRedisScript<Long> JOIN_QUEUE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 or redis.call('EXISTS', KEYS[3]) == 1 then
                return -1
            end
            return redis.call('ZADD', KEYS[1], 'NX', ARGV[1], ARGV[2])
            """, Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> POP_PAIR_SCRIPT = new DefaultRedisScript<>("""
            local pair = redis.call('ZRANGE', KEYS[1], 0, 1, 'WITHSCORES')
            if #pair < 4 then return {} end
            redis.call('SET', 'match_reservation:' .. pair[1], ARGV[1], 'EX', ARGV[2])
            redis.call('SET', 'match_reservation:' .. pair[3], ARGV[1], 'EX', ARGV[2])
            redis.call('ZREM', KEYS[1], pair[1], pair[3])
            return pair
            """, List.class);
    private static final DefaultRedisScript<Long> RETURN_PAIR_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', 'match_reservation:' .. ARGV[1], 'match_reservation:' .. ARGV[3])
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1], ARGV[4], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CREATE_ROOM_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1],
                'gameType', ARGV[1], 'p1', ARGV[2], 'p2', ARGV[3],
                'mapData', ARGV[4], 'status', ARGV[5])
            redis.call('EXPIRE', KEYS[1], ARGV[6])
            redis.call('SET', KEYS[2], ARGV[7], 'EX', ARGV[6])
            redis.call('SET', KEYS[3], ARGV[7], 'EX', ARGV[6])
            redis.call('DEL', KEYS[4], KEYS[5])
            return 1
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final MatchTelemetry telemetry;

    @Autowired
    public MatchingService(RedisTemplate<String, Object> redisTemplate, MatchTelemetry telemetry) {
        this.redisTemplate = redisTemplate;
        this.telemetry = telemetry;
    }

    MatchingService(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, MatchTelemetry.noOp());
    }

    public void joinQueue(String gameType, Long userId) {
        String user = userId.toString();
        Long added = redisTemplate.execute(
                JOIN_QUEUE_SCRIPT,
                List.of(queueKey(gameType), "user_session:" + user, "match_reservation:" + user),
                String.valueOf(System.currentTimeMillis()),
                user
        );
        if (Long.valueOf(-1L).equals(added)) {
            telemetry.queueEvent(gameType, "rejected");
            throw new IllegalStateException("User is already assigned to a match.");
        }
        if (Long.valueOf(1L).equals(added)) {
            telemetry.queueEvent(gameType, "joined");
            log.info("User {} joined {} queue", userId, gameType);
        } else {
            telemetry.queueEvent(gameType, "duplicate");
        }
        refreshQueueDepth(gameType);
    }

    public void cancelQueue(String gameType, Long userId) {
        redisTemplate.opsForZSet().remove(queueKey(gameType), userId.toString());
        telemetry.queueEvent(gameType, "cancelled");
        refreshQueueDepth(gameType);
    }

    @SuppressWarnings("unchecked")
    public Optional<MatchPair> popPair(String gameType, String reservationId) {
        List<Object> pair = redisTemplate.execute(
                POP_PAIR_SCRIPT,
                List.of(queueKey(gameType)),
                reservationId,
                String.valueOf(RESERVATION_TTL.toSeconds())
        );
        if (pair == null || pair.size() < 4) {
            refreshQueueDepth(gameType);
            return Optional.empty();
        }
        telemetry.queueEvent(gameType, "matched");
        refreshQueueDepth(gameType);
        return Optional.of(new MatchPair(
                new QueuedPlayer(String.valueOf(pair.get(0)), Double.parseDouble(String.valueOf(pair.get(1)))),
                new QueuedPlayer(String.valueOf(pair.get(2)), Double.parseDouble(String.valueOf(pair.get(3))))
        ));
    }

    public void returnPair(String gameType, MatchPair pair) {
        redisTemplate.execute(
                RETURN_PAIR_SCRIPT,
                List.of(queueKey(gameType)),
                pair.p1().userId(),
                String.valueOf(pair.p1().score()),
                pair.p2().userId(),
                String.valueOf(pair.p2().score())
        );
        telemetry.queueEvent(gameType, "returned");
        refreshQueueDepth(gameType);
    }

    public void createMatchRoom(String matchId, String gameType, String p1Id, String p2Id, String mapDataJson) {
        redisTemplate.execute(
                CREATE_ROOM_SCRIPT,
                List.of(
                        "match_room:" + matchId,
                        "user_session:" + p1Id,
                        "user_session:" + p2Id,
                        "match_reservation:" + p1Id,
                        "match_reservation:" + p2Id
                ),
                gameType,
                p1Id,
                p2Id,
                mapDataJson,
                MatchStatus.WAITING.name(),
                String.valueOf(MATCH_TTL.toSeconds()),
                matchId
        );
        log.info("Match room {} created", matchId);
    }

    private String queueKey(String gameType) {
        return "match_queue:" + gameType;
    }

    private void refreshQueueDepth(String gameType) {
        try {
            Long depth = redisTemplate.opsForZSet().size(queueKey(gameType));
            telemetry.queueDepth(gameType, depth == null ? 0 : depth);
        } catch (RuntimeException exception) {
            log.warn("Could not refresh matchmaking queue depth for {}", gameType, exception);
        }
    }

    public record QueuedPlayer(String userId, double score) {
    }

    public record MatchPair(QueuedPlayer p1, QueuedPlayer p2) {
    }
}
