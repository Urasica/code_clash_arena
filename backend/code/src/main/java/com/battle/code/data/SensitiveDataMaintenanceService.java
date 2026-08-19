package com.battle.code.data;

import com.battle.code.domain.MatchPlayer;
import com.battle.code.domain.MatchReplay;
import com.battle.code.repository.GameMatchRepository;
import com.battle.code.repository.MatchPlayerRepository;
import com.battle.code.repository.MatchReplayRepository;
import com.battle.code.repository.SensitiveDataAuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SensitiveDataMaintenanceService {

    private static final String SYSTEM_ACTOR = "system:data-lifecycle";

    private final SensitivePayloadCipher cipher;
    private final SensitiveDataProperties properties;
    private final SensitiveDataAuditService auditService;
    private final GameMatchRepository matchRepository;
    private final MatchPlayerRepository playerRepository;
    private final MatchReplayRepository replayRepository;
    private final SensitiveDataAuditRepository auditRepository;

    public SensitiveDataMaintenanceService(
            SensitivePayloadCipher cipher,
            SensitiveDataProperties properties,
            SensitiveDataAuditService auditService,
            GameMatchRepository matchRepository,
            MatchPlayerRepository playerRepository,
            MatchReplayRepository replayRepository,
            SensitiveDataAuditRepository auditRepository
    ) {
        this.cipher = cipher;
        this.properties = properties;
        this.auditService = auditService;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.replayRepository = replayRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public int migrateLegacyBatch() {
        PageRequest batch = PageRequest.of(0, properties.getCleanupBatchSize());
        List<MatchPlayer> players = playerRepository.findLegacyPlaintext(batch);
        for (MatchPlayer player : players) {
            String matchUuid = player.getGameMatch().getMatchUuid();
            player.setSubmittedCode(cipher.encrypt(
                    player.getSubmittedCode(),
                    SensitiveDataService.codeAad(matchUuid, player.getPlayerIndex())
            ));
        }

        List<MatchReplay> replays = replayRepository.findLegacyPlaintext(batch);
        for (MatchReplay replay : replays) {
            String matchUuid = replay.getGameMatch().getMatchUuid();
            replay.setFullLog(cipher.encrypt(
                    replay.getFullLog(),
                    SensitiveDataService.replayAad(matchUuid)
            ));
        }

        auditService.recordMutation(
                "ENCRYPT_LEGACY", SensitiveDataAuditService.SUBMITTED_CODE,
                SYSTEM_ACTOR, null, players.size(), "V3_MIGRATION"
        );
        auditService.recordMutation(
                "ENCRYPT_LEGACY", SensitiveDataAuditService.REPLAY,
                SYSTEM_ACTOR, null, replays.size(), "V3_MIGRATION"
        );
        return players.size() + replays.size();
    }

    @Transactional
    public PurgeResult purgeExpiredCodeBatch(LocalDateTime cutoff) {
        List<MatchPlayer> players = playerRepository.findExpired(
                cutoff, PageRequest.of(0, properties.getCleanupBatchSize())
        );
        LocalDateTime now = LocalDateTime.now();
        players.forEach(player -> {
            player.setSubmittedCode(null);
            player.setSubmittedCodePurgedAt(now);
        });
        auditService.recordMutation(
                "PURGE", SensitiveDataAuditService.SUBMITTED_CODE,
                SYSTEM_ACTOR, null, players.size(), "RETENTION_TTL"
        );
        return new PurgeResult(players.size(), 0);
    }

    @Transactional
    public PurgeResult purgeExpiredReplayBatch(LocalDateTime cutoff) {
        List<MatchReplay> replays = replayRepository.findExpired(
                cutoff, PageRequest.of(0, properties.getCleanupBatchSize())
        );
        replayRepository.deleteAll(replays);
        auditService.recordMutation(
                "PURGE", SensitiveDataAuditService.REPLAY,
                SYSTEM_ACTOR, null, replays.size(), "RETENTION_TTL"
        );
        return new PurgeResult(0, replays.size());
    }

    @Transactional
    public PurgeResult purgeCapacityBatch(Long boundaryId) {
        if (boundaryId == null) return new PurgeResult(0, 0);
        PageRequest batch = PageRequest.of(0, properties.getCleanupBatchSize());
        List<MatchPlayer> players = playerRepository.findBeyondCapacity(boundaryId, batch);
        LocalDateTime now = LocalDateTime.now();
        players.forEach(player -> {
            player.setSubmittedCode(null);
            player.setSubmittedCodePurgedAt(now);
        });
        List<MatchReplay> replays = replayRepository.findBeyondCapacity(boundaryId, batch);
        replayRepository.deleteAll(replays);
        auditService.recordMutation(
                "PURGE", SensitiveDataAuditService.SUBMITTED_CODE,
                SYSTEM_ACTOR, null, players.size(), "CAPACITY_LIMIT"
        );
        auditService.recordMutation(
                "PURGE", SensitiveDataAuditService.REPLAY,
                SYSTEM_ACTOR, null, replays.size(), "CAPACITY_LIMIT"
        );
        return new PurgeResult(players.size(), replays.size());
    }

    @Transactional(readOnly = true)
    public Optional<Long> capacityBoundaryId() {
        return matchRepository.findIdsDescending(
                PageRequest.of(properties.getMaxRetainedMatches(), 1)
        ).stream().findFirst();
    }

    @Transactional
    public int pruneAuditRecords(LocalDateTime cutoff) {
        int deleted = auditRepository.deleteOccurredBefore(cutoff);
        Optional<Long> boundary = auditRepository.findIdsDescending(
                PageRequest.of(properties.getMaxAuditRecords(), 1)
        ).stream().findFirst();
        if (boundary.isPresent()) {
            deleted += auditRepository.deleteThroughId(boundary.get());
        }
        return deleted;
    }

    public record PurgeResult(int submittedCodes, int replays) {
        public int total() {
            return submittedCodes + replays;
        }

        public PurgeResult plus(PurgeResult other) {
            return new PurgeResult(
                    submittedCodes + other.submittedCodes,
                    replays + other.replays
            );
        }
    }
}
