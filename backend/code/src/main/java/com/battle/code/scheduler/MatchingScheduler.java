package com.battle.code.scheduler;

import com.battle.code.dto.LandGrabMapDto;
import com.battle.code.dto.MatchSuccessMessage;
import com.battle.code.observability.MatchLogContext;
import com.battle.code.observability.MatchTelemetry;
import com.battle.code.service.LandGrabService;
import com.battle.code.service.MatchingService;
import com.battle.code.service.MatchingService.MatchPair;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingScheduler {

    private static final List<String> TARGET_GAMES = List.of("land_grab");

    private final MatchingService matchingService;
    private final LandGrabService landGrabService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final MatchTelemetry telemetry;

    @Scheduled(fixedDelayString = "${cca.match.queue-poll-interval:1s}")
    public void checkMatchQueue() {
        TARGET_GAMES.forEach(this::processOnePair);
    }

    private void processOnePair(String gameType) {
        String matchId = UUID.randomUUID().toString();
        matchingService.popPair(gameType, matchId)
                .ifPresent(pair -> createMatch(gameType, matchId, pair));
    }

    private void createMatch(String gameType, String matchId, MatchPair pair) {
        try (MatchLogContext.Scope ignored = MatchLogContext.open(matchId)) {
            LandGrabMapDto mapData = generateValidLandGrabMap();
            if (mapData == null) {
                telemetry.matchCreation(gameType, "map_generation_failed");
                matchingService.returnPair(gameType, pair);
                return;
            }
            matchingService.createMatchRoom(
                    matchId, gameType, pair.p1().userId(), pair.p2().userId(),
                    objectMapper.writeValueAsString(mapData)
            );
            messagingTemplate.convertAndSend(
                    "/topic/match/" + pair.p1().userId(),
                    new MatchSuccessMessage(matchId, pair.p1().userId(), pair.p2().userId(), mapData, "p1")
            );
            messagingTemplate.convertAndSend(
                    "/topic/match/" + pair.p2().userId(),
                    new MatchSuccessMessage(matchId, pair.p1().userId(), pair.p2().userId(), mapData, "p2")
            );
            telemetry.matchCreation(gameType, "success");
            log.info("Match found. game={}, matchId={}", gameType, matchId);
        } catch (Exception exception) {
            telemetry.matchCreation(gameType, "failure");
            log.error("Match creation failed for {}", matchId, exception);
            matchingService.returnPair(gameType, pair);
        }
    }

    private LandGrabMapDto generateValidLandGrabMap() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                LandGrabMapDto map = landGrabService.generateTransientMap();
                if (map != null && map.walls() != null && !map.walls().isEmpty() && map.coins() != null) {
                    return map;
                }
            } catch (Exception exception) {
                log.warn("Map generation failed on attempt {}", attempt, exception);
            }
        }
        return null;
    }
}
