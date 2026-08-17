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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void mapInitPersistsValidatedEngineOutputOnTheHost() throws Exception {
        DockerMatchExecutor executor = mock(DockerMatchExecutor.class);
        WorkspaceLeaseService leaseService = mock(WorkspaceLeaseService.class);
        LandGrabService mapService = new LandGrabService(
                new CodeTemplateManager(),
                new ObjectMapper(),
                executor,
                new MatchWorkspaceManager(tempDir.toString()),
                leaseService
        );
        when(executor.execute(any(), eq("land_grab"), eq("init"), eq(false), eq(false), eq(15)))
                .thenReturn("{\"walls\":[[1,2]],\"coins\":[[3,4]]}");

        var response = mapService.startMatch(42L);

        Path mapFile = tempDir.resolve(response.matchId()).resolve("map.json");
        assertThat(mapFile).exists();
        assertThat(Files.readString(mapFile)).contains("\"walls\"").contains("\"coins\"");
        verify(leaseService).create(response.matchId(), 42L);
    }

    @Test
    void mapInitRejectsErrorPayloadsBeforeCreatingALease() throws Exception {
        DockerMatchExecutor executor = mock(DockerMatchExecutor.class);
        WorkspaceLeaseService leaseService = mock(WorkspaceLeaseService.class);
        LandGrabService mapService = new LandGrabService(
                new CodeTemplateManager(),
                new ObjectMapper(),
                executor,
                new MatchWorkspaceManager(tempDir.toString()),
                leaseService
        );
        when(executor.execute(any(), eq("land_grab"), eq("init"), eq(false), eq(false), eq(15)))
                .thenReturn("{\"error\":\"bind mount is not writable\"}");

        assertThatThrownBy(() -> mapService.startMatch(42L))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Docker init failed");
        assertThat(tempDir).isEmptyDirectory();
    }

}
