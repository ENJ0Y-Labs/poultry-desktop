package com.grantinofarms.poultry;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationUpgradeTest {

    private static final String LOCATIONS = "classpath:db/migration";

    @TempDir
    Path tempDir;

    @Test
    void everyProductionMigrationCanUpgradeARealisticPreviousDatabaseWithoutLosingData() throws Exception {
        String latestDatabaseUrl = sqliteUrl(tempDir.resolve("migration-discovery.db"));
        Flyway discoveryFlyway = configuredFlyway(latestDatabaseUrl);
        List<String> versions = Arrays.stream(discoveryFlyway.info().all())
                .filter(MigrationInfo::isVersioned)
                .map(info -> info.getVersion().getVersion())
                .filter(version -> version != null)
                .distinct()
                .sorted((left, right) -> FlywayVersionComparator.compare(left, right))
                .collect(Collectors.toList());

        assertThat(versions).isNotEmpty();

        String latestVersion = versions.get(versions.size() - 1);

        for (String sourceVersion : versions.subList(0, versions.size() - 1)) {
            Path database = tempDir.resolve("upgrade-from-" + sourceVersion.replace('.', '_') + ".db");
            String databaseUrl = sqliteUrl(database);

            Flyway.configure()
                    .dataSource(databaseUrl, "", "")
                    .locations(LOCATIONS)
                    .target(MigrationVersion.fromVersion(sourceVersion))
                    .load()
                    .migrate();

            if (!tableExists(databaseUrl, "farms")) {
                String nextVersion = versions.get(versions.indexOf(sourceVersion) + 1);
                Flyway.configure()
                        .dataSource(databaseUrl, "", "")
                        .locations(LOCATIONS)
                        .target(MigrationVersion.fromVersion(nextVersion))
                        .load()
                        .migrate();
            }

            String farmId = seedV1Farm(databaseUrl, sourceVersion);

            configuredFlyway(databaseUrl)
                    .migrate();

            assertThat(currentVersion(databaseUrl)).isEqualTo(latestVersion);
            assertThat(farmExists(databaseUrl, farmId))
                    .as("Farm data created at V%s must survive migration to V%s", sourceVersion, latestVersion)
                    .isTrue();
        }
    }

    @Test
    void failedMigrationDoesNotDestroyExistingDataOrLeavePartialSchemaChanges() throws Exception {
        Path failureMigrationDirectory = tempDir.resolve("intentional-failure");
        Files.createDirectories(failureMigrationDirectory);
        Files.writeString(
                failureMigrationDirectory.resolve("V999999__intentional_failure.sql"),
                """
                CREATE TABLE migration_failure_probe (
                    id INTEGER PRIMARY KEY
                );

                THIS_STATEMENT_IS_INTENTIONALLY_INVALID;
                """
        );

        Path database = tempDir.resolve("failed-migration.db");
        String databaseUrl = sqliteUrl(database);

        configuredFlyway(databaseUrl)
                .migrate();

        String farmId = seedV1Farm(databaseUrl, "failure-test");

        Flyway failingFlyway = Flyway.configure()
                .dataSource(databaseUrl, "", "")
                .locations(LOCATIONS, "filesystem:" + failureMigrationDirectory.toAbsolutePath())
                .load();

        assertThatThrownBy(failingFlyway::migrate)
                .isInstanceOf(Exception.class);

        assertThat(farmExists(databaseUrl, farmId))
                .as("Existing V1 data must survive a failed later migration")
                .isTrue();

        assertThat(tableExists(databaseUrl, "migration_failure_probe"))
                .as("Failed migration must not leave its partially-created table behind")
                .isFalse();

        String latestVersion = Arrays.stream(configuredFlyway(databaseUrl).info().all())
                .filter(MigrationInfo::isVersioned)
                .map(info -> info.getVersion().getVersion())
                .filter(version -> version != null)
                .distinct()
                .sorted(FlywayVersionComparator::compare)
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(currentVersion(databaseUrl))
                .as("Failed migration must not advance the production schema version")
                .isEqualTo(latestVersion);
    }

    private static Flyway configuredFlyway(String databaseUrl) {
        return Flyway.configure()
                .dataSource(databaseUrl, "", "")
                .locations(LOCATIONS)
                .load();
    }

    private static String sqliteUrl(Path database) {
        return "jdbc:sqlite:" + database.toAbsolutePath()
                + "?foreign_keys=on&journal_mode=WAL&synchronous=FULL";
    }

    private static String seedV1Farm(String databaseUrl, String sourceVersion) throws Exception {
        String farmId = UUID.randomUUID().toString();

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     """
                     INSERT INTO farms
                     (id, name, timezone, currency, status, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            String timestamp = "2026-10-02T00:00:00Z";
            statement.setString(1, farmId);
            statement.setString(2, "Migration Test Farm V" + sourceVersion);
            statement.setString(3, "Africa/Lagos");
            statement.setString(4, "NGN");
            statement.setString(5, "ACTIVE");
            statement.setString(6, timestamp);
            statement.setString(7, timestamp);
            statement.executeUpdate();
        }

        return farmId;
    }

    private static boolean farmExists(String databaseUrl, String farmId) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM farms WHERE id = ?")) {
            statement.setString(1, farmId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }

    private static boolean tableExists(String databaseUrl, String tableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }

    private static String currentVersion(String databaseUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return null;
            }
            return resultSet.getString(1);
        }
    }

    private static final class FlywayVersionComparator {
        private static int compare(String left, String right) {
            String[] leftParts = left.split("\\.");
            String[] rightParts = right.split("\\.");
            int length = Math.max(leftParts.length, rightParts.length);

            for (int i = 0; i < length; i++) {
                long leftPart = i < leftParts.length ? Long.parseLong(leftParts[i]) : 0;
                long rightPart = i < rightParts.length ? Long.parseLong(rightParts[i]) : 0;
                int comparison = Long.compare(leftPart, rightPart);
                if (comparison != 0) {
                    return comparison;
                }
            }

            return 0;
        }
    }
}
