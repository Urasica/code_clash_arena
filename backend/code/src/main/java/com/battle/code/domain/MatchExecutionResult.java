package com.battle.code.domain;

import com.battle.code.dto.TurnLogDto;

import java.util.List;
import java.util.Map;

public record MatchExecutionResult(
        MatchWinner winner,
        MatchResultReason reason,
        String systemError,
        Map<String, Integer> finalScores,
        Integer totalTurns,
        List<TurnLogDto> logs,
        String p1Error,
        String p2Error
) {
    public MatchExecutionResult {
        if (reason == null) {
            throw new IllegalArgumentException("Match result reason is required.");
        }
        finalScores = finalScores == null ? Map.of() : Map.copyOf(finalScores);
        logs = logs == null ? null : List.copyOf(logs);
    }

    public static MatchExecutionResult disconnected(MatchWinner winner) {
        if (winner == null || winner == MatchWinner.DRAW) {
            throw new IllegalArgumentException("A disconnected match requires a winning player.");
        }
        return new MatchExecutionResult(
                winner,
                MatchResultReason.OPPONENT_DISCONNECTED,
                null,
                Map.of("p1", 0, "p2", 0),
                null,
                null,
                null,
                null
        );
    }

    public boolean hasSystemError() {
        return systemError != null && !systemError.isBlank();
    }

    public int scoreFor(String playerIndex) {
        return finalScores.getOrDefault(playerIndex, 0);
    }

    public MatchOutcome outcomeFor(String playerIndex) {
        if (!"p1".equals(playerIndex) && !"p2".equals(playerIndex)) {
            throw new IllegalArgumentException("Unsupported player index: " + playerIndex);
        }
        if (("p1".equals(playerIndex) && p1Error != null)
                || ("p2".equals(playerIndex) && p2Error != null)) {
            return MatchOutcome.LOSE;
        }
        if (winner == MatchWinner.DRAW) {
            return MatchOutcome.DRAW;
        }
        return winner != null && winner.wireValue().equals(playerIndex)
                ? MatchOutcome.WIN
                : MatchOutcome.LOSE;
    }
}
