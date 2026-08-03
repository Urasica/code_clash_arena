package com.battle.code.dto;

import java.util.List;

public record StartMatchResponseDto(
        String matchId,
        List<List<Integer>> walls,
        List<List<Integer>> coins
) {
    public LandGrabMapDto map() {
        return new LandGrabMapDto(walls, coins);
    }
}
