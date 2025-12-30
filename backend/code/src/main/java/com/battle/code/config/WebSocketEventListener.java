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

        // 유저 ID 조회
        String userId = (String) redisTemplate.opsForValue().get("websocket_session:" + sessionId);

        if (userId != null) {
            log.info("[WebSocket Event] Disconnected: User {} (Session: {})", userId, sessionId);

            //대기열 제거
            try {
                matchingService.cancelQueue("land_grab", Long.parseLong(userId));
            } catch (Exception e) {
                // 무시
            }

            // 세션 ID로 조회
            String matchId = (String) redisTemplate.opsForValue().get("socket_game:" + sessionId);

            if (matchId != null) {
                log.info("Active Game Session Disconnected! Match: {}, User: {}", matchId, userId);
                gameSessionService.handleDisconnection(matchId, userId);

                // 매핑 삭제
                redisTemplate.delete("socket_game:" + sessionId);
            }

            // 세션 정보 정리
            redisTemplate.delete("websocket_session:" + sessionId);
        }
    }
}