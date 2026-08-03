package com.battle.code.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LandGrabServiceTest {

    private final LandGrabService service = new LandGrabService(
            new CodeTemplateManager(),
            new ObjectMapper()
    );

    @TempDir
    Path tempDir;

    @Test
    void playerCodeUsesTheDirectoryContractExpectedByTheEngine() throws Exception {
        service.savePlayerCode(
                tempDir,
                "p1",
                "c",
                "const char* strategy(Point p, Point* c, int cn, Point* w, int wn, int s) { return \"STAY\"; }"
        );

        Path source = tempDir.resolve("p1").resolve("p1.c");
        assertThat(source).exists();
        assertThat(Files.readString(source)).doesNotContain("%USER_CODE%");
    }

    @Test
    void dockerCommandAppliesIsolationLimitsAndExpectedMounts() {
        List<String> command = service
                .createDockerProcess(tempDir, "run", true, true)
                .command();

        assertThat(command).containsSubsequence("--network", "none");
        assertThat(command).containsSubsequence("--memory", "512m");
        assertThat(command).contains("--read-only", "ALL", "no-new-privileges");
        assertThat(command).anyMatch(value -> value.endsWith(":/app/data"));
        assertThat(command).anyMatch(value -> value.endsWith(":/app/players"));
        assertThat(command).endsWith("code-battle-engine", "python3", "referee.py", "land_grab", "run");
    }

    @Test
    void invalidMatchIdsAreRejectedBeforeResolvingAWorkspace() {
        assertThatThrownBy(() -> service.resolveMatchDir("../../outside"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid match ID.");
    }
}
