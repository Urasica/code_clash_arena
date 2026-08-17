package com.battle.code.execution;

import com.battle.code.observability.MatchTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class MatchWorkspaceManager {

    private final Path workspaceRoot;
    private final MatchTelemetry telemetry;

    @Autowired
    public MatchWorkspaceManager(
            @Value("${cca.engine.workspace:temp}") String configuredRoot,
            MatchTelemetry telemetry
    ) {
        Path root = Paths.get(configuredRoot);
        if (!root.isAbsolute()) {
            root = Paths.get(System.getProperty("user.dir")).resolve(root);
        }
        this.workspaceRoot = root.toAbsolutePath().normalize();
        this.telemetry = telemetry;
    }

    public MatchWorkspaceManager(String configuredRoot) {
        this(configuredRoot, MatchTelemetry.noOp());
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

        boolean[] succeeded = {true};
        try (var paths = Files.walk(matchDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    succeeded[0] = false;
                    log.warn("Failed to delete match workspace path {}", path, exception);
                }
            });
        } catch (IOException exception) {
            succeeded[0] = false;
            log.warn("Failed to clean match workspace {}", matchDir, exception);
        }
        telemetry.workspaceCleanup(succeeded[0] ? "success" : "failure");
    }

    public List<Path> findOlderThan(Duration age) {
        if (!Files.isDirectory(workspaceRoot)) {
            return List.of();
        }
        Instant cutoff = Instant.now().minus(age);
        List<Path> expired = new ArrayList<>();
        try (var children = Files.list(workspaceRoot)) {
            children.filter(Files::isDirectory).forEach(path -> {
                try {
                    UUID.fromString(path.getFileName().toString());
                    if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                        expired.add(path.toAbsolutePath().normalize());
                    }
                } catch (IllegalArgumentException ignored) {
                    log.warn("Ignoring non-match directory below workspace root: {}", path);
                } catch (IOException exception) {
                    telemetry.workspaceCleanup("failure");
                    log.warn("Could not inspect workspace age: {}", path, exception);
                }
            });
        } catch (IOException exception) {
            telemetry.workspaceCleanup("failure");
            log.warn("Could not scan match workspace root {}", workspaceRoot, exception);
        }
        return expired;
    }
}
