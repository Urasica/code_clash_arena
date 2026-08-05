package com.battle.code.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RunRequestDto {
    @NotBlank
    @Pattern(regexp = RequestConstraints.MATCH_ID_PATTERN)
    private String matchId;

    @NotBlank
    @Size(max = RequestConstraints.MAX_CODE_LENGTH)
    private String userCode;

    @Pattern(regexp = RequestConstraints.LANGUAGE_PATTERN)
    private String language;   // "python", "java", "cpp", "javascript"

    @Pattern(regexp = RequestConstraints.DIFFICULTY_PATTERN)
    private String difficulty; // "easy", "normal", "hard"
}
