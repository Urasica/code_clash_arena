package com.battle.code.execution;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceJanitorTest {

    @Test
    void deletesOnlyExpiredWorkspacesWithoutAnActiveLease() {
        MatchWorkspaceManager manager = mock(MatchWorkspaceManager.class);
        WorkspaceLeaseService leases = mock(WorkspaceLeaseService.class);
        WorkspaceJanitor janitor = new WorkspaceJanitor(manager, leases);
        ReflectionTestUtils.setField(janitor, "idleTtl", Duration.ofMinutes(30));
        Path orphan = Path.of("root", "123e4567-e89b-42d3-a456-426614174000");
        Path active = Path.of("root", "123e4567-e89b-42d3-a456-426614174001");
        when(manager.findOlderThan(Duration.ofMinutes(30))).thenReturn(List.of(orphan, active));
        when(leases.exists(orphan.getFileName().toString())).thenReturn(false);
        when(leases.exists(active.getFileName().toString())).thenReturn(true);

        janitor.cleanup();

        verify(manager).delete(orphan);
        verify(manager, never()).delete(active);
    }
}
