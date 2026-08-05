package com.battle.code.dto;

public record GameNotificationMessage(String type, String message, String role) {
    public static GameNotificationMessage playerSubmitted(String role) {
        return new GameNotificationMessage("NOTIFICATION", "PLAYER_SUBMITTED", role);
    }
}
