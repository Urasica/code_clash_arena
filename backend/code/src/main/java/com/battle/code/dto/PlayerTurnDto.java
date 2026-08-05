package com.battle.code.dto;

import java.util.List;

public record PlayerTurnDto(String act, List<Integer> pos, Boolean alive) {
}
