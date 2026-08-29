package com.battle.code.dto;

import com.battle.code.domain.MatchOutcome;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MatchListDto {
    private Long matchId;
    private String matchUuid;
    private String gameType;
    private MatchOutcome result;
    private Integer score;
    private LocalDateTime playedAt;
}
