package com.battle.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MatchQueueRequestDto(
        @NotBlank
        @Pattern(regexp = RequestConstraints.GAME_TYPE_PATTERN)
        String gameType
) {
}
