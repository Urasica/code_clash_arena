package com.battle.code.controller;

import com.battle.code.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Log4j2 사용
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    /**
     * 유저가 매칭 대기열에 입장 요청
     * 클라이언트 발행 주소: /app/match/join
     * payload: { "userId": 101, "gameType": "land_grab" }
     */
    @MessageMapping("/match/join")
    public void joinQueue(Map<String, Object> payload) {
        Object userIdObj = payload.get("userId");
        if (!(userIdObj instanceof Number)) {
            log.warn("Invalid join request: {}", payload);
            return;
        }

        Long userId = ((Number) userIdObj).longValue();
        String gameType = (String) payload.getOrDefault("gameType", "land_grab"); // 기본값

        log.info("[WebSocket] Join Request: User {} for Game {}", userId, gameType);
        matchingService.joinQueue(gameType, userId);
    }

    /**
     * 유저가 매칭 대기열 취소 요청
     * 클라이언트 발행 주소: /app/match/cancel
     * payload: { "userId": 101, "gameType": "land_grab" }
     */
    @MessageMapping("/match/cancel")
    public void cancelQueue(Map<String, Object> payload) {
        Object userIdObj = payload.get("userId");
        if (!(userIdObj instanceof Number)) {
            log.warn("Invalid cancel request: {}", payload);
            return;
        }

        Long userId = ((Number) userIdObj).longValue();
        String gameType = (String) payload.getOrDefault("gameType", "land_grab");

        log.info("[WebSocket] Cancel Request: User {} for Game {}", userId, gameType);
        matchingService.cancelQueue(gameType, userId);
    }
}