package com.battle.code.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LandGrabService {

    private static final String GAME_TYPE = "land_grab";
    private static final String ENGINE_IMAGE = "code-battle-engine";
    private static final int INIT_TIMEOUT_SECONDS = 15;
    private static final int COMPILE_TIMEOUT_SECONDS = 20;
    private static final int RUN_TIMEOUT_SECONDS = 40;
    private static final int MAX_OUTPUT_BYTES = 8 * 1024 * 1024;

    private final CodeTemplateManager templateManager;
    private final ObjectMapper objectMapper;

    public Map<String, Object> startMatch() throws IOException, InterruptedException {
        String matchId = UUID.randomUUID().toString();
        Path matchDir = resolveMatchDir(matchId);
        try {
            return initializeMap(matchId, matchDir);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            deleteWorkspace(matchDir);
            throw exception;
        }
    }

    public Map<String, Object> generateTransientMap() throws IOException, InterruptedException {
        String matchId = UUID.randomUUID().toString();
        Path matchDir = resolveMatchDir(matchId);
        try {
            Map<String, Object> map = initializeMap(matchId, matchDir);
            map.remove("matchId");
            return map;
        } finally {
            deleteWorkspace(matchDir);
        }
    }

    private Map<String, Object> initializeMap(String matchId, Path matchDir) throws IOException, InterruptedException {
        Files.createDirectories(matchDir);
        ProcessBuilder pb = createDockerProcess(matchDir, "init", true, false);
        String output = runProcessAndGetOutput(pb, INIT_TIMEOUT_SECONDS);

        if (output.isBlank()) {
            throw new IOException("Docker init output is empty.");
        }
        Map<String, Object> mapData = objectMapper.readValue(output, Map.class);
        Map<String, Object> response = new HashMap<>(mapData);
        response.put("matchId", matchId);
        return response;
    }

    public Map<String, Object> compileCode(String matchId, String userCode, String language) throws IOException, InterruptedException {
        Path matchDir = resolveMatchDir(matchId);
        if (!Files.exists(matchDir)) throw new RuntimeException("Match ID not found.");

        savePlayerCode(matchDir, "p1", language, userCode);

        ProcessBuilder pb = createDockerProcess(matchDir, "compile", false, true);

        String output = runProcessAndGetOutput(pb, COMPILE_TIMEOUT_SECONDS);
        return objectMapper.readValue(output, Map.class);
    }

    public Map<String, Object> runMatch(String matchId, String userCode, String language, String difficulty) throws IOException, InterruptedException {
        Path matchDir = resolveMatchDir(matchId);
        if (!Files.exists(matchDir)) throw new RuntimeException("Match ID not found.");

        try {
            savePlayerCode(matchDir, "p1", language, userCode);

            String targetDifficulty = (difficulty != null) ? difficulty.toLowerCase() : "easy";
            String aiCode = templateManager.loadAiCode(GAME_TYPE, targetDifficulty);
            Path aiDir = matchDir.resolve("p2");
            Files.createDirectories(aiDir);
            Files.writeString(aiDir.resolve("p2.py"), aiCode, StandardCharsets.UTF_8);

            ProcessBuilder pb = createDockerProcess(matchDir, "run", true, true);
            String jsonOutput = runProcessAndGetOutput(pb, RUN_TIMEOUT_SECONDS);
            return objectMapper.readValue(jsonOutput, Map.class);
        } finally {
            deleteWorkspace(matchDir);
        }
    }

    // PvP 매치 실행
    public Map<String, Object> runPvPMatch(String matchId, String p1Code, String p1Lang, String p2Code, String p2Lang, String mapDataJson) throws IOException, InterruptedException {
        Path matchDir = resolveMatchDir(matchId);
        if (!Files.exists(matchDir)) Files.createDirectories(matchDir);

        try {
            JsonNode rootNode = objectMapper.readTree(mapDataJson);
            JsonNode mapToSave = rootNode.has("map") ? rootNode.get("map") : rootNode;
            objectMapper.writeValue(matchDir.resolve("map.json").toFile(), mapToSave);

            savePlayerCode(matchDir, "p1", p1Lang, p1Code);
            savePlayerCode(matchDir, "p2", p2Lang, p2Code);

            ProcessBuilder pb = createDockerProcess(matchDir, "run", true, true);
            String jsonOutput = runProcessAndGetOutput(pb, RUN_TIMEOUT_SECONDS);
            log.debug("Docker result received for match {} ({} bytes)", matchId, jsonOutput.length());

            return objectMapper.readValue(jsonOutput, Map.class);
        } finally {
            deleteWorkspace(matchDir);
        }
    }

    void savePlayerCode(Path matchDir, String player, String lang, String code) throws IOException {
        lang = (lang != null) ? lang.toLowerCase() : "python";

        Path playerDir = matchDir.resolve(player);
        if (!Files.exists(playerDir)) Files.createDirectories(playerDir);

        String template = templateManager.loadRunnerTemplate(lang);
        String finalCode = template.replace("%USER_CODE%", code);

        String fileName;
        if (lang.equals("java")) {
            fileName = "Main.java";
        } else {
            fileName = player + getExtension(lang);
        }

        Files.writeString(playerDir.resolve(fileName), finalCode, StandardCharsets.UTF_8);
    }

    private String getExtension(String language) {
        if (language == null) return ".txt";
        return switch (language.toLowerCase()) {
            case "python" -> ".py";
            case "java" -> ".java";
            case "c" -> ".c";
            case "cpp" -> ".cpp";
            case "javascript", "node", "nodejs" -> ".js";
            default -> ".txt";
        };
    }

    Path resolveMatchDir(String matchId) {
        try {
            UUID.fromString(matchId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid match ID.");
        }

        Path tempRoot = Paths.get(System.getProperty("user.dir"), "temp").toAbsolutePath().normalize();
        Path matchDir = tempRoot.resolve(matchId).normalize();
        if (!matchDir.startsWith(tempRoot)) {
            throw new IllegalArgumentException("Invalid match path.");
        }
        return matchDir;
    }

    ProcessBuilder createDockerProcess(
            Path matchDir,
            String mode,
            boolean mountData,
            boolean mountPlayers
    ) {
        String hostPath = matchDir.toString().replace("\\", "/");
        List<String> command = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--cpus", "0.5",
                "--memory", "512m",
                "--pids-limit", "128",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges"
        ));
        if (mountData) {
            command.addAll(List.of("-v", hostPath + ":/app/data"));
        }
        if (mountPlayers) {
            command.addAll(List.of("-v", hostPath + ":/app/players"));
        }
        command.addAll(List.of(
                ENGINE_IMAGE,
                "python3", "referee.py", GAME_TYPE, mode
        ));
        return new ProcessBuilder(command);
    }

    private String runProcessAndGetOutput(ProcessBuilder pb, int timeoutSeconds) throws IOException, InterruptedException {
        Process process = pb.start();
        CompletableFuture<StreamCapture> stdoutFuture = captureAsync(process.getInputStream());
        CompletableFuture<StreamCapture> stderrFuture = captureAsync(process.getErrorStream());

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            throw new IOException("Docker execution timed out after " + timeoutSeconds + " seconds.");
        }

        StreamCapture stdout = awaitCapture(stdoutFuture);
        StreamCapture stderr = awaitCapture(stderrFuture);
        if (stdout.truncated() || stderr.truncated()) {
            throw new IOException("Docker output exceeded " + MAX_OUTPUT_BYTES + " bytes.");
        }

        if (process.exitValue() != 0) {
            log.error("Docker execution failed (exit code {}). stderr: {}", process.exitValue(), stderr.text());
            throw new IOException("Docker execution failed: " + stderr.text());
        }
        return stdout.text().trim();
    }

    private CompletableFuture<StreamCapture> captureAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] buffer = new byte[8192];
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                boolean truncated = false;
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    int remaining = MAX_OUTPUT_BYTES - output.size();
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

    private void deleteWorkspace(Path matchDir) {
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

    private record StreamCapture(String text, boolean truncated) {}
}
