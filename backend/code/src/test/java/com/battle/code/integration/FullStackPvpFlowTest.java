package com.battle.code.integration;

import com.battle.code.scheduler.MatchingScheduler;
import com.battle.code.service.GameSessionService;
import com.battle.code.service.MatchingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "cca.scheduling.enabled=false",
        "cca.engine.workspace-cleanup-enabled=false",
        "cca.security.require-origin=false",
        "cca.match.reconnect-grace=100ms"
})
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "cca.run.integration", matches = "true")
class FullStackPvpFlowTest {

    private static final String GAME = "land_grab";
    private static final String CODE = """
            def strategy(my_pos, coins, walls, board_size):
                return 'STAY'
            """;

    @Autowired
    private MatchingService matchingService;

    @Autowired
    private MatchingScheduler matchingScheduler;

    @Autowired
    private GameSessionService gameSessionService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> matchIds = new ArrayList<>();
    private Long p1Id;
    private Long p2Id;

    @Test
    @Timeout(90)
    void twoPlayersCancelMatchSubmitOnceAndDisconnectSafely() throws Exception {
        createPlayers();

        matchingService.joinQueue(GAME, p1Id);
        assertThat(redisTemplate.opsForZSet().score(queueKey(), p1Id.toString())).isNotNull();
        matchingService.cancelQueue(GAME, p1Id);
        assertThat(redisTemplate.opsForZSet().score(queueKey(), p1Id.toString())).isNull();

        String executedMatch = createMatch();
        assertThat(gameSessionService.registerGameSession(executedMatch, "p1-main", p1Id.toString())).isTrue();
        assertThat(gameSessionService.registerGameSession(executedMatch, "p2-main", p2Id.toString())).isTrue();

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> gameSessionService.handleCodeSubmission(
                        executedMatch, p1Id, CODE, "python"
                )),
                CompletableFuture.runAsync(() -> gameSessionService.handleCodeSubmission(
                        executedMatch, p2Id, CODE, "python"
                ))
        ).join();

        await(() -> savedMatchCount(executedMatch) == 1, Duration.ofSeconds(45));
        assertStoredAggregate(executedMatch);
        assertMatchKeysRemoved(executedMatch);

        String disconnectedMatch = createMatch();
        assertThat(gameSessionService.registerGameSession(
                disconnectedMatch, "p1-tab-a", p1Id.toString()
        )).isTrue();
        assertThat(gameSessionService.registerGameSession(
                disconnectedMatch, "p1-tab-b", p1Id.toString()
        )).isTrue();
        assertThat(gameSessionService.registerGameSession(
                disconnectedMatch, "p2-tab-a", p2Id.toString()
        )).isTrue();

        gameSessionService.handleSocketDisconnection(disconnectedMatch, p1Id.toString(), "p1-tab-a");
        assertThat(redisTemplate.hasKey("match_room:" + disconnectedMatch)).isTrue();
        assertThat(savedMatchCount(disconnectedMatch)).isZero();

        gameSessionService.handleSocketDisconnection(disconnectedMatch, p1Id.toString(), "p1-tab-b");
        await(() -> savedMatchCount(disconnectedMatch) == 1, Duration.ofSeconds(5));
        assertStoredAggregate(disconnectedMatch);
        assertMatchKeysRemoved(disconnectedMatch);
    }

    private String createMatch() {
        matchingService.joinQueue(GAME, p1Id);
        matchingService.joinQueue(GAME, p2Id);
        matchingScheduler.checkMatchQueue();
        Object p1Match = redisTemplate.opsForValue().get("user_session:" + p1Id);
        Object p2Match = redisTemplate.opsForValue().get("user_session:" + p2Id);
        assertThat(p1Match).isNotNull();
        assertThat(p2Match).isEqualTo(p1Match);
        String matchId = String.valueOf(p1Match);
        matchIds.add(matchId);
        return matchId;
    }

    private void createPlayers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String p1Name = "pvp1_" + suffix;
        String p2Name = "pvp2_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO users (username, nickname, role, provider, created_at) " +
                        "VALUES (?, ?, 'USER', 'LOCAL', NOW())",
                p1Name, "P1"
        );
        jdbcTemplate.update(
                "INSERT INTO users (username, nickname, role, provider, created_at) " +
                        "VALUES (?, ?, 'USER', 'LOCAL', NOW())",
                p2Name, "P2"
        );
        p1Id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, p1Name);
        p2Id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, p2Name);
        clearPlayerRedisState(p1Id);
        clearPlayerRedisState(p2Id);
    }

    private void clearPlayerRedisState(Long userId) {
        redisTemplate.opsForZSet().remove(queueKey(), userId.toString());
        redisTemplate.delete(List.of(
                "user_session:" + userId,
                "match_reservation:" + userId
        ));
    }

    private void assertStoredAggregate(String matchId) {
        assertThat(savedMatchCount(matchId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_player mp JOIN game_match gm ON gm.id = mp.game_match_id " +
                        "WHERE gm.match_uuid = ?",
                Integer.class, matchId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_match WHERE match_uuid = ? AND map_data IS NOT NULL",
                Integer.class, matchId
        )).isEqualTo(1);
    }

    private void assertMatchKeysRemoved(String matchId) {
        assertThat(redisTemplate.hasKey("match_room:" + matchId)).isFalse();
        assertThat(redisTemplate.hasKey("user_session:" + p1Id)).isFalse();
        assertThat(redisTemplate.hasKey("user_session:" + p2Id)).isFalse();
        assertThat(redisTemplate.hasKey("match_sockets:" + matchId + ":" + p1Id)).isFalse();
        assertThat(redisTemplate.hasKey("match_sockets:" + matchId + ":" + p2Id)).isFalse();
    }

    private int savedMatchCount(String matchId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_match WHERE match_uuid = ?", Integer.class, matchId
        );
    }

    private void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @AfterEach
    void cleanTestData() {
        for (String matchId : matchIds) {
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
            redisTemplate.delete(List.of(
                    "match_room:" + matchId,
                    "match_sockets:" + matchId + ":" + p1Id,
                    "match_sockets:" + matchId + ":" + p2Id
            ));
        }
        if (p1Id != null) {
            matchingService.cancelQueue(GAME, p1Id);
            redisTemplate.delete(List.of(
                    "user_session:" + p1Id,
                    "match_reservation:" + p1Id,
                    "socket_game:p1-main",
                    "socket_game:p1-tab-a",
                    "socket_game:p1-tab-b"
            ));
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", p1Id);
        }
        if (p2Id != null) {
            matchingService.cancelQueue(GAME, p2Id);
            redisTemplate.delete(List.of(
                    "user_session:" + p2Id,
                    "match_reservation:" + p2Id,
                    "socket_game:p2-main",
                    "socket_game:p2-tab-a"
            ));
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", p2Id);
        }
    }

    private String queueKey() {
        return "match_queue:" + GAME;
    }
}
