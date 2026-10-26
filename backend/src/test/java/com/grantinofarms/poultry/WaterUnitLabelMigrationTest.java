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
    }
}
