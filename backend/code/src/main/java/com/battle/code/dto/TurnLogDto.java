package com.battle.code.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TurnLogDto(
        Integer turn,
        PlayerTurnDto p1,
        PlayerTurnDto p2,
        List<List<Integer>> coins,
        List<List<Integer>> walls,
        List<List<Integer>> board,
        Map<String, Integer> scores,
        @JsonProperty("board_size") Integer boardSize,
        @JsonProperty("system_error") String systemError
) {
}
