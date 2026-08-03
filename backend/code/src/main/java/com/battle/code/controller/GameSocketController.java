package com.battle.code.controller;

import com.battle.code.service.GameSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameSocketController {

    private final GameSessionService gameSessionService;

    // 게임입장 처리
    @MessageMapping("/game/join")
    public void joinGame(Map<String, Object> payload, SimpMessageHeaderAccessor accessor, Principal principal) {
        String matchId = (String) payload.get("matchId");
        String sessionId = accessor.getSessionId();

        if (matchId != null && sessionId != null && principal != null) {
            log.info("User {} joined Match {} (Session: {})", principal.getName(), matchId, sessionId);
            gameSessionService.registerGameSession(matchId, sessionId, principal.getName());
        }
    }

    /**
     * 유저의 PvP 코드 제출
     * 발행 주소: /app/game/submit
     */
    @MessageMapping("/game/submit")
    public void submitCode(Map<String, Object> payload, Principal principal) {
        try {
            if (payload == null) return;
            String matchId = (String) payload.get("matchId");
            String code = (String) payload.get("code");
            String language = (String) payload.getOrDefault("language", "python");

            if (matchId == null || principal == null || code == null) return;
            Long userId = Long.parseLong(principal.getName());

            log.info("[PvP] Code Submitted - Match: {}, User: {}", matchId, userId);
            gameSessionService.handleCodeSubmission(matchId, userId, code, language);
        } catch (Exception e) {
            log.error("Error in submitCode", e);
        }
    }
}
