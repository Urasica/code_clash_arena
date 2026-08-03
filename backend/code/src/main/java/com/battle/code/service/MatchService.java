package com.battle.code.service;

import com.battle.code.domain.GameMatch;
import com.battle.code.domain.MatchPlayer;
import com.battle.code.domain.MatchReplay;
import com.battle.code.domain.User;
import com.battle.code.dto.MatchExecutionResultDto;
import com.battle.code.repository.GameMatchRepository;
import com.battle.code.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchService {

    private final GameMatchRepository matchRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public void savePvPMatchResult(String matchId, Long p1Id, Long p2Id, MatchExecutionResultDto result,
                                   String p1Code, String p1Lang, String p2Code, String p2Lang,
                                   String mapDataJson) {
        if (hasSystemError(result)) {
            log.warn("System error detected for match {}. Result was not saved.", matchId);
            return;
        }
        if (alreadySaved(matchId)) return;

        User p1User = userRepository.findById(p1Id)
                .orElseThrow(() -> new IllegalArgumentException("Player 1 not found: " + p1Id));
        User p2User = userRepository.findById(p2Id)
                .orElseThrow(() -> new IllegalArgumentException("Player 2 not found: " + p2Id));
        GameMatch match = newMatch(matchId, "PVP", result, mapDataJson);
        Map<String, Integer> scores = result.finalScores();
        match.addPlayer(player(p1User, "p1", outcome("p1", result.winner(), result.p1Error()),
                score(scores, "p1"), p1Lang, p1Code));
        match.addPlayer(player(p2User, "p2", outcome("p2", result.winner(), result.p2Error()),
                score(scores, "p2"), p2Lang, p2Code));
        if (persistOnce(match)) {
            log.info("PvP match saved. matchId={}, winner={}", matchId, result.winner());
        }
    }

    public void saveMatchResult(Long userId, String matchId, MatchExecutionResultDto result,
                                String userCode, String language, String difficulty,
                                String mapDataJson) {
        if (hasSystemError(result)) {
            log.warn("System error detected for match {}. Result was not saved.", matchId);
            return;
        }
        if (alreadySaved(matchId)) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        GameMatch match = newMatch(matchId, "AI", result, mapDataJson);
        Map<String, Integer> scores = result.finalScores();
        match.addPlayer(player(user, "p1", outcome("p1", result.winner(), result.p1Error()),
                score(scores, "p1"), language, userCode));
        match.addPlayer(player(null, "p2", outcome("p2", result.winner(), result.p2Error()),
                score(scores, "p2"), "python", "AI-" + difficulty.toUpperCase()));
        if (persistOnce(match)) {
            log.info("AI match saved. matchId={}, winner={}", matchId, result.winner());
        }
    }

    private GameMatch newMatch(String matchId, String mode, MatchExecutionResultDto result, String mapDataJson) {
        if (mapDataJson == null || mapDataJson.isBlank()) {
            throw new IllegalArgumentException("Match map data is required.");
        }
        GameMatch match = GameMatch.builder()
                .matchUuid(matchId)
                .gameType("LAND_GRAB")
                .mode(mode)
                .mapData(mapDataJson)
                .build();
        if (result.logs() != null) {
            try {
                match.setReplay(MatchReplay.builder()
                        .fullLog(objectMapper.writeValueAsString(result.logs()))
                        .build());
            } catch (Exception exception) {
                log.warn("Could not serialize replay for match {}", matchId, exception);
            }
        }
        return match;
    }

    private MatchPlayer player(User user, String index, String result, int score, String language, String code) {
        return MatchPlayer.builder()
                .user(user)
                .playerIndex(index)
                .result(result)
                .score(score)
                .language(language == null || language.isBlank() ? "unknown" : language)
                .submittedCode(code == null ? "" : code)
                .build();
    }

    private boolean alreadySaved(String matchId) {
        if (!matchRepository.existsByMatchUuid(matchId)) return false;
        log.info("Match persistence skipped because it already exists. matchId={}", matchId);
        return true;
    }

    private boolean persistOnce(GameMatch match) {
        try {
            matchRepository.saveAndFlush(match);
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (matchRepository.existsByMatchUuid(match.getMatchUuid())) {
                log.info("Concurrent duplicate match persistence skipped. matchId={}", match.getMatchUuid());
                return false;
            }
            throw exception;
        }
    }

    private boolean hasSystemError(MatchExecutionResultDto result) {
        return result.error() != null && !result.error().isBlank();
    }

    private int score(Map<String, Integer> scores, String role) {
        return scores == null ? 0 : scores.getOrDefault(role, 0);
    }

    private String outcome(String role, String winner, String playerError) {
        if (playerError != null) return "LOSE";
        if ("draw".equalsIgnoreCase(winner)) return "DRAW";
        return role.equals(winner) ? "WIN" : "LOSE";
    }
}
