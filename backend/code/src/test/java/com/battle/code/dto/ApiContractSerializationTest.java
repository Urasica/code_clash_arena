package com.battle.code.dto;

import com.battle.code.exception.StompExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void engineResultUsesStableSnakeCaseFields() throws Exception {
        MatchExecutionResultDto result = new MatchExecutionResultDto(
                "RESULT", "p1", null, null, Map.of("p1", 3, "p2", 1), 10,
                List.of(new TurnLogDto(0, null, null, List.of(), List.of(), List.of(),
                        Map.of("p1", 0, "p2", 0), 20, null)),
                null, "compile failed"
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(result));

        assertThat(json.get("type").asText()).isEqualTo("RESULT");
        assertThat(json.get("final_scores").get("p1").asInt()).isEqualTo(3);
        assertThat(json.get("total_turns").asInt()).isEqualTo(10);
        assertThat(json.get("p2_error").asText()).isEqualTo("compile failed");
        assertThat(json.get("logs").get(0).get("board_size").asInt()).isEqualTo(20);
    }

    @Test
    void engineJsonDeserializesIntoThePublishedContract() throws Exception {
        MatchExecutionResultDto result = objectMapper.readValue("""
                {
                  "winner": "draw",
                  "final_scores": {"p1": 2, "p2": 2},
                  "total_turns": 1,
                  "logs": [{"turn": 0, "board_size": 20, "unknown": true}],
                  "p1_error": null,
                  "p2_error": null
                }
                """, MatchExecutionResultDto.class);

        assertThat(result.winner()).isEqualTo("draw");
        assertThat(result.finalScores()).containsEntry("p2", 2);
        assertThat(result.logs().get(0).boardSize()).isEqualTo(20);
        assertThat(result.asRealtimeResult().type()).isEqualTo("RESULT");
    }

    @Test
    void realtimeMessagesHaveStableDiscriminatorsAndPublicErrors() throws Exception {
        JsonNode notification = objectMapper.readTree(objectMapper.writeValueAsBytes(
                GameNotificationMessage.playerSubmitted("p1")
        ));
        JsonNode error = objectMapper.readTree(objectMapper.writeValueAsBytes(
                GameErrorMessage.executionFailed()
        ));

        assertThat(notification.get("type").asText()).isEqualTo("NOTIFICATION");
        assertThat(notification.get("message").asText()).isEqualTo("PLAYER_SUBMITTED");
        assertThat(error.get("type").asText()).isEqualTo("ERROR");
        assertThat(error.get("code").asText()).isEqualTo("EXECUTION_ERROR");
        assertThat(error.get("message").asText()).doesNotContain("filesystem");
    }

    @Test
    void stompValidationHandlerTargetsTheUserErrorQueue() throws Exception {
        var method = StompExceptionHandler.class.getDeclaredMethod(
                "handleValidation",
                org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class
        );
        var destination = method.getAnnotation(org.springframework.messaging.simp.annotation.SendToUser.class);

        assertThat(destination).isNotNull();
        assertThat(destination.value()).containsExactly("/queue/errors");
    }
}
