package com.battle.code.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchStateServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private MatchStateService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        service = new MatchStateService(redisTemplate);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void transitionSucceedsOnlyWhenRedisAtomicallyAcceptsIt() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);

        assertThat(service.transition("match-1", MatchStatus.RUNNING, MatchStatus.READY)).isTrue();
        assertThat(service.transition("match-1", MatchStatus.RUNNING, MatchStatus.READY)).isFalse();
    }
}
