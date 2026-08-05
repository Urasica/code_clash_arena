package com.battle.code.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchExecutionResultDto(
        String type,
        String winner,
        String reason,
        String error,
        @JsonProperty("final_scores") Map<String, Integer> finalScores,
        @JsonProperty("total_turns") Integer totalTurns,
        List<TurnLogDto> logs,
        @JsonProperty("p1_error") String p1Error,
        @JsonProperty("p2_error") String p2Error
) {
    public MatchExecutionResultDto asRealtimeResult() {
        return new MatchExecutionResultDto(
                "RESULT", winner, reason, error, finalScores, totalTurns, logs, p1Error, p2Error
        );
    }

    public static MatchExecutionResultDto disconnected(String winner) {
        return new MatchExecutionResultDto(
                "RESULT", winner, "OPPONENT_DISCONNECTED", null,
                Map.of("p1", 0, "p2", 0), null, null, null, null
        );
    }
}
