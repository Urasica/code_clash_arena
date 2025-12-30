package com.battle.code.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LandGrabService {

    private final CodeTemplateManager templateManager;
    private final String GAME_TYPE = "land_grab";
    private final ObjectMapper objectMapper;

    public Map<String, Object> startMatch() throws IOException, InterruptedException {
        String matchId = UUID.randomUUID().toString();
        Path matchDir = Paths.get(System.getProperty("user.dir"), "temp", matchId).toAbsolutePath();
        Files.createDirectories(matchDir);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/data",
                "code-battle-engine",
                "python3", "referee.py", GAME_TYPE, "init"
        );

        String output = runProcessAndGetOutput(pb);

        if (output.isBlank()) {
            throw new RuntimeException("Docker 'init' output is empty. Check log for details.");
        }

        Map<String, Object> mapData = objectMapper.readValue(output, Map.class);

        Map<String, Object> response = new HashMap<>(mapData);
        response.put("matchId", matchId);

        return response;
    }

    public Map compileCode(String matchId, String userCode, String language) throws IOException, InterruptedException {
        Path matchDir = Paths.get(System.getProperty("user.dir"), "temp", matchId).toAbsolutePath();
        if (!Files.exists(matchDir)) throw new RuntimeException("Match ID not found.");

        String runner = templateManager.loadRunnerTemplate(language);
        String finalCode = runner.replace("%USER_CODE%", userCode);
        String p1File = language.equalsIgnoreCase("java") ? "Main.java" : "p1" + getExtension(language);
        Files.writeString(matchDir.resolve(p1File), finalCode);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/players",
                "code-battle-engine",
                "python3", "referee.py", GAME_TYPE, "compile"
        );

        String output = runProcessAndGetOutput(pb);
        return objectMapper.readValue(output, Map.class);
    }

    public Map<String, Object> runMatch(String matchId, String userCode, String language, String difficulty) throws IOException, InterruptedException {
        Path matchDir = Paths.get(System.getProperty("user.dir"), "temp", matchId).toAbsolutePath();
        if (!Files.exists(matchDir)) throw new RuntimeException("Match ID not found.");

        String runner = templateManager.loadRunnerTemplate(language);
        String finalCode = runner.replace("%USER_CODE%", userCode);
        String p1File = language.equalsIgnoreCase("java") ? "Main.java" : "p1" + getExtension(language);
        Files.writeString(matchDir.resolve(p1File), finalCode);

        String targetDifficulty = (difficulty != null) ? difficulty.toLowerCase() : "easy";
        String aiCode = templateManager.loadAiCode(GAME_TYPE, targetDifficulty);
        Files.writeString(matchDir.resolve("p2.py"), aiCode);

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/data",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/players",
                "code-battle-engine",
                "python3", "referee.py", GAME_TYPE, "run"
        );

        String jsonOutput = runProcessAndGetOutput(pb);
        return objectMapper.readValue(jsonOutput, Map.class);
    }

    // PvP 매치 실행
    public Map<String, Object> runPvPMatch(String matchId, String p1Code, String p1Lang, String p2Code, String p2Lang, String mapDataJson) throws IOException, InterruptedException {
        Path matchDir = Paths.get(System.getProperty("user.dir"), "temp", matchId).toAbsolutePath();
        if (!Files.exists(matchDir)) Files.createDirectories(matchDir);

        // 맵 파일 저장
        JsonNode rootNode = objectMapper.readTree(mapDataJson);
        JsonNode mapToSave = rootNode.has("map") ? rootNode.get("map") : rootNode;

        objectMapper.writeValue(matchDir.resolve("map.json").toFile(), mapToSave);

        // 플레이어 코드 저장
        savePlayerCode(matchDir, "p1", p1Lang, p1Code);
        savePlayerCode(matchDir, "p2", p2Lang, p2Code);

        // Docker 실행
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/data",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/players",
                "code-battle-engine",
                "python3", "referee.py", GAME_TYPE, "run"
        );

        String jsonOutput = runProcessAndGetOutput(pb);
        log.info("Docker Result (Raw): {}", jsonOutput);

        return objectMapper.readValue(jsonOutput, Map.class);
    }

    private void savePlayerCode(Path matchDir, String player, String lang, String code) throws IOException {
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

        Files.writeString(playerDir.resolve(fileName), finalCode);
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

    private String runProcessAndGetOutput(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            log.error("Docker execution failed (Exit Code: {}). Output:\n{}", exitCode, output);
        }
        return output.trim();
    }
}