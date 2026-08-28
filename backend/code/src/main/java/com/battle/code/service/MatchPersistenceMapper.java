package com.battle.code.service;

import com.battle.code.data.SensitiveDataService;
import com.battle.code.domain.GameMatch;
import com.battle.code.domain.MatchExecutionResult;
import com.battle.code.domain.MatchPlayer;
import com.battle.code.domain.MatchReplay;
import com.battle.code.domain.User;
import com.battle.code.execution.EngineExecutionMetadata;
import com.battle.code.execution.EngineMetadataProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MatchPersistenceMapper {

    private final ObjectMapper objectMapper;
    private final SensitiveDataService sensitiveDataService;
    private final EngineMetadataProvider engineMetadataProvider;

    public GameMatch toAggregate(
            String matchId,
            String mode,
            MatchExecutionResult result,
            String mapDataJson,
            List<Participant> participants
    ) {
        if (mapDataJson == null || mapDataJson.isBlank()) {
            throw new IllegalArgumentException("Match map data is required.");
        }
        EngineExecutionMetadata metadata = result.executionMetadata() == null
                ? engineMetadataProvider.currentMetadata()
                : result.executionMetadata();
        GameMatch match = GameMatch.builder()
                .matchUuid(matchId)
                .gameType("LAND_GRAB")
                .mode(mode)
                .resultReason(result.reason())
                .engineDigest(metadata.engineDigest())
                .enginePolicyVersion(metadata.policyVersion())
                .mapData(mapDataJson)
                .build();
        participants.forEach(participant -> match.addPlayer(toPlayer(matchId, result, participant)));
        attachReplay(match, matchId, result);
        return match;
    }

    private MatchPlayer toPlayer(
            String matchId,
            MatchExecutionResult result,
            Participant participant
    ) {
        return MatchPlayer.builder()
                .user(participant.user())
                .playerIndex(participant.playerIndex())
                .result(result.outcomeFor(participant.playerIndex()))
                .score(result.scoreFor(participant.playerIndex()))
                .language(participant.language() == null || participant.language().isBlank()
                        ? "unknown"
                        : participant.language())
                .submittedCode(sensitiveDataService.protectSubmittedCode(
                        matchId, participant.playerIndex(), participant.code()
                ))
                .build();
    }

    private void attachReplay(GameMatch match, String matchId, MatchExecutionResult result) {
        if (result.logs() == null) {
            return;
        }
        try {
            match.setReplay(MatchReplay.builder()
                    .fullLog(sensitiveDataService.protectReplay(
                            matchId,
                            objectMapper.writeValueAsString(result.logs())
                    ))
                    .build());
        } catch (Exception exception) {
            log.warn("Could not serialize replay for match {}", matchId, exception);
        }
    }

    public record Participant(
            User user,
            String playerIndex,
            String language,
            String code
    ) {
    }
}
