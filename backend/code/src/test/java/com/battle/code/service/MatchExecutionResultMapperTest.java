package com.battle.code.service;

import com.battle.code.domain.MatchExecutionResult;
import com.battle.code.domain.MatchOutcome;
import com.battle.code.domain.MatchResultReason;
import com.battle.code.domain.MatchWinner;
import com.battle.code.dto.MatchExecutionResultDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchExecutionResultMapperTest {

    @Test
    void scoreWinHasTheSameApiReasonAndPlayerOutcomes() {
        MatchExecutionResult result = fromEngine("p1", null, null, null, null);

        assertThat(result.reason()).isEqualTo(MatchResultReason.SCORE);
        assertThat(result.outcomeFor("p1")).isEqualTo(MatchOutcome.WIN);
        assertThat(result.outcomeFor("p2")).isEqualTo(MatchOutcome.LOSE);
        assertThat(MatchExecutionResultMapper.toApi(result).reason()).isEqualTo("SCORE");
    }

    @Test
    void scoreDrawMapsBothPlayersToDraw() {
        MatchExecutionResult result = fromEngine("draw", null, null, null, null);

        assertThat(result.reason()).isEqualTo(MatchResultReason.SCORE_DRAW);
        assertThat(result.outcomeFor("p1")).isEqualTo(MatchOutcome.DRAW);
        assertThat(result.outcomeFor("p2")).isEqualTo(MatchOutcome.DRAW);
    }

    @Test
    void aPlayerCrashOverridesTheScoreOutcome() {
        MatchExecutionResult result = fromEngine("p2", null, null, "timed out", null);

        assertThat(result.reason()).isEqualTo(MatchResultReason.PLAYER_CRASH);
        assertThat(result.outcomeFor("p1")).isEqualTo(MatchOutcome.LOSE);
        assertThat(result.outcomeFor("p2")).isEqualTo(MatchOutcome.WIN);
    }

    @Test
    void twoPlayerCrashesPersistAsTwoLosses() {
        MatchExecutionResult result = fromEngine("draw", null, null, "p1 failed", "p2 failed");

        assertThat(result.reason()).isEqualTo(MatchResultReason.BOTH_PLAYERS_CRASH);
        assertThat(result.outcomeFor("p1")).isEqualTo(MatchOutcome.LOSE);
        assertThat(result.outcomeFor("p2")).isEqualTo(MatchOutcome.LOSE);
    }

    @Test
    void disconnectUsesTheSameTypedReasonForRealtimeAndPersistence() {
        MatchExecutionResult result = MatchExecutionResult.disconnected(MatchWinner.P2);

        assertThat(result.reason()).isEqualTo(MatchResultReason.OPPONENT_DISCONNECTED);
        assertThat(result.outcomeFor("p1")).isEqualTo(MatchOutcome.LOSE);
        assertThat(result.outcomeFor("p2")).isEqualTo(MatchOutcome.WIN);
        assertThat(MatchExecutionResultMapper.toRealtime(result).reason())
                .isEqualTo("OPPONENT_DISCONNECTED");
    }

    @Test
    void engineSystemErrorIsTypedAndCannotBeMistakenForAPlayedMatch() {
        MatchExecutionResult result = fromEngine(null, null, "engine failed", null, null);

        assertThat(result.reason()).isEqualTo(MatchResultReason.SYSTEM_ERROR);
        assertThat(result.hasSystemError()).isTrue();
    }

    private MatchExecutionResult fromEngine(
            String winner,
            String reason,
            String error,
            String p1Error,
            String p2Error
    ) {
        return MatchExecutionResultMapper.fromEngine(new MatchExecutionResultDto(
                null,
                winner,
                reason,
                error,
                Map.of("p1", 3, "p2", 1),
                1,
                null,
                p1Error,
                p2Error
        ));
    }
}
