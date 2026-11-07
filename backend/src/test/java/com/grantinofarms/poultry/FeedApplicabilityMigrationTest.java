package com.grantinofarms.poultry;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeedApplicabilityMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void migrationExpandsLegacyScopesWithoutChangingPurchasesOrUsage() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + tempDir.resolve("feed-applicability.db").toAbsolutePath()
                + "?foreign_keys=on&journal_mode=WAL&synchronous=FULL";
        Flyway.configure()
                .dataSource(databaseUrl, "", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("31"))
                .load()
                .migrate();

        String farmId = UUID.randomUUID().toString();
        String houseId = UUID.randomUUID().toString();
        String batchId = UUID.randomUUID().toString();
        String bothId = UUID.randomUUID().toString();
        String layerId = UUID.randomUUID().toString();
        String broilerId = UUID.randomUUID().toString();
        String turkeyId = UUID.randomUUID().toString();
        String created = "2026-11-01T00:00:00Z";

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO farms (id, name, timezone, currency, status, created_at, updated_at)
                    VALUES (?, 'Migration Farm', 'Africa/Lagos', 'NGN', 'ACTIVE', ?, ?)
                    """)) {
                statement.setString(1, farmId);
                statement.setString(2, created);
                statement.setString(3, created);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO houses (id, farm_id, name, code, status, created_at, updated_at)
                    VALUES (?, ?, 'House', 'H1', 'ACTIVE', ?, ?)
                    """)) {
                statement.setString(1, houseId);
                statement.setString(2, farmId);
                statement.setString(3, created);
                statement.setString(4, created);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO batches
                    (id, farm_id, house_id, code, batch_type, placement_date, initial_bird_count,
                     status, created_at, updated_at)
                    VALUES (?, ?, ?, 'B-001', 'BROILER', '2026-11-01', 10, 'ACTIVE', ?, ?)
                    """)) {
                statement.setString(1, batchId);
                statement.setString(2, farmId);
                statement.setString(3, houseId);
                statement.setString(4, created);
                statement.setString(5, created);
                statement.executeUpdate();
            }

            insertFeedType(connection, bothId, farmId, "Legacy Both", "BOTH", created);
            insertFeedType(connection, layerId, farmId, "Legacy Layer", "LAYER", created);
            insertFeedType(connection, broilerId, farmId, "Legacy Broiler", "BROILER", created);
            insertFeedType(connection, turkeyId, farmId, "Legacy Turkey", "TURKEY", created);

            try (PreparedStatement purchase = connection.prepareStatement("""
                    INSERT INTO feed_purchases
                    (id, farm_id, feed_type_id, purchase_date, quantity_milli, unit, total_cost_minor, created_at)
                    VALUES ('purchase-1', ?, ?, '2026-11-01', 5000, 'kg', 50000, ?)
                    """)) {
                purchase.setString(1, farmId);
                purchase.setString(2, bothId);
                purchase.setString(3, created);
                purchase.executeUpdate();
            }
            try (PreparedStatement usage = connection.prepareStatement("""
                    INSERT INTO feed_usage
                    (id, batch_id, feed_type_id, usage_date, quantity_milli, created_at)
                    VALUES ('usage-1', ?, ?, '2026-11-02', 1000, ?)
                    """)) {
                usage.setString(1, batchId);
                usage.setString(2, bothId);
                usage.setString(3, created);
                usage.executeUpdate();
            }
        }

        Flyway.configure()
                .dataSource(databaseUrl, "", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(applicableTypes(databaseUrl, bothId)).containsExactly("BROILER", "LAYER", "TURKEY");
        assertThat(applicableTypes(databaseUrl, layerId)).containsExactly("LAYER");
        assertThat(applicableTypes(databaseUrl, broilerId)).containsExactly("BROILER");
        assertThat(applicableTypes(databaseUrl, turkeyId)).containsExactly("TURKEY");

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            try (ResultSet result = connection.createStatement().executeQuery("""
                    SELECT p.quantity_milli, p.total_cost_minor, u.quantity_milli
                    FROM feed_purchases p JOIN feed_usage u ON u.feed_type_id = p.feed_type_id
                    WHERE p.id = 'purchase-1' AND u.id = 'usage-1'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("quantity_milli")).isEqualTo(5000);
                assertThat(result.getLong("total_cost_minor")).isEqualTo(50000);
                assertThat(result.getLong(3)).isEqualTo(1000);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void insertFeedType(Connection connection, String id, String farmId,
                                       String name, String legacyType, String created) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO feed_types
                (id, farm_id, name, unit, applicable_type, status, created_at, updated_at)
                VALUES (?, ?, ?, 'kg', ?, 'ACTIVE', ?, ?)
                """)) {
            statement.setString(1, id);
            statement.setString(2, farmId);
            statement.setString(3, name);
            statement.setString(4, legacyType);
            statement.setString(5, created);
            statement.setString(6, created);
            statement.executeUpdate();
        }
    }

    private static List<String> applicableTypes(String databaseUrl, String feedTypeId) throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT bird_type FROM feed_type_applicability
                     WHERE feed_type_id = ?
                     ORDER BY bird_type
                     """)) {
            statement.setString(1, feedTypeId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
        }
        return result;
    }
}
