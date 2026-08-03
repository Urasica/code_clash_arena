package com.battle.code.service;

import com.battle.code.dto.CompileResultDto;
import com.battle.code.dto.LandGrabMapDto;
import com.battle.code.dto.MatchExecutionResultDto;
import com.battle.code.dto.StartMatchResponseDto;
import com.battle.code.execution.DockerMatchExecutor;
import com.battle.code.execution.MatchWorkspaceManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LandGrabService {

    private static final String GAME_TYPE = "land_grab";
    private static final int INIT_TIMEOUT_SECONDS = 15;
    private static final int COMPILE_TIMEOUT_SECONDS = 20;
    private static final int RUN_TIMEOUT_SECONDS = 40;

    private final CodeTemplateManager templateManager;
    private final ObjectMapper objectMapper;
    private final DockerMatchExecutor dockerExecutor;
    private final MatchWorkspaceManager workspaceManager;

    public StartMatchResponseDto startMatch() throws IOException, InterruptedException {
        String matchId = UUID.randomUUID().toString();
        Path matchDir = workspaceManager.resolve(matchId);
        try {
            return initializeMap(matchId, matchDir);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            workspaceManager.delete(matchDir);
            throw exception;
        }
    }

    public LandGrabMapDto generateTransientMap() throws IOException, InterruptedException {
        String matchId = UUID.randomUUID().toString();
        Path matchDir = workspaceManager.resolve(matchId);
        try {
            return initializeMap(matchId, matchDir).map();
        } finally {
            workspaceManager.delete(matchDir);
        }
    }

    private StartMatchResponseDto initializeMap(String matchId, Path matchDir) throws IOException, InterruptedException {
        Files.createDirectories(matchDir);
        String output = dockerExecutor.execute(
                matchDir, GAME_TYPE, "init", true, false, INIT_TIMEOUT_SECONDS
        );

        if (output.isBlank()) {
            throw new IOException("Docker init output is empty.");
        }
        LandGrabMapDto mapData = objectMapper.readValue(output, LandGrabMapDto.class);
        return new StartMatchResponseDto(matchId, mapData.walls(), mapData.coins());
    }

    public CompileResultDto compileCode(String matchId, String userCode, String language) throws IOException, InterruptedException {
        Path matchDir = workspaceManager.resolve(matchId);
        if (!Files.exists(matchDir)) throw new RuntimeException("Match ID not found.");

        savePlayerCode(matchDir, "p1", language, userCode);

        String output = dockerExecutor.execute(
                matchDir, GAME_TYPE, "compile", false, true, COMPILE_TIMEOUT_SECONDS
        );
        return objectMapper.readValue(output, CompileResultDto.class);
    }

    public MatchExecutionResultDto runMatch(String matchId, String userCode, String language, String difficulty) throws IOException, InterruptedException {
        Path matchDir = workspaceManager.resolve(matchId);
        if (!Files.exists(matchDir)) throw new RuntimeException("Match ID not found.");

        try {
            savePlayerCode(matchDir, "p1", language, userCode);

            String targetDifficulty = (difficulty != null) ? difficulty.toLowerCase() : "easy";
            String aiCode = templateManager.loadAiCode(GAME_TYPE, targetDifficulty);
            Path aiDir = matchDir.resolve("p2");
            Files.createDirectories(aiDir);
            Files.writeString(aiDir.resolve("p2.py"), aiCode, StandardCharsets.UTF_8);

            String jsonOutput = dockerExecutor.execute(
                    matchDir, GAME_TYPE, "run", true, true, RUN_TIMEOUT_SECONDS
            );
            return objectMapper.readValue(jsonOutput, MatchExecutionResultDto.class);
        } finally {
            workspaceManager.delete(matchDir);
        }
    }

    // PvP 매치 실행
    public MatchExecutionResultDto runPvPMatch(String matchId, String p1Code, String p1Lang, String p2Code, String p2Lang, String mapDataJson) throws IOException, InterruptedException {
        Path matchDir = workspaceManager.resolve(matchId);
        if (!Files.exists(matchDir)) Files.createDirectories(matchDir);

        try {
            JsonNode rootNode = objectMapper.readTree(mapDataJson);
            JsonNode mapToSave = rootNode.has("map") ? rootNode.get("map") : rootNode;
            objectMapper.writeValue(matchDir.resolve("map.json").toFile(), mapToSave);

            savePlayerCode(matchDir, "p1", p1Lang, p1Code);
            savePlayerCode(matchDir, "p2", p2Lang, p2Code);

            String jsonOutput = dockerExecutor.execute(
                    matchDir, GAME_TYPE, "run", true, true, RUN_TIMEOUT_SECONDS
            );
            log.debug("Docker result received for match {} ({} bytes)", matchId, jsonOutput.length());

            return objectMapper.readValue(jsonOutput, MatchExecutionResultDto.class);
        } finally {
            workspaceManager.delete(matchDir);
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

}
