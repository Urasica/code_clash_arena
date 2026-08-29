package com.battle.code.integration;

import com.battle.code.dto.CompileResultDto;
import com.battle.code.dto.MatchExecutionResultDto;
import com.battle.code.dto.StartMatchResponseDto;
import com.battle.code.execution.MatchWorkspaceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "cca.scheduling.enabled=false",
                "cca.engine.workspace-cleanup-enabled=false",
                "cca.security.require-origin=true"
        }
)
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "cca.run.integration", matches = "true")
class FullStackAiFlowTest extends InfrastructureIntegrationTest {

    private static final String ORIGIN = "http://localhost:3000";
    private static final String USER_CODE = """
            def strategy(my_pos, coins, walls, board_size):
                return 'STAY'
            """;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MatchWorkspaceManager workspaceManager;

    private final String username = "m1_" + UUID.randomUUID().toString().substring(0, 8);
    private String matchId;
    private Long userId;

    @Test
    void signupLoginAiRunPersistenceCleanupAndLogout() {
        String baseUrl = "http://localhost:" + port;

        var signup = rest.exchange(
                baseUrl + "/api/auth/signup",
                HttpMethod.POST,
                json(Map.of("username", username, "password", "m1-password", "nickname", "M1"), null),
                String.class
        );
        assertThat(signup.getStatusCode()).isEqualTo(HttpStatus.OK);

        var login = rest.exchange(
                baseUrl + "/api/auth/login",
                HttpMethod.POST,
                json(Map.of("username", username, "password", "m1-password"), null),
                Map.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        userId = ((Number) login.getBody().get("userId")).longValue();
        String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];

        var me = rest.exchange(
                baseUrl + "/api/auth/me", HttpMethod.GET, request(null, cookie), Map.class
        );
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) me.getBody().get("userId")).longValue()).isEqualTo(userId);

        var start = rest.exchange(
                baseUrl + "/api/match/land-grab/start",
                HttpMethod.POST,
                request(null, cookie),
                StartMatchResponseDto.class
        );
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
        matchId = start.getBody().matchId();
        assertThat(start.getBody().walls()).isNotNull();
        assertThat(start.getBody().coins()).isNotNull();

        Map<String, String> runRequest = Map.of(
                "matchId", matchId,
                "userCode", USER_CODE,
                "language", "python",
                "difficulty", "easy"
        );
        var compile = rest.exchange(
                baseUrl + "/api/match/land-grab/compile",
                HttpMethod.POST,
                json(runRequest, cookie),
                CompileResultDto.class
        );
        assertThat(compile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(compile.getBody().status()).isEqualToIgnoringCase("success");

        var run = rest.exchange(
                baseUrl + "/api/match/land-grab/run",
                HttpMethod.POST,
                json(runRequest, cookie),
                MatchExecutionResultDto.class
        );
        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(run.getBody().winner()).isIn("p1", "p2", "draw");
        assertThat(run.getBody().logs()).isNotEmpty();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_match WHERE match_uuid = ? AND map_data IS NOT NULL " +
                        "AND engine_digest LIKE '%sha256:%' " +
                        "AND engine_policy_version = 'm3-v1'",
                Integer.class, matchId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_player mp JOIN game_match gm ON gm.id = mp.game_match_id " +
                        "WHERE gm.match_uuid = ?",
                Integer.class, matchId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_replay mr JOIN game_match gm ON gm.id = mr.game_match_id " +
                        "WHERE gm.match_uuid = ?",
                Integer.class, matchId
        )).isEqualTo(1);
        assertThat(redisTemplate.hasKey("ai_workspace:" + matchId)).isFalse();
        assertThat(Files.exists(workspaceManager.resolve(matchId))).isFalse();

        var logout = rest.exchange(
                baseUrl + "/api/auth/logout",
                HttpMethod.POST,
                request(null, cookie),
                String.class
        );
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(rest.getForEntity(baseUrl + "/api/auth/me", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @AfterEach
    void cleanTestData() {
        if (matchId != null) {
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
            redisTemplate.delete("ai_workspace:" + matchId);
            workspaceManager.delete(workspaceManager.resolve(matchId));
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
            redisTemplate.delete(List.of(
                    "rate_limit:compile:user-" + userId,
                    "rate_limit:run:user-" + userId
            ));
        }
        redisTemplate.delete("rate_limit:login:ip-127.0.0.1");
        redisTemplate.delete("rate_limit:login:ip-0:0:0:0:0:0:0:1");
    }

    private HttpEntity<?> json(Object body, String cookie) {
        HttpHeaders headers = headers(cookie);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<?> request(Object body, String cookie) {
        return new HttpEntity<>(body, headers(cookie));
    }

    private HttpHeaders headers(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(ORIGIN);
        if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
        return headers;
    }
}
