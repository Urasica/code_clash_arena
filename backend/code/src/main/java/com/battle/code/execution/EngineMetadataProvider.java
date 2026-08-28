package com.battle.code.execution;

import java.io.IOException;

@FunctionalInterface
public interface EngineMetadataProvider {

    EngineExecutionSnapshot resolve() throws IOException, InterruptedException;

    default EngineExecutionMetadata currentMetadata() {
        try {
            return resolve().metadata();
        } catch (IOException exception) {
            throw new IllegalStateException("Engine metadata is not available.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Engine metadata inspection was interrupted.", exception);
        }
    }

    static EngineMetadataProvider fixed(String executionImage, String digest, String policyVersion) {
        EngineExecutionSnapshot snapshot = new EngineExecutionSnapshot(
                executionImage,
                new EngineExecutionMetadata(digest, policyVersion)
        );
        return () -> snapshot;
    }
}
