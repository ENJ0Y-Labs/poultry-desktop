package com.grantinofarms.poultry;

import com.grantinofarms.poultry.service.BackupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupServiceTest {

    @TempDir
    Path tempDir;

    private BackupService service(Path database) throws Exception {
        var dataSource = new SingleConnectionDataSource(
                "jdbc:sqlite:" + database.toAbsolutePath(),
                true
        );
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
    void rejectsInMemoryDatabaseBackups() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        var environment = new MockEnvironment()
                .withProperty("poultry.database-path", ":memory:");
        var service = new BackupService(new JdbcTemplate(dataSource), environment);

        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(com.grantinofarms.poultry.exception.ApiException.class)
                .hasMessageContaining("unavailable");
    }
}
