package com.battle.code.service;

import com.battle.code.domain.GameMatch;
import com.battle.code.domain.MatchExecutionResult;
import com.battle.code.domain.User;
import com.battle.code.observability.MatchTelemetry;
import com.battle.code.repository.GameMatchRepository;
import com.battle.code.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class MatchService {

    private final GameMatchRepository matchRepository;
    private final UserRepository userRepository;
    private final MatchTelemetry telemetry;
    private final MatchPersistenceMapper persistenceMapper;

    @Autowired
    public MatchService(
            GameMatchRepository matchRepository,
            UserRepository userRepository,
            MatchTelemetry telemetry,
            MatchPersistenceMapper persistenceMapper
    ) {
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.telemetry = telemetry;
        this.persistenceMapper = persistenceMapper;
    }

    public void savePvPMatchResult(String matchId, Long p1Id, Long p2Id, MatchExecutionResult result,
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
        GameMatch match = persistenceMapper.toAggregate(
                matchId,
                "PVP",
                result,
                mapDataJson,
                List.of(
                        new MatchPersistenceMapper.Participant(p1User, "p1", p1Lang, p1Code),
                        new MatchPersistenceMapper.Participant(p2User, "p2", p2Lang, p2Code)
                )
        );
        if (persistOnce(match)) {
            log.info("PvP match saved. matchId={}, winner={}", matchId, result.winner());
        }
    }

    public void saveMatchResult(Long userId, String matchId, MatchExecutionResult result,
                                String userCode, String language, String difficulty,
                                String mapDataJson) {
        if (hasSystemError(result)) {
            log.warn("System error detected for match {}. Result was not saved.", matchId);
            return;
        }
        if (alreadySaved(matchId)) return;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        GameMatch match = persistenceMapper.toAggregate(
                matchId,
                "AI",
                result,
                mapDataJson,
                List.of(
                        new MatchPersistenceMapper.Participant(user, "p1", language, userCode),
                        new MatchPersistenceMapper.Participant(
                                null, "p2", "python", "AI-" + difficulty.toUpperCase()
                        )
                )
        );
        if (persistOnce(match)) {
            log.info("AI match saved. matchId={}, winner={}", matchId, result.winner());
        }
    }

    private boolean alreadySaved(String matchId) {
        if (!matchRepository.existsByMatchUuid(matchId)) return false;
        log.info("Match persistence skipped because it already exists. matchId={}", matchId);
        return true;
    }

    private boolean persistOnce(GameMatch match) {
        long startedAt = System.nanoTime();
        String outcome = "failure";
        try {
            matchRepository.saveAndFlush(match);
            outcome = "success";
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (matchRepository.existsByMatchUuid(match.getMatchUuid())) {
                outcome = "duplicate";
                log.info("Concurrent duplicate match persistence skipped. matchId={}", match.getMatchUuid());
                return false;
            }
            throw exception;
        } finally {
            telemetry.persistence(
                    match.getMode(),
                    outcome,
                    Duration.ofNanos(Math.max(0, System.nanoTime() - startedAt))
            );
        }
    }

    private boolean hasSystemError(MatchExecutionResult result) {
        return result.hasSystemError();
    }
}
