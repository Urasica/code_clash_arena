package com.battle.code.execution;

public record DockerExecutionResult(
        String output,
        EngineExecutionMetadata metadata
) {
}
