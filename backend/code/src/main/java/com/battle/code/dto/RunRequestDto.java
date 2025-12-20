package com.battle.code.dto;

import lombok.Data;

@Data
public class RunRequestDto {
    private String matchId;
    private String userCode;

    private String language;   // "python", "java", "cpp", "javascript"
    private String difficulty; // "easy", "normal", "hard"
}