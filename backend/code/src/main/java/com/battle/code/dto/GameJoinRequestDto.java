package com.battle.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GameJoinRequestDto(
        @NotBlank
        @Pattern(regexp = RequestConstraints.MATCH_ID_PATTERN)
        String matchId
) {
}
