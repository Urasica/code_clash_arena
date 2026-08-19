package com.battle.code.data;

import com.battle.code.domain.GameMatch;
import com.battle.code.domain.MatchPlayer;
import com.battle.code.domain.MatchReplay;
import com.battle.code.repository.GameMatchRepository;
import com.battle.code.repository.MatchPlayerRepository;
import com.battle.code.repository.MatchReplayRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class SensitiveDataService {

    private final SensitivePayloadCipher cipher;
    private final SensitiveDataProperties properties;
    private final SensitiveDataAuditService auditService;
    private final GameMatchRepository matchRepository;
    private final MatchPlayerRepository playerRepository;
    private final MatchReplayRepository replayRepository;

    public SensitiveDataService(
            SensitivePayloadCipher cipher,
            SensitiveDataProperties properties,
            SensitiveDataAuditService auditService,
            GameMatchRepository matchRepository,
            MatchPlayerRepository playerRepository,
            MatchReplayRepository replayRepository
    ) {
        this.cipher = cipher;
        this.properties = properties;
        this.auditService = auditService;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.replayRepository = replayRepository;
    }

    public String protectSubmittedCode(String matchUuid, String playerIndex, String plaintext) {
        String value = plaintext == null ? "" : plaintext;
        enforceMaxBytes(value, properties.getSubmittedCodeMaxBytes(), "Submitted code");
        return cipher.encrypt(value, codeAad(matchUuid, playerIndex));
    }

    public String protectReplay(String matchUuid, String plaintext) {
        enforceMaxBytes(plaintext, properties.getReplayMaxBytes(), "Replay");
        return cipher.encrypt(plaintext, replayAad(matchUuid));
    }

    @Transactional(readOnly = true)
    public Optional<String> readSubmittedCode(String matchUuid, String playerIndex, String actor) {
        Optional<MatchPlayer> player = playerRepository.findSensitiveCode(matchUuid, playerIndex);
        if (player.isEmpty() || player.get().getSubmittedCode() == null) {
            auditService.recordAccess(
                    "READ", SensitiveDataAuditService.SUBMITTED_CODE, actor,
                    matchUuid, "NOT_FOUND", playerIndex
            );
            return Optional.empty();
        }
        try {
            String plaintext = cipher.decrypt(
                    player.get().getSubmittedCode(), codeAad(matchUuid, playerIndex)
            );
            auditService.recordAccess(
                    "READ", SensitiveDataAuditService.SUBMITTED_CODE, actor,
                    matchUuid, "SUCCESS", playerIndex
            );
            return Optional.of(plaintext);
        } catch (RuntimeException exception) {
            auditService.recordAccess(
                    "READ", SensitiveDataAuditService.SUBMITTED_CODE, actor,
                    matchUuid, "FAILURE", playerIndex
            );
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> readReplay(String matchUuid, String actor) {
        Optional<MatchReplay> replay = replayRepository.findSensitiveReplay(matchUuid);
        if (replay.isEmpty()) {
            auditService.recordAccess(
                    "READ", SensitiveDataAuditService.REPLAY, actor,
                    matchUuid, "NOT_FOUND", null
            );
            return Optional.empty();
        }
        try {
            String plaintext = cipher.decrypt(replay.get().getFullLog(), replayAad(matchUuid));
            auditService.recordAccess(
                    "READ", SensitiveDataAuditService.REPLAY, actor,
                    matchUuid, "SUCCESS", null
            );
            return Optional.of(plaintext);
        } catch (RuntimeException exception) {
            auditService.recordAccess(
                    "READ", SensitiveDataAuditService.REPLAY, actor,
                    matchUuid, "FAILURE", null
            );
            throw exception;
        }
    }

    @Transactional
    public PurgeResult purgeForUser(String matchUuid, Long userId) {
        GameMatch match = matchRepository.findWithSensitiveData(matchUuid)
                .orElseThrow(() -> new NoSuchElementException("Match not found"));
        boolean participant = match.getPlayers().stream()
                .anyMatch(player -> player.getUser() != null && userId.equals(player.getUser().getId()));
        if (!participant) {
            throw new AccessDeniedException("The match does not belong to the authenticated user.");
        }

        LocalDateTime now = LocalDateTime.now();
        int codeCount = 0;
        for (MatchPlayer player : match.getPlayers()) {
            if (player.getUser() != null && userId.equals(player.getUser().getId())
                    && player.getSubmittedCode() != null) {
                player.setSubmittedCode(null);
                player.setSubmittedCodePurgedAt(now);
                codeCount++;
            }
        }
        int replayCount = match.getReplay() == null ? 0 : 1;
        if (replayCount > 0) {
            match.removeReplay();
        }

        String actor = "user:" + userId;
        auditService.recordMutation(
                "PURGE", SensitiveDataAuditService.SUBMITTED_CODE, actor,
                matchUuid, codeCount, "USER_REQUEST"
        );
        auditService.recordMutation(
                "PURGE", SensitiveDataAuditService.REPLAY, actor,
                matchUuid, replayCount, "USER_REQUEST"
        );
        return new PurgeResult(codeCount, replayCount);
    }

    static String codeAad(String matchUuid, String playerIndex) {
        return "match:" + matchUuid + ":player:" + playerIndex + ":submitted-code";
    }

    static String replayAad(String matchUuid) {
        return "match:" + matchUuid + ":replay";
    }

    private void enforceMaxBytes(String value, int maxBytes, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(label + " exceeds the storage limit.");
        }
    }

    public record PurgeResult(int submittedCodes, int replays) {
    }
}
