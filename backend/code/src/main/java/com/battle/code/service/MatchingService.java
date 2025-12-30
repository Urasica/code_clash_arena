package com.battle.code.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final RedisTemplate<String, Object> redisTemplate;

    private String getQueueKey(String gameType) {
        return "match_queue:" + gameType;
    }

    /**
     * 대기열 참가
     * - Key: userId
     * - Score: 현재 시간 (System.currentTimeMillis())
     * - 이미 있는 userId면 Score(시간)만 갱신되거나 유지
     */
    public void joinQueue(String gameType, Long userId) {
        String key = getQueueKey(gameType);
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            // 없을 때만 추가 (새로 줄 서기)
            redisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            log.info("[match_queue] User joined queue: {}", userId);
        } else {
            log.info("[match_queue] User already in queue: {}", userId);
        }
    }

    // 대기열 취소
    public void cancelQueue(String gameType, Long userId) {
        String key = getQueueKey(gameType);
        redisTemplate.opsForZSet().remove(key, userId.toString());
        log.info("[match_queue] User cancelled queue: {}", userId);
    }

    // 대기열 크기 확인
    public Long getQueueSize(String gameType) {
        return redisTemplate.opsForZSet().zCard(getQueueKey(gameType));
    }

    /**
     * 유저 매칭
     * - Score(시간)가 가장 작은(오래된) 유저를 꺼냄
     * - TypedTuple을 반환하여 유저ID와 Score(입장시간)를 모두 가져옴 (롤백 대비)
     */
    public ZSetOperations.TypedTuple<Object> popUserWithScore(String gameType) {
        return redisTemplate.opsForZSet().popMin(getQueueKey(gameType));
    }

    /**
     * 롤백
     * - 매칭 실패 시, 원래 기다리던 시간으로 다시 넣기
     */
    public void returnToQueue(String gameType, String userId, Double originalScore) {
        String key = getQueueKey(gameType);
        if (userId != null && originalScore != null) {
            redisTemplate.opsForZSet().add(key, userId, originalScore);
            log.info("↺ [Rollback] User {} returned to queue with score {}", userId, originalScore);
        }
    }

    /**
     *
     * Key: match_room:{matchId}
     * Fields: p1, p2, gameType, mapData, status, p1_code, p2_code
     */
    public void createMatchRoom(String matchId, String gameType, String p1Id, String p2Id, String mapDataJson) {
        String key = "match_room:" + matchId;
        redisTemplate.opsForHash().put(key, "gameType", gameType);
        redisTemplate.opsForHash().put(key, "p1", p1Id);
        redisTemplate.opsForHash().put(key, "p2", p2Id);
        redisTemplate.opsForHash().put(key, "mapData", mapDataJson);
        redisTemplate.opsForHash().put(key, "status", "PLAYING");

        // 유저 -> 매치ID 매핑 (접속 종료 처리용)
        redisTemplate.opsForValue().set("user_session:" + p1Id, matchId);
        redisTemplate.opsForValue().set("user_session:" + p2Id, matchId);

        log.info("Match Room Created in Redis: {}", matchId);
    }
}