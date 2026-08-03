package com.battle.code.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DockerMatchExecutorTest {

    private final DockerMatchExecutor executor = new DockerMatchExecutor("custom-engine");

    @TempDir
    Path tempDir;

    @Test
    void commandAppliesIsolationLimitsAndExpectedMounts() {
        List<String> command = executor
                .createProcess(tempDir, "land_grab", "run", true, true)
                .command();

        assertThat(command).containsSubsequence("--network", "none");
        assertThat(command).containsSubsequence("--memory", "512m");
        assertThat(command).contains("--read-only", "ALL", "no-new-privileges");
        assertThat(command).anyMatch(value -> value.endsWith(":/app/data"));
        assertThat(command).anyMatch(value -> value.endsWith(":/app/players"));
        assertThat(command).endsWith("custom-engine", "python3", "referee.py", "land_grab", "run");
    }
}
