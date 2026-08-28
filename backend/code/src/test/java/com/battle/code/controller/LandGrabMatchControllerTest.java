package com.battle.code.controller;

import com.battle.code.exception.GlobalExceptionHandler;
import com.battle.code.domain.MatchExecutionResult;
import com.battle.code.domain.MatchResultReason;
import com.battle.code.domain.MatchWinner;
import com.battle.code.service.LandGrabService;
import com.battle.code.service.MatchRunOutcome;
import com.battle.code.service.MatchService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LandGrabMatchControllerTest {

    private LandGrabService landGrabService;
    private MatchService matchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        landGrabService = mock(LandGrabService.class);
        matchService = mock(MatchService.class);
        LandGrabMatchController controller = new LandGrabMatchController(landGrabService, matchService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new SpringValidatorAdapter(
                        Validation.buildDefaultValidatorFactory().getValidator()
                ))
                .build();
    }

    @Test
    void invalidCompileRequestUsesTheStandardValidationError() throws Exception {
        mockMvc.perform(post("/api/match/land-grab/compile")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "matchId": "../outside",
                                  "userCode": "",
                                  "language": "ruby"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(landGrabService);
    }

    @Test
    void malformedJsonUsesTheStandardMalformedRequestError() throws Exception {
        mockMvc.perform(post("/api/match/land-grab/compile")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"));
    }

    @Test
    void executionDetailsAreNotExposedToTheClient() throws Exception {
        when(landGrabService.compileCode(anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new IOException("sensitive compiler filesystem details"));

        mockMvc.perform(post("/api/match/land-grab/compile")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "matchId": "123e4567-e89b-42d3-a456-426614174000",
                                  "userCode": "def strategy(*args): return 'STAY'",
                                  "language": "python"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EXECUTION_ERROR"))
                .andExpect(content().string(not(containsString("sensitive compiler filesystem details"))));
    }

    @Test
    void runPersistsTheMapSnapshotReturnedWithTheEngineResult() throws Exception {
        String matchId = "123e4567-e89b-42d3-a456-426614174000";
        String mapData = "{\"walls\":[],\"coins\":[]}";
        MatchExecutionResult result = new MatchExecutionResult(
                MatchWinner.P1, MatchResultReason.SCORE, null,
                Map.of("p1", 1, "p2", 0), 1, List.of(), null, null
        );
        when(landGrabService.runMatch(matchId, 7L, "code", "python", "easy"))
                .thenReturn(new MatchRunOutcome(result, mapData));

        mockMvc.perform(post("/api/match/land-grab/run")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "matchId": "123e4567-e89b-42d3-a456-426614174000",
                                  "userCode": "code",
                                  "language": "python",
                                  "difficulty": "easy"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winner").value("p1"));

        verify(matchService).saveMatchResult(
                7L, matchId, result, "code", "python", "easy", mapData
        );
    }
}
