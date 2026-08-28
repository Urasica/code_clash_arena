package com.battle.code.execution;

public record EngineExecutionSnapshot(
        String executionImage,
        EngineExecutionMetadata metadata
) {
    public EngineExecutionSnapshot {
        if (executionImage == null || executionImage.isBlank()) {
            throw new IllegalArgumentException("Engine execution image is required.");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("Engine execution metadata is required.");
        }
    }
}
