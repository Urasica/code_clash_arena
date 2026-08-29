package com.battle.code.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerEngineMetadataProviderTest {

    @Test
    void configuredImageResolvesToAnImmutableExecutionReferenceAndPolicyVersion() throws Exception {
        String digest = "registry.example/engine@sha256:" + "a".repeat(64);
        EnginePolicyProperties policy = new EnginePolicyProperties();
        policy.setVersion("policy-2026-08");
        EngineImageIdentityResolver resolver = image -> {
            assertThat(image).isEqualTo("engine:latest");
            return digest;
        };

        EngineExecutionSnapshot snapshot = new DockerEngineMetadataProvider(
                "engine:latest", resolver, policy
        ).resolve();

        assertThat(snapshot.executionImage()).isEqualTo(digest);
        assertThat(snapshot.metadata().engineDigest()).isEqualTo(digest);
        assertThat(snapshot.metadata().policyVersion()).isEqualTo("policy-2026-08");
    }
}
