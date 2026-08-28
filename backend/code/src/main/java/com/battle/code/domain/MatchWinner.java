package com.battle.code.domain;

import java.util.Locale;

public enum MatchWinner {
    P1("p1"),
    P2("p2"),
    DRAW("draw");

    private final String wireValue;

    MatchWinner(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static MatchWinner fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "p1" -> P1;
            case "p2" -> P2;
            case "draw" -> DRAW;
            default -> throw new IllegalArgumentException("Unsupported match winner: " + value);
        };
    }
}
