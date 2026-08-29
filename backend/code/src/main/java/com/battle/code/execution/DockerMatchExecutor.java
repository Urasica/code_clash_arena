package com.battle.code.execution;

import com.battle.code.observability.MatchLogContext;
import com.battle.code.observability.MatchTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DockerMatchExecutor {

    private final String engineImage;
    private final MatchTelemetry telemetry;
    private final EnginePolicyProperties policy;
    private final EngineMetadataProvider metadataProvider;

    @Autowired
    public DockerMatchExecutor(
            @Value("${cca.engine.image:code-battle-engine:latest}") String engineImage,
            MatchTelemetry telemetry,
            EnginePolicyProperties policy,
            EngineMetadataProvider metadataProvider
    ) {
        this.engineImage = engineImage;
        this.telemetry = telemetry;
        this.policy = policy;
        this.metadataProvider = metadataProvider;
    }

    public DockerMatchExecutor(String engineImage) {
        this(
                engineImage,
                MatchTelemetry.noOp(),
                new EnginePolicyProperties(),
                EngineMetadataProvider.fixed(
                        engineImage, "sha256:" + "0".repeat(64), "test-v1"
                )
        );
    }

    public DockerExecutionResult execute(
            Path matchDir,
            String gameType,
            String mode,
            boolean mountData,
            boolean mountPlayers
    ) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        String outcome = "failure";
        try (MatchLogContext.Scope ignored = MatchLogContext.open(matchDir.getFileName().toString())) {
            EngineExecutionSnapshot snapshot = metadataProvider.resolve();
            Process process = createProcess(
                    matchDir, gameType, mode, mountData, mountPlayers, snapshot.executionImage()
            ).start();
            CompletableFuture<StreamCapture> stdoutFuture = captureAsync(process.getInputStream());
            CompletableFuture<StreamCapture> stderrFuture = captureAsync(process.getErrorStream());

            Duration timeout = policy.timeoutFor(mode);
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                outcome = "timeout";
                throw new IOException("Docker execution timed out after " + timeout + ".");
            }

            StreamCapture stdout = awaitCapture(stdoutFuture);
            StreamCapture stderr = awaitCapture(stderrFuture);
            if (stdout.truncated() || stderr.truncated()) {
                outcome = "output_limit";
                throw new IOException(
                        "Docker output exceeded " + policy.getMaxOutputBytes() + " bytes."
                );
            }

            if (process.exitValue() != 0) {
                outcome = "non_zero_exit";
                log.error("Docker execution failed (exit code {}). stderr: {}", process.exitValue(), stderr.text());
                throw new IOException("Docker execution failed: " + stderr.text());
            }
            outcome = "success";
            return new DockerExecutionResult(stdout.text().trim(), snapshot.metadata());
        } catch (InterruptedException exception) {
            outcome = "interrupted";
            throw exception;
        } finally {
            telemetry.engineExecution(
                    gameType,
                    mode,
                    outcome,
                    Duration.ofNanos(Math.max(0, System.nanoTime() - startedAt))
            );
        }
    }

    ProcessBuilder createProcess(
            Path matchDir,
            String gameType,
            String mode,
            boolean mountData,
            boolean mountPlayers
    ) {
        return createProcess(matchDir, gameType, mode, mountData, mountPlayers, engineImage);
    }

    ProcessBuilder createProcess(
            Path matchDir,
            String gameType,
            String mode,
            boolean mountData,
            boolean mountPlayers,
            String executionImage
    ) {
        String hostPath = matchDir.toString().replace("\\", "/");
        List<String> command = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--cpus", policy.getCpus().stripTrailingZeros().toPlainString(),
                "--memory", policy.getMemory(),
                "--pids-limit", String.valueOf(policy.getPidsLimit()),
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=" + policy.getTempSize(),
                "--tmpfs", "/run/players:rw,exec,nosuid,nodev,size=" + policy.getPlayersSize(),
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
                executionImage,
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
                    int remaining = policy.getMaxOutputBytes() - output.size();
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
