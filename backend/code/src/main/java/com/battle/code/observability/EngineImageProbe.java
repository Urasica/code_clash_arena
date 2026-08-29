package com.battle.code.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class EngineImageProbe {

    private final String engineImage;
    private final Duration timeout;

    public EngineImageProbe(
            @Value("${cca.engine.image:code-battle-engine:latest}") String engineImage,
            @Value("${cca.engine.readiness-timeout:3s}") Duration timeout
    ) {
        this.engineImage = engineImage;
        this.timeout = timeout;
    }

    public ProbeResult check() {
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "inspect", "--type", "image", engineImage)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new ProbeResult(false, engineImage, "Docker image inspection timed out");
            }
            if (process.exitValue() != 0) {
                return new ProbeResult(false, engineImage, "Engine image is not available");
            }
            return new ProbeResult(true, engineImage, "ready");
        } catch (IOException exception) {
            return new ProbeResult(false, engineImage, "Docker is not available");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProbeResult(false, engineImage, "Engine readiness check was interrupted");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    public record ProbeResult(boolean ready, String image, String reason) {
    }
}
