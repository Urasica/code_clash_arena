package com.battle.code.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DockerMatchExecutor {

    private static final int MAX_OUTPUT_BYTES = 8 * 1024 * 1024;

    private final String engineImage;

    public DockerMatchExecutor(@Value("${cca.engine.image:code-battle-engine}") String engineImage) {
        this.engineImage = engineImage;
    }

    public String execute(
            Path matchDir,
            String gameType,
            String mode,
            boolean mountData,
            boolean mountPlayers,
            int timeoutSeconds
    ) throws IOException, InterruptedException {
        Process process = createProcess(matchDir, gameType, mode, mountData, mountPlayers).start();
        CompletableFuture<StreamCapture> stdoutFuture = captureAsync(process.getInputStream());
        CompletableFuture<StreamCapture> stderrFuture = captureAsync(process.getErrorStream());

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            throw new IOException("Docker execution timed out after " + timeoutSeconds + " seconds.");
        }

        StreamCapture stdout = awaitCapture(stdoutFuture);
        StreamCapture stderr = awaitCapture(stderrFuture);
        if (stdout.truncated() || stderr.truncated()) {
            throw new IOException("Docker output exceeded " + MAX_OUTPUT_BYTES + " bytes.");
        }

        if (process.exitValue() != 0) {
            log.error("Docker execution failed (exit code {}). stderr: {}", process.exitValue(), stderr.text());
            throw new IOException("Docker execution failed: " + stderr.text());
        }
        return stdout.text().trim();
    }

    ProcessBuilder createProcess(
            Path matchDir,
            String gameType,
            String mode,
            boolean mountData,
            boolean mountPlayers
    ) {
        String hostPath = matchDir.toString().replace("\\", "/");
        List<String> command = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--cpus", "0.5",
                "--memory", "512m",
                "--pids-limit", "128",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
                "--tmpfs", "/run/players:rw,exec,nosuid,nodev,size=128m",
                "--cap-drop", "ALL",
                "--cap-add", "CHOWN",
                "--cap-add", "DAC_READ_SEARCH",
                "--cap-add", "KILL",
                "--cap-add", "SETUID",
                "--cap-add", "SETGID",
                "--security-opt", "no-new-privileges"
        ));
        if (mountData) {
            command.addAll(List.of("-v", hostPath + ":/app/data"));
        }
        if (mountPlayers) {
            command.addAll(List.of("-v", hostPath + ":/app/players"));
        }
        command.addAll(List.of(
                engineImage,
                "python3", "referee.py", gameType, mode
        ));
        return new ProcessBuilder(command);
    }

    private CompletableFuture<StreamCapture> captureAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] buffer = new byte[8192];
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                boolean truncated = false;
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    int remaining = MAX_OUTPUT_BYTES - output.size();
                    if (remaining > 0) {
                        output.write(buffer, 0, Math.min(read, remaining));
                    }
                    if (read > remaining) {
                        truncated = true;
                    }
                }
                return new StreamCapture(output.toString(StandardCharsets.UTF_8), truncated);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    private StreamCapture awaitCapture(CompletableFuture<StreamCapture> future) throws IOException {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to capture Docker output.", cause);
        }
    }

    private record StreamCapture(String text, boolean truncated) {}
}
