package com.battle.code.service;

import com.battle.code.domain.*;
import com.battle.code.repository.GameMatchRepository;
import com.battle.code.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final GameMatchRepository matchRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveMatchResult(Long userId, String matchId, Map<String, Object> resultData,
                                String userCode, String language, String difficulty) {

        // 에러 체크: 컴파일 에러나 런타임 에러가 있으면 저장하지 않음
        String systemError = (String) resultData.get("error");
        String p1Error = (String) resultData.get("p1_error");

        // 시스템 에러(도커 실패 등)는 무조건 저장 중단
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
                // .mapData(...) // 필요하다면 초기 맵 데이터도 저장 가능
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
            e.printStackTrace();
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

        System.out.println("Match Saved! ID: " + match.getId());
    }
}