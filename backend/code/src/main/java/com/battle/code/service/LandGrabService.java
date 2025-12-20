package com.battle.code.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LandGrabService {

    private final CodeTemplateManager templateManager;
    private final String GAME_TYPE = "landgrab";

    public Map<String, Object> startMatch() throws IOException, InterruptedException {
        // (기존 코드와 동일)
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
        Map<String, Object> response = new HashMap<>();
        response.put("matchId", matchId);
        response.put("map", new ObjectMapper().readValue(output, Map.class));
        return response;
    }

    public Map compileCode(String matchId, String userCode, String language) throws IOException, InterruptedException {
        Path matchDir = Paths.get(System.getProperty("user.dir"), "temp", matchId).toAbsolutePath();
        if (!Files.exists(matchDir)) throw new RuntimeException("Match ID not found.");

        // 1. 유저 코드 저장
        String runner = templateManager.loadRunnerTemplate(language);
        String finalCode = runner.replace("%USER_CODE%", userCode);
        String p1File = language.equalsIgnoreCase("java") ? "Main.java" : "p1" + getExtension(language);
        Files.writeString(matchDir.resolve(p1File), finalCode);

        // 2. Docker 실행 (compile 모드)
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/players", // 코드만 있으면 됨
                "code-battle-engine",
                "python3", "referee.py", GAME_TYPE, "compile" // compile 모드 호출
        );

        String output = runProcessAndGetOutput(pb);
        return new ObjectMapper().readValue(output, Map.class);
    }

    public String runMatch(String matchId, String userCode, String language, String difficulty) throws IOException, InterruptedException {
        // [디버깅 로그] 프론트에서 값이 제대로 오는지 확인
        System.out.println("=== Run Match Request ===");
        System.out.println("Match ID: " + matchId);
        System.out.println("Language: " + language);
        System.out.println("Difficulty (Received): " + difficulty);

        Path matchDir = Paths.get(System.getProperty("user.dir"), "temp", matchId).toAbsolutePath();
        if (!Files.exists(matchDir)) throw new RuntimeException("Match ID not found.");

        // 1. 유저 코드 저장
        String runner = templateManager.loadRunnerTemplate(language);
        String finalCode = runner.replace("%USER_CODE%", userCode);

        // [수정] Java는 Main.java 강제, 나머지는 확장자 메서드 사용
        String p1File = language.equalsIgnoreCase("java") ? "Main.java" : "p1" + getExtension(language);
        Files.writeString(matchDir.resolve(p1File), finalCode);

        // 2. AI 코드 저장
        // [수정] 대소문자 문제 방지를 위해 소문자로 변환하여 요청
        String targetDifficulty = (difficulty != null) ? difficulty.toLowerCase() : "easy";
        System.out.println("AI Difficulty (Loading): " + targetDifficulty); // 로그 확인

        String aiCode = templateManager.loadAiCode(GAME_TYPE, targetDifficulty);
        Files.writeString(matchDir.resolve("p2.py"), aiCode);

        // 3. Docker 실행
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/data",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/players",
                "code-battle-engine",
                "python3", "referee.py", GAME_TYPE, "run"
        );

        return runProcessAndGetOutput(pb);
    }

    // [중요] C, C++, JS 확장자 처리가 확실한지 확인
    private String getExtension(String language) {
        if (language == null) return ".txt";
        return switch (language.toLowerCase()) {
            case "python" -> ".py";
            case "java" -> ".java";
            case "c" -> ".c";
            case "cpp" -> ".cpp";
            case "javascript", "node", "nodejs" -> ".js"; // JS 관련 키워드 모두 처리
            default -> ".txt";
        };
    }

    private String runProcessAndGetOutput(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) output.append(line);
        process.waitFor();
        return output.toString();
    }
}