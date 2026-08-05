package com.battle.code.controller;

import com.battle.code.dto.MatchQueueRequestDto;
import com.battle.code.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Log4j2 사용
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    /**
     * 유저가 매칭 대기열에 입장 요청
     * 클라이언트 발행 주소: /app/match/join
     * payload: { "gameType": "land_grab" }
     */
    @MessageMapping("/match/join")
    public void joinQueue(@Valid @Payload MatchQueueRequestDto request, Principal principal) {
        Long userId = Long.parseLong(principal.getName());

        log.info("[WebSocket] Join Request: User {} for Game {}", userId, request.gameType());
        matchingService.joinQueue(request.gameType(), userId);
    }

    /**
     * 유저가 매칭 대기열 취소 요청
     * 클라이언트 발행 주소: /app/match/cancel
     * payload: { "gameType": "land_grab" }
     */
    @MessageMapping("/match/cancel")
    public void cancelQueue(@Valid @Payload MatchQueueRequestDto request, Principal principal) {
        Long userId = Long.parseLong(principal.getName());

        log.info("[WebSocket] Cancel Request: User {} for Game {}", userId, request.gameType());
        matchingService.cancelQueue(request.gameType(), userId);
    }
}
