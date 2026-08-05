package com.battle.code.controller;

import com.battle.code.dto.GameJoinRequestDto;
import com.battle.code.dto.GameSubmissionRequestDto;
import com.battle.code.dto.MatchQueueRequestDto;
import com.battle.code.service.GameSessionService;
import com.battle.code.service.MatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeControllerTest {

    private static final String MATCH_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final Principal USER_7 = () -> "7";

    @Test
    void matchmakingUsesTheAuthenticatedPrincipal() {
        MatchingService service = mock(MatchingService.class);
        MatchingController controller = new MatchingController(service);

        controller.joinQueue(new MatchQueueRequestDto("land_grab"), USER_7);

        verify(service).joinQueue("land_grab", 7L);
    }

    @Test
    void gameJoinRegistersTheAuthenticatedSession() {
        GameSessionService service = mock(GameSessionService.class);
        GameSocketController controller = new GameSocketController(service);
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId("session-1");

        controller.joinGame(new GameJoinRequestDto(MATCH_ID), accessor, USER_7);

        verify(service).registerGameSession(MATCH_ID, "session-1", "7");
    }

    @Test
    void gameSubmissionUsesTheAuthenticatedPrincipal() {
        GameSessionService service = mock(GameSessionService.class);
        GameSocketController controller = new GameSocketController(service);

        controller.submitCode(
                new GameSubmissionRequestDto(MATCH_ID, "strategy", "python"),
                USER_7
        );

        verify(service).handleCodeSubmission(MATCH_ID, 7L, "strategy", "python");
    }
}
