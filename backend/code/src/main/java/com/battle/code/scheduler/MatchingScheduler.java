package com.battle.code.scheduler;

import com.battle.code.service.LandGrabService;
import com.battle.code.service.MatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingScheduler {

    private final MatchingService matchingService;
    private final LandGrabService landGrabService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private static final List<String> TARGET_GAMES = List.of("land_grab"); // 추가 예정

    @Scheduled(fixedDelay = 1000)
    public void checkMatchQueue() {
        // 등록된 모든 게임 타입에 대해 매칭 시도
        for (String gameType : TARGET_GAMES) {
            processMatching(gameType);
        }
    }

    private void processMatching(String gameType) {
        Long size = matchingService.getQueueSize(gameType);

        if (size != null && size >= 2) {
            log.debug("Matching users for [{}]... Queue Size: {}", gameType, size);

            // 유저 꺼내기 (gameType 구분)
            ZSetOperations.TypedTuple<Object> player1 = matchingService.popUserWithScore(gameType);
            ZSetOperations.TypedTuple<Object> player2 = matchingService.popUserWithScore(gameType);

            if (player1 != null && player2 != null) {
                String user1Id = (String) player1.getValue();
                String user2Id = (String) player2.getValue();
                String matchId = UUID.randomUUID().toString();

                try {
                    // 맵 생성
                    Map<String, Object> mapData = new HashMap<>();
                    if ("land_grab".equals(gameType)) {
                        mapData = generateValidLandGrabMap();

                        if (mapData == null) {
                            log.error("Failed to generate map for match {}", matchId);
                            matchingService.returnToQueue(gameType, (String)player1.getValue(), player1.getScore());
                            matchingService.returnToQueue(gameType, (String)player2.getValue(), player2.getScore());
                            return;
                        }

                    } else {
                        // snake 등 다른 게임 맵 생성 로직
                    }

                    String mapJson = objectMapper.writeValueAsString(mapData);

                    // Redis 방 생성
                    matchingService.createMatchRoom(matchId, gameType, user1Id, user2Id, mapJson);

                    // p1에게 전송
                    MatchSuccessEvent eventP1 = new MatchSuccessEvent(matchId, user1Id, user2Id, mapData, "p1");
                    messagingTemplate.convertAndSend("/topic/match/" + user1Id, eventP1);

                    // P2에게 전송
                    MatchSuccessEvent eventP2 = new MatchSuccessEvent(matchId, user1Id, user2Id, mapData, "p2");
                    messagingTemplate.convertAndSend("/topic/match/" + user2Id, eventP2);

                    log.info("Match Found! Game: {}, ID: {}", gameType, matchId);

                } catch (Exception e) {
                    log.error("Error during match creation: {}", e.getMessage());
                    // 에러 시 롤백
                    matchingService.returnToQueue(gameType, user1Id, player1.getScore());
                    matchingService.returnToQueue(gameType, user2Id, player2.getScore());
                }

            } else {
                // 롤백 (gameType 전달)
                if (player1 != null) matchingService.returnToQueue(gameType, (String) player1.getValue(), player1.getScore());
                if (player2 != null) matchingService.returnToQueue(gameType, (String) player2.getValue(), player2.getScore());
            }
        }
    }

    private Map<String, Object> generateValidLandGrabMap() {
        for (int i = 0; i < 3; i++) {
            try {
                Map<String, Object> map = landGrabService.startMatch();
                if (map != null && map.containsKey("walls") && map.containsKey("coins")) {
                    List<?> walls = (List<?>) map.get("walls");
                    if (!walls.isEmpty()) return map;
                }
            } catch (Exception e) {
                log.warn("⚠️ Map generation failed (attempt {}): {}", i+1, e.getMessage());
            }
        }
        return null;
    }

    public record MatchSuccessEvent(String matchId, String p1Id, String p2Id, Map<String, Object> mapData, String myRole) {}
}