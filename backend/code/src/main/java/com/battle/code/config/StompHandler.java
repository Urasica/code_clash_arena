package com.battle.code.config;

import com.battle.code.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public StompHandler(JwtTokenProvider jwtTokenProvider,
                        RedisTemplate<String, Object> redisTemplate) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // 연결 요청
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            // [DEBUG] 헤더 확인
            Map<String, List<String>> nativeHeaders = accessor.toNativeHeaderMap();
            log.info("🔍 [WebSocket CONNECT] Headers received: {}", nativeHeaders);

            String token = accessor.getFirstNativeHeader("Authorization");

            if (token == null) {
                token = accessor.getFirstNativeHeader("authorization");
            }

            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);

                if (jwtTokenProvider.validateToken(token)) {
                    String userId = String.valueOf(jwtTokenProvider.getUserId(token));
                    String sessionId = accessor.getSessionId();

                    // 세션-유저 매핑 저장
                    redisTemplate.opsForValue().set("websocket_session:" + sessionId, userId);
                    log.info("[WebSocket] Auth Success: User {} (Session: {})", userId, sessionId);
                } else {
                    log.error("[WebSocket] Token validation failed");
                }
            } else {
                log.warn("[WebSocket] No valid Authorization header found.");
            }
        }
        return message;
    }
}