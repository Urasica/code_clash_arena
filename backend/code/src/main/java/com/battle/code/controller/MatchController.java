package com.battle.code.controller;

import com.battle.code.dto.RunRequestDto;
import com.battle.code.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MatchController {
    private final GameService gameService;

    // 1단계: 매치 생성 (맵 받기)
    @PostMapping("/start")
    public ResponseEntity<?> startMatch() {
        try {
            var result = gameService.startMatch();
            return ResponseEntity.ok(result); // { matchId: "...", map: {...} }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // 2단계: 코드 제출 및 실행
    @PostMapping("/run")
    public ResponseEntity<?> runMatch(@RequestBody RunRequestDto request) {
        try {
            String logs = gameService.runMatch(request.getMatchId(), request.getUserCode());
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}

