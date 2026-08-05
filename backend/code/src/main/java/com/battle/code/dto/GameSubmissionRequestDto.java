package com.battle.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GameSubmissionRequestDto(
        @NotBlank
        @Pattern(regexp = RequestConstraints.MATCH_ID_PATTERN)
        String matchId,
        @NotBlank
        @Size(max = RequestConstraints.MAX_CODE_LENGTH)
        String code,
        @NotBlank
        @Pattern(regexp = RequestConstraints.LANGUAGE_PATTERN)
        String language
) {
}
