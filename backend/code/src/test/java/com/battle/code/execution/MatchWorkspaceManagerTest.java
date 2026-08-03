package com.battle.code.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchWorkspaceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void invalidMatchIdsAreRejectedBeforeResolvingAWorkspace() {
        MatchWorkspaceManager manager = new MatchWorkspaceManager(tempDir.toString());

        assertThatThrownBy(() -> manager.resolve("../../outside"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid match ID.");
    }

    @Test
    void deleteOnlyRemovesTheRequestedMatchDirectory() throws Exception {
        MatchWorkspaceManager manager = new MatchWorkspaceManager(tempDir.toString());
        Path matchDir = manager.resolve(UUID.randomUUID().toString());
        Path sibling = tempDir.resolve("keep.txt");
        Files.createDirectories(matchDir);
        Files.writeString(matchDir.resolve("result.json"), "{}");
        Files.writeString(sibling, "keep");

        manager.delete(matchDir);

        assertThat(matchDir).doesNotExist();
        assertThat(sibling).exists();
    }
}
