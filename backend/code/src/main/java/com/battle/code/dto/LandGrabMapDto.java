package com.battle.code.dto;

import java.util.List;

public record LandGrabMapDto(
        List<List<Integer>> walls,
        List<List<Integer>> coins
) {
}
