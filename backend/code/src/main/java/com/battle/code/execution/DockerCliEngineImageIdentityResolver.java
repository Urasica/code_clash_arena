package com.battle.code.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class DockerCliEngineImageIdentityResolver implements EngineImageIdentityResolver {

    private static final int MAX_DIGEST_OUTPUT_BYTES = 4096;
    private static final String DIGEST_FORMAT =
            "{{if .RepoDigests}}{{index .RepoDigests 0}}{{else}}{{.Id}}{{end}}";

    private final Duration timeout;

    public DockerCliEngineImageIdentityResolver(
            @Value("${cca.engine.readiness-timeout:3s}") Duration timeout
    ) {
        this.timeout = timeout;
    }

    @Override
    public String resolveDigest(String image) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "docker", "inspect", "--type", "image", "--format", DIGEST_FORMAT, image
        ).start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Docker image digest inspection timed out.");
            }
            byte[] stdout = process.getInputStream().readNBytes(MAX_DIGEST_OUTPUT_BYTES + 1);
            byte[] stderr = process.getErrorStream().readNBytes(MAX_DIGEST_OUTPUT_BYTES + 1);
            if (stdout.length > MAX_DIGEST_OUTPUT_BYTES || stderr.length > MAX_DIGEST_OUTPUT_BYTES) {
                throw new IOException("Docker image inspection output exceeded the limit.");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Engine image digest is not available.");
            }
            String digest = new String(stdout, StandardCharsets.UTF_8).trim();
            if (!isDigest(digest)) {
                throw new IOException("Docker returned an invalid engine image digest.");
            }
            return digest;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    static boolean isDigest(String value) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        int separator = value.lastIndexOf("sha256:");
        if (separator < 0) {
            return false;
        }
        String hash = value.substring(separator + "sha256:".length());
        return hash.length() == 64 && hash.chars().allMatch(character ->
                (character >= '0' && character <= '9')
                        || (character >= 'a' && character <= 'f')
        );
    }
}
