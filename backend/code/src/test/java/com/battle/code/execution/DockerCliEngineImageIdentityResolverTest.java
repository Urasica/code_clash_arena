package com.battle.code.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerCliEngineImageIdentityResolverTest {

    @Test
    void acceptsImageIdsAndRepositoryDigestsOnly() {
        String hash = "a".repeat(64);

        assertThat(DockerCliEngineImageIdentityResolver.isDigest("sha256:" + hash)).isTrue();
        assertThat(DockerCliEngineImageIdentityResolver.isDigest(
                "registry.example/engine@sha256:" + hash
        )).isTrue();
        assertThat(DockerCliEngineImageIdentityResolver.isDigest("engine:latest")).isFalse();
        assertThat(DockerCliEngineImageIdentityResolver.isDigest("sha256:short")).isFalse();
        assertThat(DockerCliEngineImageIdentityResolver.isDigest("sha256:" + hash + " extra"))
                .isFalse();
    }

    @Test
    void executionMetadataRejectsMutableImageTags() {
        assertThatThrownBy(() -> new EngineExecutionMetadata("engine:latest", "policy-v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
