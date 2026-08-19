package com.battle.code.service;

import com.battle.code.domain.GameMatch;
import com.battle.code.domain.User;
import com.battle.code.data.SensitiveDataAuditService;
import com.battle.code.data.SensitiveDataProperties;
import com.battle.code.data.SensitiveDataService;
import com.battle.code.data.SensitivePayloadCipher;
import com.battle.code.dto.MatchExecutionResultDto;
import com.battle.code.observability.MatchTelemetry;
import com.battle.code.repository.GameMatchRepository;
import com.battle.code.repository.MatchPlayerRepository;
import com.battle.code.repository.MatchReplayRepository;
import com.battle.code.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchServiceTest {

    private GameMatchRepository matchRepository;
    private UserRepository userRepository;
    private MatchService service;

    @BeforeEach
    void setUp() {
        matchRepository = mock(GameMatchRepository.class);
        userRepository = mock(UserRepository.class);
        SensitiveDataProperties properties = new SensitiveDataProperties();
        SensitiveDataService sensitiveDataService = new SensitiveDataService(
                new SensitivePayloadCipher(properties),
                properties,
                mock(SensitiveDataAuditService.class),
                matchRepository,
                mock(MatchPlayerRepository.class),
                mock(MatchReplayRepository.class)
        );
        service = new MatchService(
                matchRepository,
                userRepository,
                new ObjectMapper(),
                MatchTelemetry.noOp(),
                sensitiveDataService
        );
    }

    @Test
    void aiResultPersistsMapPlayersAndReplayAsOneAggregate() {
        User user = User.builder().id(7L).username("player").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        MatchExecutionResultDto result = result();

        service.saveMatchResult(
                7L, "match-1", result, "code", "python", "easy",
                "{\"walls\":[],\"coins\":[]}"
        );

        ArgumentCaptor<GameMatch> captor = ArgumentCaptor.forClass(GameMatch.class);
        verify(matchRepository).saveAndFlush(captor.capture());
        GameMatch saved = captor.getValue();
        assertThat(saved.getMapData()).isEqualTo("{\"walls\":[],\"coins\":[]}");
        assertThat(saved.getPlayers()).hasSize(2);
        assertThat(saved.getPlayers())
                .allSatisfy(player -> assertThat(player.getSubmittedCode())
                        .startsWith("cca:v1:")
                        .doesNotContain("code"));
        assertThat(saved.getReplay()).isNotNull();
        assertThat(saved.getReplay().getFullLog()).startsWith("cca:v1:");
        assertThat(saved.getReplay().getGameMatch()).isSameAs(saved);
    }

    @Test
    void existingMatchUuidIsAnIdempotentNoOp() {
        when(matchRepository.existsByMatchUuid("match-1")).thenReturn(true);

        service.saveMatchResult(
                7L, "match-1", result(), "code", "python", "easy",
                "{\"walls\":[],\"coins\":[]}"
        );

        verify(userRepository, never()).findById(7L);
        verify(matchRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void uniqueConstraintRaceIsAlsoAnIdempotentNoOp() {
        when(matchRepository.existsByMatchUuid("match-1")).thenReturn(false, true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).build()));
        when(matchRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatCode(() -> service.saveMatchResult(
                7L, "match-1", result(), "code", "python", "easy",
                "{\"walls\":[],\"coins\":[]}"
        )).doesNotThrowAnyException();
    }

    private MatchExecutionResultDto result() {
        return new MatchExecutionResultDto(
                null, "p1", "score", null, Map.of("p1", 3, "p2", 1), 1,
                List.of(), null, null
        );
    }
}
