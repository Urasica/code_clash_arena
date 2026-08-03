package com.battle.code.service;

import com.battle.code.dto.MatchExecutionResultDto;

public record MatchRunOutcome(
        MatchExecutionResultDto result,
        String mapDataJson
) {
}
