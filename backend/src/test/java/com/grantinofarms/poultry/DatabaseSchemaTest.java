package com.grantinofarms.poultry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:./target/schema-test.db?foreign_keys=on&journal_mode=WAL&synchronous=FULL",
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
                "batch_code_sequences",
                "farm_settings",
                "water_container_sizes",
                "bird_population_events",
                "bird_cost_events",
                "feed_types",
                "feed_purchases",
                "feed_usage",
                "expenses",
                "daily_records",
                "water_usage",
                "health_records",
                "drug_records",
                "vaccination_records",
                "egg_collections",
                "egg_sales",
                "weight_records",
                "bird_sales",
                "pricing_margin_history",
                "customers",
                "sales",
                "inventory_items",
                "inventory_movements"
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
        assertThat(flywaySuccess).isGreaterThanOrEqualTo(15);
    }

    @Test
    void inventorySupportsItemsAndMovementReasons() {
        List<String> itemColumns = jdbcTemplate.query(
                "PRAGMA table_info(inventory_items)",
                (rs, rowNum) -> rs.getString("name")
        );
        assertThat(itemColumns).contains("name", "category", "unit", "reorder_level", "status");

        List<String> movementColumns = jdbcTemplate.query(
                "PRAGMA table_info(inventory_movements)",
                (rs, rowNum) -> rs.getString("name")
        );
        assertThat(movementColumns).contains("movement_date", "movement_type", "quantity", "reason", "source", "batch_id");
    }

    @Test
    void expensesSupportOptionalBatchAssociation() {
        List<String> columns = jdbcTemplate.query(
                "PRAGMA table_info(expenses)",
                (rs, rowNum) -> rs.getString("name")
        );
        assertThat(columns).contains("batch_id");
    }

    @Test
    void salesSupportCustomerAndTypeSpecificReferences() {
        List<String> salesColumns = jdbcTemplate.query(
                "PRAGMA table_info(sales)",
                (rs, rowNum) -> rs.getString("name")
        );
        assertThat(salesColumns).contains("customer_id", "batch_id", "quantity", "unit", "unit_price_minor", "total_amount_minor", "reference_type", "reference_id");

        List<String> customerColumns = jdbcTemplate.query(
                "PRAGMA table_info(customers)",
                (rs, rowNum) -> rs.getString("name")
        );
        assertThat(customerColumns).contains("name", "phone", "notes");
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
                UUID.randomUUID().toString(), "Test Farm", "Africa/Lagos", "NGN", "ACTIVE", "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z"
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO houses (id, farm_id, name, code, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), "missing-farm", "House", "H1", "ACTIVE", "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z"
        )).isInstanceOf(Exception.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO batches (id, farm_id, house_id, code, batch_type, placement_date, initial_bird_count, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), "missing-farm", "missing-house", "L-2026-001", "LAYER", "2026-10-02", 0, "ACTIVE", "2026-10-02T00:00:00Z", "2026-10-02T00:00:00Z"
        )).isInstanceOf(Exception.class);
    }
}
