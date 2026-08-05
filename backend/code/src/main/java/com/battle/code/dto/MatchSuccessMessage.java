package com.battle.code.dto;

public record MatchSuccessMessage(
        String matchId,
        String p1Id,
        String p2Id,
        LandGrabMapDto mapData,
        String myRole
) {
}
