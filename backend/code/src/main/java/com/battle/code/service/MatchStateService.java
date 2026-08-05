package com.battle.code.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class MatchStateService {

    private static final Duration MATCH_TTL = Duration.ofMinutes(30);
    private static final DefaultRedisScript<Long> TRANSITION_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'status')
            if not current then return -1 end
            for i = 1, #ARGV - 2 do
                if current == ARGV[i] then
                    redis.call('HSET', KEYS[1], 'status', ARGV[#ARGV - 1])
                    redis.call('EXPIRE', KEYS[1], ARGV[#ARGV])
                    return 1
                end
            end
            return 0
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public MatchStateService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<MatchStatus> current(String matchId) {
        Object value = redisTemplate.opsForHash().get(roomKey(matchId), "status");
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MatchStatus.valueOf(String.valueOf(value)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean transition(String matchId, MatchStatus target, MatchStatus... expected) {
        if (expected.length == 0) {
            throw new IllegalArgumentException("At least one source state is required.");
        }
        List<Object> arguments = new ArrayList<>();
        Arrays.stream(expected).map(Enum::name).forEach(arguments::add);
        arguments.add(target.name());
        arguments.add(String.valueOf(MATCH_TTL.toSeconds()));
        Long result = redisTemplate.execute(
                TRANSITION_SCRIPT,
                List.of(roomKey(matchId)),
                arguments.toArray()
        );
        return Long.valueOf(1L).equals(result);
    }

    private String roomKey(String matchId) {
        return "match_room:" + matchId;
    }
}
