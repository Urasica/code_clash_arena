package com.battle.code.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DockerEngineMetadataProvider implements EngineMetadataProvider {

    private final String engineImage;
    private final EngineImageIdentityResolver identityResolver;
    private final EnginePolicyProperties policy;

    public DockerEngineMetadataProvider(
            @Value("${cca.engine.image:code-battle-engine:latest}") String engineImage,
            EngineImageIdentityResolver identityResolver,
            EnginePolicyProperties policy
    ) {
        this.engineImage = engineImage;
        this.identityResolver = identityResolver;
        this.policy = policy;
    }

    @Override
    public EngineExecutionSnapshot resolve() throws IOException, InterruptedException {
        String digest = identityResolver.resolveDigest(engineImage);
        return new EngineExecutionSnapshot(
                digest,
                new EngineExecutionMetadata(digest, policy.getVersion())
        );
    }
}
