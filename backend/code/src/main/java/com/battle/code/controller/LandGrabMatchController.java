package com.battle.code.controller;

import com.battle.code.dto.RunRequestDto;
import com.battle.code.service.LandGrabService;
import com.battle.code.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/match/land-grab")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LandGrabMatchController {
    private final LandGrabService landGrabService;
    private final MatchService matchService;
    private static final Logger log =
            LoggerFactory.getLogger(LandGrabMatchController.class);

    // 매치 생성 (맵 받기)
    @PostMapping("/start")
    public ResponseEntity<?> startMatch() {
        log.info("[LAND_GRAB_START] Request");

        try {
            var result = landGrabService.startMatch();
            log.info("[LAND_GRAB_START] Success");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[LAND_GRAB_START] Failed", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    //코드 제출 및 실행
    @PostMapping("/run")
    public ResponseEntity<?> runMatch(@RequestBody RunRequestDto request,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = null;
        if (userDetails != null) {
            userId = Long.parseLong(userDetails.getUsername());
        }

        log.info("[LAND_GRAB_RUN] Request - matchId={}, userId={}, lang={}, diff={}",
                request.getMatchId(),
                userId,
                request.getLanguage(),
                request.getDifficulty());

        try {
            Map<String, Object> result = landGrabService.runMatch(
                    request.getMatchId(),
                    request.getUserCode(),
                    request.getLanguage(),
                    request.getDifficulty()
            );

            log.debug("[LAND_GRAB_RUN] Result keys={}", result.keySet());

            if (userDetails != null) {
                try {
                    log.info("[MATCH_SAVE] Attempt - userId={}, matchId={}",
                            userId, request.getMatchId());

                    matchService.saveMatchResult(
                            userId,
                            request.getMatchId(),
                            result,
                            request.getUserCode(),
                            request.getLanguage() != null ? request.getLanguage() : "python",
                            request.getDifficulty()
                    );

                    log.info("[MATCH_SAVE] Success - userId={}, matchId={}",
                            userId, request.getMatchId());
                } catch (Exception e) {
                    log.error("[MATCH_SAVE] Failed - userId={}, matchId={}",
                            userId, request.getMatchId(), e);
                }
            } else {
                log.warn("[MATCH_SAVE] Skipped - anonymous user");
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[LAND_GRAB_RUN] Failed - matchId={}, userId={}",
                    request.getMatchId(), userId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/compile")
    public ResponseEntity<?> compileMatch(@RequestBody RunRequestDto request) {

        String language = request.getLanguage() != null
                ? request.getLanguage()
                : "python";

        log.info("[LAND_GRAB_COMPILE] Request - matchId={}, lang={}",
                request.getMatchId(), language);

        try {
            Map<String, Object> result = landGrabService.compileCode(
                    request.getMatchId(),
                    request.getUserCode(),
                    language
            );

            log.info("[LAND_GRAB_COMPILE] Success - matchId={}",
                    request.getMatchId());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[LAND_GRAB_COMPILE] Failed - matchId={}",
                    request.getMatchId(), e);

            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}