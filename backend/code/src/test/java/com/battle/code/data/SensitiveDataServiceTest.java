package com.battle.code.data;

import com.battle.code.domain.GameMatch;
import com.battle.code.domain.MatchPlayer;
import com.battle.code.domain.MatchReplay;
import com.battle.code.domain.User;
import com.battle.code.repository.GameMatchRepository;
import com.battle.code.repository.MatchPlayerRepository;
import com.battle.code.repository.MatchReplayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveDataServiceTest {

    private SensitivePayloadCipher cipher;
    private SensitiveDataProperties properties;
    private SensitiveDataAuditService auditService;
    private GameMatchRepository matchRepository;
    private MatchPlayerRepository playerRepository;
    private MatchReplayRepository replayRepository;
    private SensitiveDataService service;

    @BeforeEach
    void setUp() {
        properties = new SensitiveDataProperties();
        cipher = new SensitivePayloadCipher(properties);
        auditService = mock(SensitiveDataAuditService.class);
        matchRepository = mock(GameMatchRepository.class);
        playerRepository = mock(MatchPlayerRepository.class);
        replayRepository = mock(MatchReplayRepository.class);
        service = new SensitiveDataService(
                cipher, properties, auditService,
                matchRepository, playerRepository, replayRepository
        );
    }

    @Test
    void decryptsThroughAnAuditedAccessBoundary() {
        String envelope = service.protectSubmittedCode("match-1", "p1", "secret code");
        MatchPlayer player = MatchPlayer.builder().submittedCode(envelope).build();
        when(playerRepository.findSensitiveCode("match-1", "p1"))
                .thenReturn(Optional.of(player));

        assertThat(service.readSubmittedCode("match-1", "p1", "admin:7"))
                .contains("secret code");
        verify(auditService).recordAccess(
                "READ", SensitiveDataAuditService.SUBMITTED_CODE,
                "admin:7", "match-1", "SUCCESS", "p1"
        );
    }

    @Test
    void participantCanImmediatelyPurgeOwnCodeAndSharedReplay() {
        User user = User.builder().id(7L).build();
        GameMatch match = GameMatch.builder().matchUuid("match-1").build();
        match.addPlayer(MatchPlayer.builder()
                .user(user)
                .playerIndex("p1")
                .submittedCode("encrypted")
                .build());
        match.setReplay(MatchReplay.builder().fullLog("encrypted").build());
        when(matchRepository.findWithSensitiveData("match-1")).thenReturn(Optional.of(match));

        SensitiveDataService.PurgeResult result = service.purgeForUser("match-1", 7L);

        assertThat(result.submittedCodes()).isEqualTo(1);
        assertThat(result.replays()).isEqualTo(1);
        assertThat(match.getPlayers().getFirst().getSubmittedCode()).isNull();
        assertThat(match.getPlayers().getFirst().getSubmittedCodePurgedAt()).isNotNull();
        assertThat(match.getReplay()).isNull();
        verify(auditService).recordMutation(
                "PURGE", SensitiveDataAuditService.SUBMITTED_CODE,
                "user:7", "match-1", 1, "USER_REQUEST"
        );
        verify(auditService).recordMutation(
                "PURGE", SensitiveDataAuditService.REPLAY,
                "user:7", "match-1", 1, "USER_REQUEST"
        );
    }

    @Test
    void nonParticipantCannotPurgeMatchData() {
        GameMatch match = GameMatch.builder().matchUuid("match-1").build();
        match.addPlayer(MatchPlayer.builder()
                .user(User.builder().id(8L).build())
                .playerIndex("p1")
                .submittedCode("encrypted")
                .build());
        when(matchRepository.findWithSensitiveData("match-1")).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> service.purgeForUser("match-1", 7L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void enforcesUtf8ByteLimitsBeforeEncryption() {
        properties.setSubmittedCodeMaxBytes(3);
        properties.setReplayMaxBytes(3);

        assertThat(service.protectSubmittedCode("match-1", "p1", "가"))
                .startsWith("cca:v1:");
        assertThatThrownBy(() -> service.protectSubmittedCode("match-1", "p1", "가a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage limit");
        assertThatThrownBy(() -> service.protectReplay("match-1", "1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage limit");
    }
}
