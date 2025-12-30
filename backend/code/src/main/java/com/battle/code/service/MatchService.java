package com.battle.code.service;

import com.battle.code.domain.*;
import com.battle.code.repository.GameMatchRepository;
import com.battle.code.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchService {

    private final GameMatchRepository matchRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * [PvP] 매치 결과 저장 (유저 vs 유저)
     */
    @Transactional
    public void savePvPMatchResult(String matchId, Long p1Id, Long p2Id, Map<String, Object> resultData,
                                   String p1Code, String p1Lang, String p2Code, String p2Lang) {

        // 시스템 에러 체크
        String systemError = (String) resultData.get("error");
        if (systemError != null && !systemError.isEmpty()) {
            log.warn("[MatchService] System error detected for match {}. Not saving.", matchId);
            return;
        }

        // 유저 조회
        User p1User = userRepository.findById(p1Id)
                .orElseThrow(() -> new RuntimeException("Player 1 not found: " + p1Id));
        User p2User = userRepository.findById(p2Id)
                .orElseThrow(() -> new RuntimeException("Player 2 not found: " + p2Id));

        // 결과 데이터 파싱 (Null Safety)
        String winner = (String) resultData.get("winner"); // "p1", "p2", "draw"
        String reason = (String) resultData.get("reason"); // "OPPONENT_DISCONNECTED" 등
        Map<String, Integer> scores = (Map<String, Integer>) resultData.get("final_scores");

        int p1Score = (scores != null && scores.containsKey("p1")) ? scores.get("p1") : 0;
        int p2Score = (scores != null && scores.containsKey("p2")) ? scores.get("p2") : 0;

        // GameMatch 생성
        GameMatch match = GameMatch.builder()
                .matchUuid(matchId)
                .gameType("LAND_GRAB")
                .mode("PVP")
                .build();

        // 로그 저장 (탈주 시 로그 없음)
        if (resultData.get("logs") != null) {
            try {
                String fullLogJson = objectMapper.writeValueAsString(resultData.get("logs"));
                MatchReplay replay = MatchReplay.builder()
                        .fullLog(fullLogJson)
                        .build();
                match.setReplay(replay);
            } catch (Exception e) {
                log.error("Failed to serialize match logs", e);
            }
        }

        // 플레이어 1 기록
        String p1Result = determineResult("p1", winner, reason, resultData.get("p1_error") != null);
        match.addPlayer(MatchPlayer.builder()
                .user(p1User)
                .playerIndex("p1")
                .result(p1Result)
                .score(p1Score)
                .language(p1Lang)
                .submittedCode(p1Code)
                .build());

        // 플레이어 2 기록
        String p2Result = determineResult("p2", winner, reason, resultData.get("p2_error") != null);
        match.addPlayer(MatchPlayer.builder()
                .user(p2User)
                .playerIndex("p2")
                .result(p2Result)
                .score(p2Score)
                .language(p2Lang)
                .submittedCode(p2Code)
                .build());

        // 저장
        matchRepository.save(match);
        log.info("PvP Match Saved! ID: {}, Winner: {}", match.getId(), winner);
    }

    // 승패 판정 헬퍼
    private String determineResult(String playerRole, String winner, String reason, boolean hasError) {
        if (hasError) return "LOSE"; // 런타임 에러
        if ("draw".equalsIgnoreCase(winner)) return "DRAW";
        if (playerRole.equals(winner)) return "WIN";
        return "LOSE";
    }

    @Transactional
    public void saveMatchResult(Long userId, String matchId, Map<String, Object> resultData,
                                String userCode, String language, String difficulty) {

        // 에러 체크
        String systemError = (String) resultData.get("error");

        // 시스템 에러 시 저장 중단
        if (systemError != null && !systemError.isEmpty()) {
            System.out.println("[DEBUG] System error detected. Not saving.");
            return;
        }

        // 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 결과 데이터 파싱
        String winner = (String) resultData.get("winner"); // "p1", "p2", "draw"
        Map<String, Integer> scores = (Map<String, Integer>) resultData.get("final_scores");

        // GameMatch 생성 (공통 정보)
        GameMatch match = GameMatch.builder()
                .matchUuid(matchId)
                .gameType("LAND_GRAB")
                .mode("AI")
                .build();

        // 로그 분리 저장 (MatchReplay)
        try {
            // logs 배열을 JSON 문자열로 변환하여 저장
            String fullLogJson = objectMapper.writeValueAsString(resultData.get("logs"));
            MatchReplay replay = MatchReplay.builder()
                    .fullLog(fullLogJson)
                    .build();
            match.setReplay(replay); // 연관관계 설정
        } catch (Exception e) {
            log.warn("save error: {}", e.getMessage());
            // 로그 저장 실패해도 매치 기록은 남기도록 진행
        }

        // 플레이어 기록 (P1: 유저)
        boolean p1Crashed = resultData.get("p1_error") != null;
        String p1Result = "DRAW";
        if (p1Crashed) p1Result = "LOSE"; // 런타이 에러 시 패배
        else if ("p1".equals(winner)) p1Result = "WIN";
        else if ("p2".equals(winner)) p1Result = "LOSE";

        MatchPlayer p1 = MatchPlayer.builder()
                .user(user)
                .playerIndex("p1")
                .result(p1Result)
                .score(scores != null ? scores.get("p1") : 0)
                .language(language)
                .submittedCode(userCode)
                .build();
        match.addPlayer(p1);

        // 플레이어 기록 (P2: AI)
        String p2Result = "DRAW";
        if ("p2".equals(winner)) p2Result = "WIN";
        else if ("p1".equals(winner)) p2Result = "LOSE";

        MatchPlayer p2 = MatchPlayer.builder()
                .user(null) // AI는 유저 없음
                .playerIndex("p2")
                .result(p2Result)
                .score(scores != null ? scores.get("p2") : 0)
                .language("python")
                .submittedCode("AI-" + difficulty.toUpperCase()) // AI 난이도 기록
                .build();
        match.addPlayer(p2);

        // 최종 저장 (Cascade 설정으로 match 저장 시 players, replay도 자동 저장됨)
        matchRepository.save(match);

        log.info("Match Saved! ID: {}" , match.getId());
    }
}