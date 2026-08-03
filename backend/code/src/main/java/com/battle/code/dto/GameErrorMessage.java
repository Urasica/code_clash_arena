package com.battle.code.dto;

public record GameErrorMessage(String type, String code, String message) {
    public static GameErrorMessage executionFailed() {
        return new GameErrorMessage("ERROR", "EXECUTION_ERROR", "Match execution failed");
    }

    public static GameErrorMessage validation(String message) {
        return new GameErrorMessage("ERROR", "VALIDATION_ERROR", message);
    }
}
