package com.battle.code.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsCreateTheCompleteSchemaAndOAuthIdentityConstraint() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
        Integer domainTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' " +
                        "AND table_name IN ('users', 'game_match', 'match_player', 'match_replay')",
                Integer.class
        );
        assertThat(domainTableCount).isEqualTo(4);

        Integer oauthIdentityConstraintCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                        "WHERE table_schema = 'public' " +
                        "AND table_name = 'users' " +
                        "AND constraint_name = 'uk_users_provider_identity' " +
                        "AND constraint_type = 'UNIQUE'",
                Integer.class
        );
        assertThat(oauthIdentityConstraintCount).isEqualTo(1);
    }
}
