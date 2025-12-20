package com.battle.code.controller;

import com.battle.code.dto.RunRequestDto;
import com.battle.code.service.LandGrabService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/match/land-grab")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LandGrabMatchController {
    private final LandGrabService landGrabService;

    // 1단계: 매치 생성 (맵 받기)
    @PostMapping("/start")
    public ResponseEntity<?> startMatch() {
        try {
            var result = landGrabService.startMatch();
            return ResponseEntity.ok(result); // { matchId: "...", map: {...} }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // 2단계: 코드 제출 및 실행 (인자 추가됨)
    @PostMapping("/run")
    public ResponseEntity<?> runMatch(@RequestBody RunRequestDto request) {
        try {
            String language = request.getLanguage() != null ? request.getLanguage() : "python";
            String difficulty = request.getDifficulty() != null ? request.getDifficulty() : "easy";

            String logs = landGrabService.runMatch(
                    request.getMatchId(),
                    request.getUserCode(),
                    language,
                    difficulty
            );
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            e.printStackTrace(); // 서버 로그에 에러 출력
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/compile")
    public ResponseEntity<?> compileMatch(@RequestBody RunRequestDto request) {
        try {
            // language가 null이면 기본값 처리
            String language = request.getLanguage() != null ? request.getLanguage() : "python";

            Map<String, Object> result = landGrabService.compileCode(
                    request.getMatchId(),
                    request.getUserCode(),
                    language
            );
            return ResponseEntity.ok(result); // { "status": "success" } or { "status": "error", "error": "..." }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}