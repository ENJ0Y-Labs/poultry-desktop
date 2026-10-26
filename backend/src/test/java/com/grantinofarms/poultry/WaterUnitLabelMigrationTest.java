package com.grantinofarms.poultry;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class WaterUnitLabelMigrationTest {
    @TempDir Path tempDir;

    @Test
    void addsLitresDefaultToExistingFarmWithoutChangingStoredWaterDefaults() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("water-unit-migration.db");
        Flyway.configure().dataSource(url, "", "").locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("20")).load().migrate();

        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO farms(id, name, timezone, currency, status, created_at, updated_at)
                VALUES ('farm-water-test', 'Water Test Farm', 'Africa/Lagos', 'NGN', 'ACTIVE',
                        '2026-10-26T00:00:00Z', '2026-10-26T00:00:00Z')
                """);
            statement.executeUpdate("""
                INSERT INTO farm_settings(farm_id, default_crate_size, default_water_container_size, created_at, updated_at)
                VALUES ('farm-water-test', 30, 25, '2026-10-26T00:00:00Z', '2026-10-26T00:00:00Z')
                """);
            statement.executeUpdate("""
                INSERT INTO water_container_sizes(id, farm_id, name, capacity_units, status, created_at, updated_at)
                VALUES ('water-size-test', 'farm-water-test', '25 units', 25, 'ACTIVE',
                        '2026-10-26T00:00:00Z', '2026-10-26T00:00:00Z')
                """);
            statement.executeUpdate("""
                INSERT INTO houses(id, farm_id, name, code, status, created_at, updated_at)
                VALUES ('house-water-test', 'farm-water-test', 'Water Test House', 'WTH',
                        'ACTIVE', '2026-10-26T00:00:00Z', '2026-10-26T00:00:00Z')
                """);
            statement.executeUpdate("""
                INSERT INTO batches(id, farm_id, house_id, code, batch_type, placement_date,
                    initial_bird_count, status, created_at, updated_at)
                VALUES ('batch-water-test', 'farm-water-test', 'house-water-test', 'B-WATER-TEST',
                    'BROILER', '2026-10-01', 100, 'ACTIVE',
                    '2026-10-26T00:00:00Z', '2026-10-26T00:00:00Z')
                """);
            statement.executeUpdate("""
                INSERT INTO daily_records(id, batch_id, record_date, notes, created_at, updated_at)
                VALUES ('daily-water-test', 'batch-water-test', '2026-10-26', 'Existing water record',
                    '2026-10-26T00:00:00Z', '2026-10-26T00:00:00Z')
                """);
            statement.executeUpdate("""
                INSERT INTO water_usage(id, daily_record_id, container_size_id, container_count, created_at)
                VALUES ('water-usage-test', 'daily-water-test', 'water-size-test', 4,
                    '2026-10-26T00:00:00Z')
                """);
        }

        Flyway.configure().dataSource(url, "", "").locations("classpath:db/migration").load().migrate();

        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                SELECT default_crate_size, default_water_container_size, water_unit_label
                FROM farm_settings WHERE farm_id = 'farm-water-test'
                """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt("default_crate_size")).isEqualTo(30);
            assertThat(result.getInt("default_water_container_size")).isEqualTo(25);
            assertThat(result.getString("water_unit_label")).isEqualTo("litres");
        }
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT name FROM water_container_sizes WHERE id = 'water-size-test'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("name")).isEqualTo("25 litres");
        }
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                SELECT w.capacity_units, u.container_count
                FROM water_container_sizes w
                JOIN water_usage u ON u.container_size_id = w.id
                WHERE w.id = 'water-size-test' AND u.id = 'water-usage-test'
                """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt("capacity_units")).isEqualTo(25);
            assertThat(result.getInt("container_count")).isEqualTo(4);
        }
    }
}
