package com.battle.code.controller;

import com.battle.code.dto.RunRequestDto;
import com.battle.code.service.LandGrabService;
import com.battle.code.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/match/land-grab")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LandGrabMatchController {
    private final LandGrabService landGrabService;
    private final MatchService matchService;

    // 매치 생성 (맵 받기)
    @PostMapping("/start")
    public ResponseEntity<?> startMatch() {
        try {
            var result = landGrabService.startMatch();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    //코드 제출 및 실행 (인자 추가됨)
    @PostMapping("/run")
    public ResponseEntity<?> runMatch(@RequestBody RunRequestDto request,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        // [디버그] 요청 진입 확인
        System.out.println("🚀 [DEBUG] /run Request Received");
        System.out.println("👤 [DEBUG] UserDetails: " + (userDetails != null ? userDetails.getUsername() : "NULL"));

        try {
            Map<String, Object> result = landGrabService.runMatch(
                    request.getMatchId(),
                    request.getUserCode(),
                    request.getLanguage(),
                    request.getDifficulty()
            );

            // [디버그] 실행 결과 확인
            System.out.println("📊 [DEBUG] Match Result Keys: " + result.keySet());

            if (userDetails != null) {
                try {
                    Long userId = Long.parseLong(userDetails.getUsername());
                    System.out.println("💾 [DEBUG] Attempting to save match for User ID: " + userId);

                    matchService.saveMatchResult(
                            userId,
                            request.getMatchId(),
                            result,
                            request.getUserCode(),
                            request.getLanguage() != null ? request.getLanguage() : "python",
                            request.getDifficulty()
                    );
                } catch (Exception e) {
                    System.err.println("❌ [ERROR] Save Failed: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("⚠️ [DEBUG] UserDetails is NULL. Skipping Save.");
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
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