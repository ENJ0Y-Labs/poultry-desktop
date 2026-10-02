package com.grantinofarms.poultry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:schema-test?mode=memory&cache=shared",
        "poultry.database-path=:memory:"
})
class DatabaseSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesStageOneTablesAndConstraints() {
        List<String> expectedTables = List.of(
                "farms",
                "users",
                "houses",
                "suppliers",
                "batches",
                "bird_purchases",
                "audit_logs",
                "batch_code_sequences"
        );

        for (String table : expectedTables) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                    Integer.class,
                    table
            );
            assertThat(count).isEqualTo(1);
        }

        Integer flywaySuccess = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class
        );
        assertThat(flywaySuccess).isGreaterThanOrEqualTo(2);
    }

    @Test
    void sqlitePragmasAreConfigured() {
        assertThat(jdbcTemplate.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("PRAGMA journal_mode", String.class))
                .isEqualToIgnoringCase("wal");
        assertThat(jdbcTemplate.queryForObject("PRAGMA synchronous", Integer.class)).isEqualTo(2);
    }

    @Test
    void foreignKeyAndCheckConstraintsAreEnforced() {
        jdbcTemplate.update(
                "INSERT INTO farms (id, name, timezone, currency, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "farm-1", "Test Farm", "Africa/Lagos", "NGN", "ACTIVE", "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z"
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO houses (id, farm_id, name, code, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "house-1", "missing-farm", "House", "H1", "ACTIVE", "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z"
        )).isInstanceOf(Exception.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO batches (id, farm_id, house_id, code, batch_type, placement_date, initial_bird_count, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "batch-1", "farm-1", "missing-house", "L-2026-001", "LAYER", "2026-10-02", 0, "ACTIVE", "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z"
        )).isInstanceOf(Exception.class);
    }
}
