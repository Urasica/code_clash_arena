package com.battle.code.controller;

import com.battle.code.dto.GameJoinRequestDto;
import com.battle.code.dto.GameSubmissionRequestDto;
import com.battle.code.observability.MatchLogContext;
import com.battle.code.service.GameSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameSocketController {

    private final GameSessionService gameSessionService;

    // 게임입장 처리
    @MessageMapping("/game/join")
    public void joinGame(
            @Valid @Payload GameJoinRequestDto request,
            SimpMessageHeaderAccessor accessor,
            Principal principal
    ) {
        String sessionId = accessor.getSessionId();

        try (MatchLogContext.Scope ignored = MatchLogContext.open(request.matchId())) {
            if (sessionId != null) {
                log.info("User {} joined Match {} (Session: {})", principal.getName(), request.matchId(), sessionId);
                gameSessionService.registerGameSession(request.matchId(), sessionId, principal.getName());
            }
        }
    }

    /**
     * 유저의 PvP 코드 제출
     * 발행 주소: /app/game/submit
     */
    @MessageMapping("/game/submit")
    public void submitCode(@Valid @Payload GameSubmissionRequestDto request, Principal principal) {
        Long userId = Long.parseLong(principal.getName());

        try (MatchLogContext.Scope ignored = MatchLogContext.open(request.matchId())) {
            log.info("[PvP] Code Submitted - Match: {}, User: {}", request.matchId(), userId);
            gameSessionService.handleCodeSubmission(
                    request.matchId(),
                    userId,
                    request.code(),
                    request.language()
            );
        }
    }
}
