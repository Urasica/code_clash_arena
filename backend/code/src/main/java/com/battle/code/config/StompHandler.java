package com.battle.code.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Duration;

@Slf4j
@Component
public class StompHandler implements ChannelInterceptor {

    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private final RedisTemplate<String, Object> redisTemplate;

    public StompHandler(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Principal principal = requirePrincipal(accessor);
            String sessionId = accessor.getSessionId();
            redisTemplate.opsForValue().set(
                    "websocket_session:" + sessionId,
                    principal.getName(),
                    SESSION_TTL
            );
            String userSocketsKey = "user_sockets:" + principal.getName();
            redisTemplate.opsForSet().add(userSocketsKey, sessionId);
            redisTemplate.expire(userSocketsKey, SESSION_TTL);
            log.info("[WebSocket] Authenticated user {} (session {})", principal.getName(), sessionId);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            requirePrincipal(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal principal = requirePrincipal(accessor);
            authorizeSubscription(principal.getName(), accessor.getDestination());
        }
        return message;
    }

    private Principal requirePrincipal(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new AccessDeniedException("Authenticated WebSocket session required");
        }
        return principal;
    }

    private void authorizeSubscription(String userId, String destination) {
        if (destination == null) {
            throw new AccessDeniedException("Subscription destination is required");
        }
        if (destination.startsWith("/topic/match/")) {
            String targetUserId = destination.substring("/topic/match/".length());
            if (!userId.equals(targetUserId)) {
                throw new AccessDeniedException("Cannot subscribe to another user's match topic");
            }
            return;
        }
        if (destination.startsWith("/topic/game/")) {
            String matchId = destination.substring("/topic/game/".length());
            String roomKey = "match_room:" + matchId;
            Object p1 = redisTemplate.opsForHash().get(roomKey, "p1");
            Object p2 = redisTemplate.opsForHash().get(roomKey, "p2");
            if (!userId.equals(String.valueOf(p1)) && !userId.equals(String.valueOf(p2))) {
                throw new AccessDeniedException("User is not a player in this match");
            }
            return;
        }
        throw new AccessDeniedException("Subscription destination is not allowed");
    }
}
