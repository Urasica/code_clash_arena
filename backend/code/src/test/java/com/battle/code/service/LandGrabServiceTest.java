package com.battle.code.service;

import com.battle.code.execution.DockerMatchExecutor;
import com.battle.code.execution.MatchWorkspaceManager;
import com.battle.code.execution.WorkspaceLeaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LandGrabServiceTest {

    private final LandGrabService service = new LandGrabService(
            new CodeTemplateManager(),
            new ObjectMapper(),
            new DockerMatchExecutor("code-battle-engine"),
            new MatchWorkspaceManager("temp"),
            mock(WorkspaceLeaseService.class)
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

}
