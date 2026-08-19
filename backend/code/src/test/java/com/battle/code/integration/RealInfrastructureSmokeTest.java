package com.battle.code.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "cca.scheduling.enabled=false",
        "cca.engine.workspace-cleanup-enabled=false",
        "cca.security.require-origin=false"
})
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "cca.run.integration", matches = "true")
class RealInfrastructureSmokeTest extends InfrastructureIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void mysqlMigrationAndRedisAreReady() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("3");
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name IN (" +
                        "'users', 'game_match', 'match_player', 'match_replay', 'sensitive_data_audit')",
                Integer.class
        );
        assertThat(tableCount).isEqualTo(5);

        Integer oauthIdentityConstraintCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name = 'users' " +
                        "AND constraint_name = 'uk_users_provider_identity' " +
                        "AND constraint_type = 'UNIQUE'",
                Integer.class
        );
        assertThat(oauthIdentityConstraintCount).isEqualTo(1);

        Integer auditIndexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() " +
                        "AND table_name = 'sensitive_data_audit' " +
                        "AND index_name IN ('idx_sensitive_audit_occurred_at', " +
                        "'idx_sensitive_audit_match_uuid')",
                Integer.class
        );
        assertThat(auditIndexCount).isEqualTo(2);

        try (var connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }
}
