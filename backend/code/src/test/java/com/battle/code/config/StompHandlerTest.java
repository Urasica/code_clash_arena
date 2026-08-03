package com.battle.code.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompHandlerTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private HashOperations<String, Object, Object> hashOperations;
    private SetOperations<String, Object> setOperations;
    private StompHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        hashOperations = mock(HashOperations.class);
        setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        handler = new StompHandler(redisTemplate);
    }

    @Test
    void connectRequiresAnAuthenticatedPrincipal() {
        Message<byte[]> message = message(StompCommand.CONNECT, null, "session-1", null);

        assertThatThrownBy(() -> handler.preSend(message, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectStoresTheAuthenticatedSessionWithATtl() {
        Message<byte[]> message = message(StompCommand.CONNECT, "7", "session-1", null);

        handler.preSend(message, null);

        verify(valueOperations).set("websocket_session:session-1", "7", Duration.ofHours(2));
        verify(setOperations).add("user_sockets:7", "session-1");
        verify(redisTemplate).expire("user_sockets:7", Duration.ofHours(2));
    }

    @Test
    void aUserCannotSubscribeToAnotherUsersMatchTopic() {
        Message<byte[]> message = message(
                StompCommand.SUBSCRIBE,
                "7",
                "session-1",
                "/topic/match/8"
        );

        assertThatThrownBy(() -> handler.preSend(message, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> message(StompCommand command, String userId, String sessionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (userId != null) {
            Principal principal = () -> userId;
            accessor.setUser(principal);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
