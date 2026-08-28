package com.battle.code.domain;

import java.util.Locale;

public enum MatchResultReason {
    SCORE,
    SCORE_DRAW,
    PLAYER_CRASH,
    BOTH_PLAYERS_CRASH,
    OPPONENT_DISCONNECTED,
    SYSTEM_ERROR,
    LEGACY;

    public static MatchResultReason fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Match result reason is required.");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
