package com.battle.code.service;

import com.battle.code.domain.MatchExecutionResult;

public record MatchRunOutcome(
        MatchExecutionResult result,
        String mapDataJson
) {
}
