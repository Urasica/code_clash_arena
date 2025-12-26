package com.battle.code.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MatchListDto {
    private Long matchId;
    private String matchUuid;
    private String gameType;
    private String result;     // WIN, LOSE, DRAW
    private Integer score;
    private LocalDateTime playedAt;
}