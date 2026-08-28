package com.battle.code.execution;

public record EngineExecutionMetadata(
        String engineDigest,
        String policyVersion
) {
    public EngineExecutionMetadata {
        if (!DockerCliEngineImageIdentityResolver.isDigest(engineDigest)) {
            throw new IllegalArgumentException("A valid engine digest is required.");
        }
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("Engine policy version is required.");
        }
    }
}
