package com.battle.code.execution;

import java.io.IOException;

@FunctionalInterface
public interface EngineImageIdentityResolver {
    String resolveDigest(String image) throws IOException, InterruptedException;
}
