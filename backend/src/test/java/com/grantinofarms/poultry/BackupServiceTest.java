package com.grantinofarms.poultry;

import com.grantinofarms.poultry.service.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupServiceTest {

    @TempDir
    Path tempDir;

    private final List<SingleConnectionDataSource> dataSources = new ArrayList<>();

    @AfterEach
    void closeDataSources() {
        dataSources.forEach(SingleConnectionDataSource::destroy);
    }

    private BackupService service(Path database) throws Exception {
        var dataSource = new SingleConnectionDataSource(
                "jdbc:sqlite:" + database.toAbsolutePath(),
                true
        );
        dataSources.add(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE farm_data (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE app_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE flyway_schema_history (installed_rank INTEGER PRIMARY KEY, version TEXT, success INTEGER NOT NULL)");
        jdbc.update("INSERT INTO app_metadata(key, value) VALUES (?, ?)", "schema_initialized", "true");
        jdbc.update("INSERT INTO flyway_schema_history(installed_rank, version, success) VALUES (?, ?, ?)", 18, "18", 1);
        jdbc.update("INSERT INTO farm_data(name) VALUES (?)", "Grantino Farms");

        var environment = new MockEnvironment()
                .withProperty("poultry.database-path", database.toString());

        return new BackupService(jdbc, environment);
    }

    @Test
    void createsConsistentBackupWithDefaultAndDuplicateNames() throws Exception {
        Path database = tempDir.resolve("poultry.db");
        BackupService service = service(database);

        var first = service.create(tempDir.toString());
        var firstPath = Path.of((String) first.get("path"));

        assertThat(firstPath).exists();
        assertThat(first.get("filename")).isEqualTo(
                "poultry-" + LocalDate.now() + ".db"
        );

        var second = service.create(tempDir.toString());
        var secondName = (String) second.get("filename");

        assertThat(Path.of((String) second.get("path"))).exists();
        assertThat(secondName)
                .matches("poultry-\\d{4}-\\d{2}-\\d{2}-\\d{4}(?:-\\d+)?\\.db");

        var validation = service.validate(second.get("path").toString());
        assertThat(validation.get("valid")).isEqualTo(true);
    }

    @Test
    void vacuumIntoSnapshotIncludesCommittedDataWhileWalModeIsEnabled() throws Exception {
        Path database = tempDir.resolve("poultry.db");
        BackupService service = service(database);

        var dataSource = dataSources.get(dataSources.size() - 1);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("PRAGMA journal_mode=WAL");
        jdbc.execute("PRAGMA synchronous=FULL");
        jdbc.update("INSERT INTO farm_data(name) VALUES (?)", "WAL-visible farm record");

        var result = service.create(tempDir.toString());
        Path backup = Path.of(result.get("path").toString());

        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + backup)) {
            String name = connection.createStatement()
                    .executeQuery("SELECT name FROM farm_data WHERE name = 'WAL-visible farm record'")
                    .getString(1);
            assertThat(name).isEqualTo("WAL-visible farm record");
        }
    }

    @Test
    void retainsOnlyThirtyManagedBackups() throws Exception {
        Path database = tempDir.resolve("poultry.db");
        BackupService service = service(database);

        for (int day = 1; day <= 31; day++) {
            Files.createFile(tempDir.resolve(String.format(
                    "poultry-2026-09-%02d.db", day
            )));
        }

        service.create(tempDir.toString());

        try (Stream<Path> files = Files.list(tempDir)) {
            long count = files
                    .filter(path -> path.getFileName().toString().matches(
                            "poultry-\\d{4}-\\d{2}-\\d{2}(?:-\\d{4}(?:-\\d+)?)?\\.db"
                    ))
                    .count();
            assertThat(count).isEqualTo(30);
        }
    }

    @Test
    void rejectsSqliteDatabaseWithoutPoultrySchema() throws Exception {
        Path database = tempDir.resolve("not-poultry.db");
        var dataSource = new SingleConnectionDataSource(
                "jdbc:sqlite:" + database.toAbsolutePath(),
                true
        );
        dataSources.add(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE unrelated (id INTEGER PRIMARY KEY)");
        var environment = new MockEnvironment()
                .withProperty("poultry.database-path", database.toString());
        var service = new BackupService(jdbc, environment);

        assertThatThrownBy(() -> service.validate(database.toString()))
                .isInstanceOf(com.grantinofarms.poultry.exception.ApiException.class)
                .hasMessageContaining("not a Poultry Farm Manager database");
    }

    @Test
    void rejectsInMemoryDatabaseBackups() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        dataSources.add(dataSource);
        var environment = new MockEnvironment()
                .withProperty("poultry.database-path", ":memory:");
        var service = new BackupService(new JdbcTemplate(dataSource), environment);

        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(com.grantinofarms.poultry.exception.ApiException.class)
                .hasMessageContaining("unavailable");
    }
    @Test
    void rejectsMissingBackupWithClearNotFoundError() throws Exception {
        Path database = tempDir.resolve("poultry.db");
        BackupService service = service(database);

        assertThatThrownBy(() -> service.validate(tempDir.resolve("missing.db").toString()))
                .isInstanceOf(com.grantinofarms.poultry.exception.ApiException.class)
                .hasMessage("Backup file was not found.");
    }

    @Test
    void rejectsCorruptedBackupWithClearValidationError() throws Exception {
        Path database = tempDir.resolve("corrupt.db");
        Files.writeString(database, "this is not a sqlite database");
        BackupService service = service(tempDir.resolve("poultry.db"));

        assertThatThrownBy(() -> service.validate(database.toString()))
                .isInstanceOf(com.grantinofarms.poultry.exception.ApiException.class)
                .hasMessage("The selected file is not a valid Poultry Farm Manager database.");
    }

}
