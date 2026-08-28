package com.battle.code.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DockerMatchExecutorTest {

    private final EnginePolicyProperties policy = policy();
    private final DockerMatchExecutor executor = new DockerMatchExecutor(
            "custom-engine",
            com.battle.code.observability.MatchTelemetry.noOp(),
            policy,
            EngineMetadataProvider.fixed("resolved-engine", "sha256:" + "a".repeat(64), "policy-7")
    );

    @TempDir
    Path tempDir;

    @Test
    void commandAppliesIsolationLimitsAndExpectedMounts() {
        List<String> command = executor
                .createProcess(tempDir, "land_grab", "run", true, true)
                .command();

        assertThat(command).containsSubsequence("--network", "none");
        assertThat(command).containsSubsequence("--cpus", "0.75");
        assertThat(command).containsSubsequence("--memory", "768m");
        assertThat(command).containsSubsequence("--pids-limit", "96");
        assertThat(command).contains("--read-only", "ALL", "no-new-privileges");
        assertThat(command).contains("/tmp:rw,noexec,nosuid,size=32m");
        assertThat(command).contains("/run/players:rw,exec,nosuid,nodev,size=96m");
        assertThat(command).containsSubsequence("--cap-add", "CHOWN");
        assertThat(command).containsSubsequence("--cap-add", "DAC_READ_SEARCH");
        assertThat(command).containsSubsequence("--cap-add", "KILL");
        assertThat(command).containsSubsequence("--cap-add", "SETUID");
        assertThat(command).containsSubsequence("--cap-add", "SETGID");
        assertThat(command).anyMatch(value -> value.endsWith(":/app/data"));
        assertThat(command).anyMatch(value -> value.endsWith(":/app/players"));
        assertThat(command).endsWith("custom-engine", "python3", "referee.py", "land_grab", "run");
    }

    @Test
    void resolvedDigestIsTheImagePassedToDocker() {
        List<String> command = executor
                .createProcess(tempDir, "land_grab", "run", true, true, "sha256:" + "b".repeat(64))
                .command();

        assertThat(command).endsWith(
                "sha256:" + "b".repeat(64),
                "python3", "referee.py", "land_grab", "run"
        );
    }

    private EnginePolicyProperties policy() {
        EnginePolicyProperties properties = new EnginePolicyProperties();
        properties.setCpus(new BigDecimal("0.75"));
        properties.setMemory("768m");
        properties.setPidsLimit(96);
        properties.setTempSize("32m");
        properties.setPlayersSize("96m");
        return properties;
    }
}
