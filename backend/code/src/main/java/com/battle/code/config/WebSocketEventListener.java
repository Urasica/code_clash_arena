package com.battle.code.config;

import com.battle.code.service.GameSessionService;
import com.battle.code.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MatchingService matchingService;
    private final GameSessionService gameSessionService;

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String websocketKey = "websocket_session:" + sessionId;
        Object storedUserId = redisTemplate.opsForValue().get(websocketKey);
        if (storedUserId == null) {
            return;
        }

        String userId = String.valueOf(storedUserId);
        String userSocketsKey = "user_sockets:" + userId;
        redisTemplate.opsForSet().remove(userSocketsKey, sessionId);
        Long remainingUserSockets = redisTemplate.opsForSet().size(userSocketsKey);
        if (remainingUserSockets == null || remainingUserSockets == 0) {
            try {
                matchingService.cancelQueue("land_grab", Long.parseLong(userId));
            } catch (RuntimeException exception) {
                log.warn("Could not remove disconnected user {} from queue", userId, exception);
            }
            redisTemplate.delete(userSocketsKey);
        }

        Object storedMatchId = redisTemplate.opsForValue().get("socket_game:" + sessionId);
        if (storedMatchId != null) {
            gameSessionService.handleSocketDisconnection(
                    String.valueOf(storedMatchId), userId, sessionId
            );
        }
        redisTemplate.delete(websocketKey);
    }
}
