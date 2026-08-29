package com.battle.code.service;

import com.battle.code.domain.MatchExecutionResult;
import com.battle.code.domain.MatchResultReason;
import com.battle.code.domain.MatchWinner;
import com.battle.code.dto.MatchExecutionResultDto;

public final class MatchExecutionResultMapper {

    private MatchExecutionResultMapper() {
    }

    public static MatchExecutionResult fromEngine(MatchExecutionResultDto dto) {
        MatchWinner winner = MatchWinner.fromWireValue(dto.winner());
        return new MatchExecutionResult(
                winner,
                classifyReason(dto, winner),
                dto.error(),
                dto.finalScores(),
                dto.totalTurns(),
                dto.logs(),
                dto.p1Error(),
                dto.p2Error()
        );
    }

    public static MatchExecutionResultDto toApi(MatchExecutionResult result) {
        return toDto(result, null);
    }

    public static MatchExecutionResultDto toRealtime(MatchExecutionResult result) {
        return toDto(result, "RESULT");
    }

    private static MatchExecutionResultDto toDto(MatchExecutionResult result, String type) {
        return new MatchExecutionResultDto(
                type,
                result.winner() == null ? null : result.winner().wireValue(),
                result.reason().name(),
                result.systemError(),
                result.finalScores(),
                result.totalTurns(),
                result.logs(),
                result.p1Error(),
                result.p2Error()
        );
    }

    private static MatchResultReason classifyReason(MatchExecutionResultDto dto, MatchWinner winner) {
        if (dto.error() != null && !dto.error().isBlank()) {
            return MatchResultReason.SYSTEM_ERROR;
        }
        if (dto.reason() != null && !dto.reason().isBlank()) {
            return MatchResultReason.fromWireValue(dto.reason());
        }
        if (dto.p1Error() != null && dto.p2Error() != null) {
            return MatchResultReason.BOTH_PLAYERS_CRASH;
        }
        if (dto.p1Error() != null || dto.p2Error() != null) {
            return MatchResultReason.PLAYER_CRASH;
        }
        return winner == MatchWinner.DRAW
                ? MatchResultReason.SCORE_DRAW
                : MatchResultReason.SCORE;
    }
}
