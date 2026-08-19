package com.battle.code.integration;

import com.battle.code.data.SensitiveDataMaintenanceService;
import com.battle.code.data.SensitiveDataService;
import com.battle.code.domain.User;
import com.battle.code.dto.MatchExecutionResultDto;
import com.battle.code.dto.TurnLogDto;
import com.battle.code.repository.UserRepository;
import com.battle.code.service.MatchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "cca.scheduling.enabled=false",
        "cca.engine.workspace-cleanup-enabled=false",
        "cca.security.require-origin=false"
})
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "cca.run.integration", matches = "true")
class SensitiveDataIntegrationTest extends InfrastructureIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchService matchService;

    @Autowired
    private SensitiveDataService sensitiveDataService;

    @Autowired
    private SensitiveDataMaintenanceService maintenanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String matchId;
    private Long userId;

    @Test
    void mysqlStoresCiphertextAndAuditsAccessAndUserPurge() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("data-" + UUID.randomUUID())
                .nickname("Data Tester")
                .role(User.Role.USER)
                .provider("LOCAL")
                .build());
        userId = user.getId();
        matchId = UUID.randomUUID().toString();

        MatchExecutionResultDto result = new MatchExecutionResultDto(
                null,
                "p1",
                "score",
                null,
                Map.of("p1", 3, "p2", 1),
                1,
                List.of(new TurnLogDto(
                        1, null, null, List.of(), List.of(), List.of(),
                        Map.of("p1", 3, "p2", 1), 10, null
                )),
                null,
                null
        );
        matchService.saveMatchResult(
                userId, matchId, result, "secret strategy", "python", "easy",
                "{\"walls\":[],\"coins\":[]}"
        );

        List<String> codeRows = jdbcTemplate.queryForList(
                "SELECT mp.submitted_code FROM match_player mp " +
                        "JOIN game_match gm ON gm.id = mp.game_match_id " +
                        "WHERE gm.match_uuid = ? ORDER BY mp.player_index",
                String.class,
                matchId
        );
        String replayRow = jdbcTemplate.queryForObject(
                "SELECT mr.full_log FROM match_replay mr " +
                        "JOIN game_match gm ON gm.id = mr.game_match_id " +
                        "WHERE gm.match_uuid = ?",
                String.class,
                matchId
        );
        assertThat(codeRows).hasSize(2).allSatisfy(value ->
                assertThat(value).startsWith("cca:v1:").doesNotContain("secret strategy")
        );
        assertThat(replayRow).startsWith("cca:v1:").doesNotContain("\"turn\":1");

        jdbcTemplate.update(
                "UPDATE match_player mp JOIN game_match gm ON gm.id = mp.game_match_id " +
                        "SET mp.submitted_code = 'legacy strategy' " +
                        "WHERE gm.match_uuid = ? AND mp.player_index = 'p1'",
                matchId
        );
        jdbcTemplate.update(
                "UPDATE match_replay mr JOIN game_match gm ON gm.id = mr.game_match_id " +
                        "SET mr.full_log = '[{\"turn\":99}]' WHERE gm.match_uuid = ?",
                matchId
        );
        assertThat(maintenanceService.migrateLegacyBatch()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT mp.submitted_code FROM match_player mp " +
                        "JOIN game_match gm ON gm.id = mp.game_match_id " +
                        "WHERE gm.match_uuid = ? AND mp.player_index = 'p1'",
                String.class,
                matchId
        )).startsWith("cca:v1:").doesNotContain("legacy strategy");

        assertThat(sensitiveDataService.readSubmittedCode(matchId, "p1", "integration:test"))
                .contains("legacy strategy");
        assertThat(sensitiveDataService.readReplay(matchId, "integration:test"))
                .hasValueSatisfying(value -> assertThat(value).contains("\"turn\":99"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sensitive_data_audit " +
                        "WHERE match_uuid = ? AND action = 'READ' AND actor = 'integration:test'",
                Integer.class,
                matchId
        )).isEqualTo(2);

        SensitiveDataService.PurgeResult purged =
                sensitiveDataService.purgeForUser(matchId, userId);
        assertThat(purged.submittedCodes()).isEqualTo(1);
        assertThat(purged.replays()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_player mp " +
                        "JOIN game_match gm ON gm.id = mp.game_match_id " +
                        "WHERE gm.match_uuid = ? AND mp.user_id = ? " +
                        "AND mp.submitted_code IS NULL AND mp.submitted_code_purged_at IS NOT NULL",
                Integer.class,
                matchId,
                userId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_replay mr " +
                        "JOIN game_match gm ON gm.id = mr.game_match_id " +
                        "WHERE gm.match_uuid = ?",
                Integer.class,
                matchId
        )).isZero();
    }

    @AfterEach
    void cleanTestData() {
        if (matchId != null) {
            jdbcTemplate.update("DELETE FROM sensitive_data_audit WHERE match_uuid = ?", matchId);
            jdbcTemplate.update(
                    "DELETE mr FROM match_replay mr JOIN game_match gm ON gm.id = mr.game_match_id " +
                            "WHERE gm.match_uuid = ?",
                    matchId
            );
            jdbcTemplate.update(
                    "DELETE mp FROM match_player mp JOIN game_match gm ON gm.id = mp.game_match_id " +
                            "WHERE gm.match_uuid = ?",
                    matchId
            );
            jdbcTemplate.update("DELETE FROM game_match WHERE match_uuid = ?", matchId);
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}
