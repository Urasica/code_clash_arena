package com.battle.code.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Component
public class MatchWorkspaceManager {

    private final Path workspaceRoot;

    public MatchWorkspaceManager(@Value("${cca.engine.workspace:temp}") String configuredRoot) {
        Path root = Paths.get(configuredRoot);
        if (!root.isAbsolute()) {
            root = Paths.get(System.getProperty("user.dir")).resolve(root);
        }
        this.workspaceRoot = root.toAbsolutePath().normalize();
    }

    public Path resolve(String matchId) {
        try {
            UUID.fromString(matchId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid match ID.");
        }

        Path matchDir = workspaceRoot.resolve(matchId).normalize();
        if (!matchDir.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Invalid match path.");
        }
        return matchDir;
    }

    public void delete(Path matchDir) {
        if (!matchDir.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Refusing to delete a path outside the match workspace.");
        }
        if (!Files.exists(matchDir)) {
            return;
        }

        try (var paths = Files.walk(matchDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("Failed to delete match workspace path {}", path, exception);
                }
            });
        } catch (IOException exception) {
            log.warn("Failed to clean match workspace {}", matchDir, exception);
        }
    }
}
