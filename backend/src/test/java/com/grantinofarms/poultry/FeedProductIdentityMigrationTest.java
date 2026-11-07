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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedProductIdentityMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void migrationPreservesFeedHistoryAndAllowsSameNameUnderDifferentBrands() throws Exception {
        String databaseUrl = "jdbc:sqlite:" + tempDir.resolve("feed-product-identity.db").toAbsolutePath()
                + "?foreign_keys=on&journal_mode=WAL&synchronous=FULL";
        Flyway.configure()
                .dataSource(databaseUrl, "", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("32"))
                .load()
                .migrate();

        String farmId = UUID.randomUUID().toString();
        String feedTypeId = UUID.randomUUID().toString();
        String created = "2026-11-07T00:00:00Z";
        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            try (PreparedStatement farm = connection.prepareStatement("""
                    INSERT INTO farms (id, name, timezone, currency, status, created_at, updated_at)
                    VALUES (?, 'Brand Migration Farm', 'Africa/Lagos', 'NGN', 'ACTIVE', ?, ?)
                    """)) {
                farm.setString(1, farmId);
                farm.setString(2, created);
                farm.setString(3, created);
                farm.executeUpdate();
            }
            try (PreparedStatement feed = connection.prepareStatement("""
                    INSERT INTO feed_types
                    (id, farm_id, name, unit, applicable_type, status, created_at, updated_at,
                     kg_per_unit, brand, packaging_name, package_weight_kg)
                    VALUES (?, ?, 'Grower Mash', 'bag', 'BROILER', 'ACTIVE', ?, ?, 25, 'Hybrid',
                            '25 kg bag', 25)
                    """)) {
                feed.setString(1, feedTypeId);
                feed.setString(2, farmId);
                feed.setString(3, created);
                feed.setString(4, created);
                feed.executeUpdate();
            }
            try (PreparedStatement applicability = connection.prepareStatement("""
                    INSERT INTO feed_type_applicability (feed_type_id, bird_type) VALUES (?, 'BROILER')
                    """)) {
                applicability.setString(1, feedTypeId);
                applicability.executeUpdate();
            }
            try (PreparedStatement purchase = connection.prepareStatement("""
                    INSERT INTO feed_purchases
                    (id, farm_id, feed_type_id, purchase_date, quantity_milli, unit, total_cost_minor, created_at)
                    VALUES ('migration-purchase', ?, ?, '2026-11-01', 10000, 'bag', 100000, ?)
                    """)) {
                purchase.setString(1, farmId);
                purchase.setString(2, feedTypeId);
                purchase.setString(3, created);
                purchase.executeUpdate();
            }
            try (PreparedStatement opening = connection.prepareStatement("""
                    INSERT INTO feed_opening_balances
                    (id, farm_id, feed_type_id, effective_date, quantity_milli, unit, reason, created_at)
                    VALUES ('migration-opening', ?, ?, '2026-11-01', 2000, 'bag', 'Opening count', ?)
                    """)) {
                opening.setString(1, farmId);
                opening.setString(2, feedTypeId);
                opening.setString(3, created);
                opening.executeUpdate();
            }
        }

        Flyway.configure()
                .dataSource(databaseUrl, "", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            try (Statement statement = connection.createStatement();
                 ResultSet foreignKeys = statement.executeQuery("PRAGMA foreign_key_check")) {
                assertThat(foreignKeys.next()).isFalse();
            }
            try (PreparedStatement existing = connection.prepareStatement("""
                    SELECT f.id, f.brand, f.packaging_name, f.package_weight_kg,
                           p.feed_type_id AS purchase_type, o.feed_type_id AS opening_type,
                           a.bird_type
                    FROM feed_types f
                    JOIN feed_purchases p ON p.feed_type_id = f.id
                    JOIN feed_opening_balances o ON o.feed_type_id = f.id
                    JOIN feed_type_applicability a ON a.feed_type_id = f.id
                    WHERE f.id = ?
                    """)) {
                existing.setString(1, feedTypeId);
                try (ResultSet row = existing.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("id")).isEqualTo(feedTypeId);
                    assertThat(row.getString("brand")).isEqualTo("Hybrid");
                    assertThat(row.getString("packaging_name")).isEqualTo("25 kg bag");
                    assertThat(row.getBigDecimal("package_weight_kg")).isEqualByComparingTo("25");
                    assertThat(row.getString("purchase_type")).isEqualTo(feedTypeId);
                    assertThat(row.getString("opening_type")).isEqualTo(feedTypeId);
                    assertThat(row.getString("bird_type")).isEqualTo("BROILER");
                    assertThat(row.next()).isFalse();
                }
            }

            try (PreparedStatement secondBrand = connection.prepareStatement("""
                    INSERT INTO feed_types
                    (id, farm_id, name, unit, applicable_type, status, created_at, updated_at, brand)
                    VALUES ('brand-second', ?, 'Grower Mash', 'bag', 'BROILER', 'ACTIVE', ?, ?, 'Other Brand')
                    """)) {
                secondBrand.setString(1, farmId);
                secondBrand.setString(2, created);
                secondBrand.setString(3, created);
                assertThat(secondBrand.executeUpdate()).isEqualTo(1);
            }

            assertThatThrownBy(() -> {
                try (PreparedStatement duplicate = connection.prepareStatement("""
                        INSERT INTO feed_types
                        (id, farm_id, name, unit, applicable_type, status, created_at, updated_at, brand)
                        VALUES ('brand-duplicate', ?, 'Grower Mash', 'bag', 'BROILER', 'ACTIVE', ?, ?, 'Hybrid')
                        """)) {
                    duplicate.setString(1, farmId);
                    duplicate.setString(2, created);
                    duplicate.setString(3, created);
                    duplicate.executeUpdate();
                }
            }).isInstanceOf(SQLException.class);
        }
    }
}
