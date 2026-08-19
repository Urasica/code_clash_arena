package com.battle.code.data;

import com.battle.code.domain.GameMatch;
import com.battle.code.domain.MatchPlayer;
import com.battle.code.domain.MatchReplay;
import com.battle.code.repository.GameMatchRepository;
import com.battle.code.repository.MatchPlayerRepository;
import com.battle.code.repository.MatchReplayRepository;
import com.battle.code.repository.SensitiveDataAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveDataMaintenanceServiceTest {

    private SensitivePayloadCipher cipher;
    private SensitiveDataAuditService auditService;
    private GameMatchRepository matchRepository;
    private MatchPlayerRepository playerRepository;
    private MatchReplayRepository replayRepository;
    private SensitiveDataAuditRepository auditRepository;
    private SensitiveDataMaintenanceService service;

    @BeforeEach
    void setUp() {
        SensitiveDataProperties properties = new SensitiveDataProperties();
        cipher = new SensitivePayloadCipher(properties);
        auditService = mock(SensitiveDataAuditService.class);
        matchRepository = mock(GameMatchRepository.class);
        playerRepository = mock(MatchPlayerRepository.class);
        replayRepository = mock(MatchReplayRepository.class);
        auditRepository = mock(SensitiveDataAuditRepository.class);
        service = new SensitiveDataMaintenanceService(
                cipher,
                properties,
                auditService,
                matchRepository,
                playerRepository,
                replayRepository,
                auditRepository
        );
    }

    @Test
    void encryptsLegacyPlaintextWithMatchBoundAssociatedData() {
        GameMatch match = GameMatch.builder().matchUuid("match-1").build();
        MatchPlayer player = MatchPlayer.builder()
                .gameMatch(match)
                .playerIndex("p1")
                .submittedCode("legacy code")
                .build();
        MatchReplay replay = MatchReplay.builder()
                .gameMatch(match)
                .fullLog("legacy replay")
                .build();
        when(playerRepository.findLegacyPlaintext(any(Pageable.class)))
                .thenReturn(List.of(player));
        when(replayRepository.findLegacyPlaintext(any(Pageable.class)))
                .thenReturn(List.of(replay));

        assertThat(service.migrateLegacyBatch()).isEqualTo(2);
        assertThat(cipher.decrypt(
                player.getSubmittedCode(), SensitiveDataService.codeAad("match-1", "p1")
        )).isEqualTo("legacy code");
        assertThat(cipher.decrypt(
                replay.getFullLog(), SensitiveDataService.replayAad("match-1")
        )).isEqualTo("legacy replay");
        verify(auditService).recordMutation(
                "ENCRYPT_LEGACY", SensitiveDataAuditService.SUBMITTED_CODE,
                "system:data-lifecycle", null, 1, "V3_MIGRATION"
        );
        verify(auditService).recordMutation(
                "ENCRYPT_LEGACY", SensitiveDataAuditService.REPLAY,
                "system:data-lifecycle", null, 1, "V3_MIGRATION"
        );
    }

    @Test
    void purgesExpiredAndOverCapacityPayloadsInBatches() {
        MatchPlayer expiredPlayer = MatchPlayer.builder().submittedCode("encrypted").build();
        MatchReplay expiredReplay = MatchReplay.builder().fullLog("encrypted").build();
        when(playerRepository.findExpired(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(expiredPlayer));
        when(replayRepository.findExpired(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(expiredReplay));

        SensitiveDataMaintenanceService.PurgeResult expiredCodes =
                service.purgeExpiredCodeBatch(LocalDateTime.now());
        SensitiveDataMaintenanceService.PurgeResult expiredReplays =
                service.purgeExpiredReplayBatch(LocalDateTime.now());

        assertThat(expiredCodes.submittedCodes()).isEqualTo(1);
        assertThat(expiredPlayer.getSubmittedCode()).isNull();
        assertThat(expiredPlayer.getSubmittedCodePurgedAt()).isNotNull();
        assertThat(expiredReplays.replays()).isEqualTo(1);
        verify(replayRepository).deleteAll(List.of(expiredReplay));

        MatchPlayer capacityPlayer = MatchPlayer.builder().submittedCode("encrypted").build();
        MatchReplay capacityReplay = MatchReplay.builder().fullLog("encrypted").build();
        when(playerRepository.findBeyondCapacity(
                org.mockito.ArgumentMatchers.eq(42L), any(Pageable.class)
        )).thenReturn(List.of(capacityPlayer));
        when(replayRepository.findBeyondCapacity(
                org.mockito.ArgumentMatchers.eq(42L), any(Pageable.class)
        )).thenReturn(List.of(capacityReplay));

        SensitiveDataMaintenanceService.PurgeResult capacity = service.purgeCapacityBatch(42L);

        assertThat(capacity.total()).isEqualTo(2);
        assertThat(capacityPlayer.getSubmittedCode()).isNull();
        verify(replayRepository).deleteAll(List.of(capacityReplay));
    }

    @Test
    void derivesCapacityBoundaryAndPrunesAuditTtlBeforeCountLimit() {
        when(matchRepository.findIdsDescending(any(Pageable.class))).thenReturn(List.of(42L));
        LocalDateTime cutoff = LocalDateTime.now();
        when(auditRepository.deleteOccurredBefore(cutoff)).thenReturn(3);
        when(auditRepository.findIdsDescending(any(Pageable.class))).thenReturn(List.of(20L));
        when(auditRepository.deleteThroughId(20L)).thenReturn(4);

        assertThat(service.capacityBoundaryId()).contains(42L);
        assertThat(service.pruneAuditRecords(cutoff)).isEqualTo(7);
        verify(auditRepository).deleteOccurredBefore(cutoff);
        verify(auditRepository).deleteThroughId(20L);
    }
}
