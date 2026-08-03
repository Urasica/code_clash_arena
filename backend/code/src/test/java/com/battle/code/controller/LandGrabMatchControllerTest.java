package com.battle.code.controller;

import com.battle.code.exception.GlobalExceptionHandler;
import com.battle.code.service.LandGrabService;
import com.battle.code.service.MatchService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import java.io.IOException;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LandGrabMatchControllerTest {

    private LandGrabService landGrabService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        landGrabService = mock(LandGrabService.class);
        MatchService matchService = mock(MatchService.class);
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
}
