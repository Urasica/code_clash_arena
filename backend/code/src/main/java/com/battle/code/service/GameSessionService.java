package com.battle.code.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameSessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LandGrabService landGrabService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchService matchService;

    // 유저가 코드를 제출했을 때 처리
    public void handleCodeSubmission(String matchId, Long userId, String code, String language) {
        String roomKey = "match_room:" + matchId;

        // 유효성 검사 및 역할(p1/p2) 확인
        if (!Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(roomKey, "p1"))) {
            log.warn("Submission for non-existent or expired match: {}", matchId);
            return;
        }

        String p1IdStr = String.valueOf(redisTemplate.opsForHash().get(roomKey, "p1"));
        String p2IdStr = String.valueOf(redisTemplate.opsForHash().get(roomKey, "p2"));

        String playerRole; // "p1" or "p2"
        if (String.valueOf(userId).equals(p1IdStr)) playerRole = "p1";
        else if (String.valueOf(userId).equals(p2IdStr)) playerRole = "p2";
        else {
            log.error("Unknown user {} tried to submit in match {}", userId, matchId);
            return;
        }

        // Redis에 코드 저장
        redisTemplate.opsForHash().put(roomKey, playerRole + "_code", code);
        redisTemplate.opsForHash().put(roomKey, playerRole + "_lang", language);

        log.info("Code saved for {} in match {}", playerRole, matchId);

        // 상대에게 "제출 완료" 알림 (UI 업데이트용 - role 포함)
        messagingTemplate.convertAndSend("/topic/game/" + matchId, Map.of(
                "type", "NOTIFICATION",
                "message", "PLAYER_SUBMITTED",
                "role", playerRole
        ));

        // 양쪽 다 제출했는지 확인 후 게임 시작
        boolean p1Ready = redisTemplate.opsForHash().hasKey(roomKey, "p1_code");
        boolean p2Ready = redisTemplate.opsForHash().hasKey(roomKey, "p2_code");

        if (p1Ready && p2Ready) {
            log.info("All players ready in match {}. Starting execution!", matchId);
            runPvPMatch(matchId, roomKey);
        } else {
            log.info("Waiting for opponent in match {}...", matchId);
        }
    }

    // [정상 종료] 양측 코드 실행 및 결과 처리
    private void runPvPMatch(String matchId, String roomKey) {
        try {
            // Redis에서 실행에 필요한 데이터 꺼내기
            String p1Code = (String) redisTemplate.opsForHash().get(roomKey, "p1_code");
            String p2Code = (String) redisTemplate.opsForHash().get(roomKey, "p2_code");
            String p1Lang = (String) redisTemplate.opsForHash().get(roomKey, "p1_lang");
            String p2Lang = (String) redisTemplate.opsForHash().get(roomKey, "p2_lang");
            String mapDataJson = (String) redisTemplate.opsForHash().get(roomKey, "mapData"); // 저장해둔 맵 데이터

            // DB 저장을 위해 ID도 가져옴
            String p1Id = (String) redisTemplate.opsForHash().get(roomKey, "p1");
            String p2Id = (String) redisTemplate.opsForHash().get(roomKey, "p2");

            // Docker 엔진 실행 (LandGrabService)
            Map<String, Object> result = landGrabService.runPvPMatch(matchId, p1Code, p1Lang, p2Code, p2Lang, mapDataJson);
            result.put("type", "RESULT");

            // [DB 저장] MatchService 호출 (정상 종료)
            try {
                matchService.savePvPMatchResult(
                        matchId,
                        Long.parseLong(p1Id),
                        Long.parseLong(p2Id),
                        result,
                        p1Code, p1Lang, p2Code, p2Lang
                );
                log.info("✅ Match result saved to DB for match {}", matchId);
            } catch (Exception e) {
                log.error("❌ Failed to save match result to DB: {}", e.getMessage());
                // 저장 실패해도 결과 전달을 위해 전송 진행
            }

            //  결과 전송 (양쪽 유저에게 전송)
            messagingTemplate.convertAndSend("/topic/game/" + matchId, result);

            // 방 정리
            cleanupMatch(matchId, p1Id, p2Id);

        } catch (Exception e) {
            log.error("🔥 PvP Execution Error: {}", e.getMessage());
            messagingTemplate.convertAndSend("/topic/game/" + matchId, Map.of(
                    "type", "ERROR",
                    "error", "Execution Failed: " + e.getMessage()
            ));

            String p1Id = (String) redisTemplate.opsForHash().get(roomKey, "p1");
            String p2Id = (String) redisTemplate.opsForHash().get(roomKey, "p2");
            // 에러 시에도 방 정리
            cleanupMatch(matchId, p1Id, p2Id);
        }
    }

    /**
     * [비정상 종료] 탈주(Disconnect) 처리
     */
    public void handleDisconnection(String matchId, String disconnectedUserId) {
        String roomKey = "match_room:" + matchId;

        if (!Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(roomKey, "p1"))) {
            return;
        }

        String p1Id = (String) redisTemplate.opsForHash().get(roomKey, "p1");
        String p2Id = (String) redisTemplate.opsForHash().get(roomKey, "p2");

        // 코드 정보 조회 (제출 전 탈주 시 null)
        String p1Code = (String) redisTemplate.opsForHash().get(roomKey, "p1_code");
        String p1Lang = (String) redisTemplate.opsForHash().get(roomKey, "p1_lang");
        String p2Code = (String) redisTemplate.opsForHash().get(roomKey, "p2_code");
        String p2Lang = (String) redisTemplate.opsForHash().get(roomKey, "p2_lang");

        String winnerRole = disconnectedUserId.equals(p1Id) ? "p2" : "p1";

        Map<String, Object> result = new HashMap<>();
        result.put("type", "RESULT");
        result.put("winner", winnerRole);
        result.put("reason", "OPPONENT_DISCONNECTED");
        result.put("logs", null);
        result.put("final_scores", Map.of("p1", 0, "p2", 0)); // 기본 점수

        // [DB 저장] 기권패 기록
        try {
            matchService.savePvPMatchResult(
                    matchId,
                    Long.parseLong(p1Id),
                    Long.parseLong(p2Id),
                    result,
                    p1Code, p1Lang, p2Code, p2Lang
            );
        } catch (Exception e) {
            log.error("❌ Failed to save disconnect result: {}", e.getMessage());
        }

        messagingTemplate.convertAndSend("/topic/game/" + matchId, result);
        cleanupMatch(matchId, p1Id, p2Id);
    }

    public void registerGameSession(String matchId, String sessionId) {
        // 세션이 끊기면 해당 매치 기권패
        redisTemplate.opsForValue().set("socket_game:" + sessionId, matchId);
    }

    // 방 정리 헬퍼 메서드
    private void cleanupMatch(String matchId, String p1Id, String p2Id) {
        String roomKey = "match_room:" + matchId;
        redisTemplate.delete(roomKey);
        log.info("Match room {} cleaned up.", matchId);
    }
}