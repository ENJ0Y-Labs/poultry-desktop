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
    void startPageConstraintUpgradePreservesExistingApplicationSettings() throws Exception {
        Path database = tempDir.resolve("start-page-upgrade.db");
        String databaseUrl = sqliteUrl(database);

        Flyway.configure()
                .dataSource(databaseUrl, "", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("20"))
                .load()
                .migrate();

        String farmId = seedV1Farm(databaseUrl, "20");
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO application_settings (farm_id, start_page, date_format, created_at, updated_at) VALUES (?, 'operations', 'DD/MM/YYYY', 'created-before-upgrade', 'preserve-me')")) {
            insert.setString(1, farmId);
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }

        configuredFlyway(databaseUrl).migrate();

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT start_page, date_format, updated_at FROM application_settings WHERE farm_id = ?")) {
            statement.setString(1, farmId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("start_page")).isEqualTo("daily-work");
                assertThat(result.getString("date_format")).isEqualTo("DD/MM/YYYY");
                assertThat(result.getString("updated_at")).isEqualTo("preserve-me");
            }
        }

        assertThat(farmExists(databaseUrl, farmId)).isTrue();
    }

    @Test
    void freshDatabaseAcceptsEverySupportedStartPageAndRejectsUnsupportedValues() throws Exception {
        Path database = tempDir.resolve("fresh-start-page.db");
        String databaseUrl = sqliteUrl(database);
        configuredFlyway(databaseUrl).migrate();

        String farmId = seedV1Farm(databaseUrl, "fresh-start-page");
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO application_settings (farm_id, start_page, date_format, created_at, updated_at) VALUES (?, 'dashboard', 'YYYY-MM-DD', 'created', 'updated')")) {
            insert.setString(1, farmId);
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }

        List<String> supportedPages = List.of(
                "dashboard", "flocks", "daily-work", "feed-stock", "health",
                "production", "sales-money", "reports", "settings"
        );

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE application_settings SET start_page = ? WHERE farm_id = ?");
             PreparedStatement select = connection.prepareStatement(
                     "SELECT start_page FROM application_settings WHERE farm_id = ?")) {
            for (String page : supportedPages) {
                update.setString(1, page);
                update.setString(2, farmId);
                assertThat(update.executeUpdate()).isEqualTo(1);

                select.setString(1, farmId);
                try (ResultSet result = select.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("start_page")).isEqualTo(page);
                }
            }

            update.setString(1, "not-a-supported-page");
            update.setString(2, farmId);
            assertThatThrownBy(update::executeUpdate)
                    .isInstanceOf(java.sql.SQLException.class);
        }

        assertThat(farmExists(databaseUrl, farmId)).isTrue();
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


    @Test
    void usernameMigrationPreservesExistingAccountCredentialsAndSessions() throws Exception {
        Path database = tempDir.resolve("username-upgrade.db");
        String databaseUrl = sqliteUrl(database);
        Flyway.configure()
                .dataSource(databaseUrl, "", "")
                .locations(LOCATIONS)
                .target(MigrationVersion.fromVersion("22"))
                .load()
                .migrate();

        String userId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        String created = "2026-10-20T10:00:00Z";
        String expires = "2026-10-20T22:00:00Z";
        try (Connection connection = DriverManager.getConnection(databaseUrl)) {
            try (PreparedStatement user = connection.prepareStatement("""
                    INSERT INTO users
                    (id, email, password_hash, full_name, role, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'OWNER', 'ACTIVE', ?, ?)
                    """)) {
                user.setString(1, userId);
                user.setString(2, "owner@example.com");
                user.setString(3, "existing-password-hash");
                user.setString(4, "Existing Owner");
                user.setString(5, created);
                user.setString(6, created);
                user.executeUpdate();
            }
            try (PreparedStatement session = connection.prepareStatement("""
                    INSERT INTO user_sessions
                    (id, user_id, token_hash, created_at, expires_at, last_seen_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                session.setString(1, sessionId);
                session.setString(2, userId);
                session.setString(3, "existing-session-hash");
                session.setString(4, created);
                session.setString(5, expires);
                session.setString(6, created);
                session.executeUpdate();
            }
        }

        configuredFlyway(databaseUrl).migrate();

        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement query = connection.prepareStatement("""
                     SELECT u.username, u.email, u.password_hash, u.full_name, s.id
                     FROM users u JOIN user_sessions s ON s.user_id = u.id
                     WHERE u.id = ? AND s.id = ?
                     """)) {
            query.setString(1, userId);
            query.setString(2, sessionId);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("username")).isEqualTo("owner-1");
                assertThat(result.getString("email")).isEqualTo("owner@example.com");
                assertThat(result.getString("password_hash")).isEqualTo("existing-password-hash");
                assertThat(result.getString("full_name")).isEqualTo("Existing Owner");
                assertThat(result.getString("id")).isEqualTo(sessionId);
                assertThat(result.next()).isFalse();
            }
        }
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
