package com.battle.code.service;

import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CodeTemplateManager {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String loadRunnerTemplate(String language) {
        String filename = switch (language.toLowerCase()) {
            case "python" -> "python_runner.py";
            case "java" -> "java_runner.txt";
            case "cpp" -> "cpp_runner.cpp";
            case "c" -> "c_runner.c";
            case "javascript", "node", "nodejs" -> "js_runner.js";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
        return loadResource("templates/runners/" + filename);
    }

    public String loadAiCode(String gameType, String difficulty) {

        String diffFilename = difficulty.toLowerCase();

        // 경로: templates/ai/landgrab/hard.py
        String path = String.format("templates/ai/%s/%s.py", gameType, diffFilename);

        System.out.println("Trying to load AI code from: " + path); // 로그 추가

        return loadResource(path);
    }

    private String loadResource(String path) {
        return cache.computeIfAbsent(path, p -> {
            try {
                ClassPathResource resource = new ClassPathResource(p);
                // 리소스가 없으면 에러가 발생함 -> try-catch로 잡아서 기본값 반환하는지 확인 필요
                if (!resource.exists()) {
                    throw new RuntimeException("Resource file not found: " + p);
                }
                return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                // [의심 포인트] 여기서 에러가 났을 때 상위에서 잡아서 "Easy"로 퉁치고 있을 수 있음
                throw new RuntimeException("Failed to load resource: " + p, e);
            }
        });
    }
}