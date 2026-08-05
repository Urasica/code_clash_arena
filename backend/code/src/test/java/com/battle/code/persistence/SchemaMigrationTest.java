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
    void baselineMigrationCreatesTheCompleteSchema() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
        Integer domainTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' " +
                        "AND table_name IN ('users', 'game_match', 'match_player', 'match_replay')",
                Integer.class
        );
        assertThat(domainTableCount).isEqualTo(4);
    }
}
