package com.battle.code.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchingServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ZSetOperations<String, Object> zSetOperations;
    private MatchingService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.size("match_queue:land_grab")).thenReturn(0L);
        service = new MatchingService(redisTemplate);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void atomicPairPopReturnsBothUsersAndOriginalScores() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("1", "1000", "2", "2000"));

        var pair = service.popPair("land_grab", "match-1").orElseThrow();

        assertThat(pair.p1().userId()).isEqualTo("1");
        assertThat(pair.p1().score()).isEqualTo(1000D);
        assertThat(pair.p2().userId()).isEqualTo("2");
        assertThat(pair.p2().score()).isEqualTo(2000D);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pairPopDoesNothingWhenFewerThanTwoUsersExist() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of());

        assertThat(service.popPair("land_grab", "match-1")).isEmpty();
    }
}
