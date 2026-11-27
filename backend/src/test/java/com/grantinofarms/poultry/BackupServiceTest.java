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
        assertThat(first).containsEntry("valid", true).containsEntry("schemaVersion", "18");
        assertThat(first.get("sizeBytes")).isInstanceOf(Long.class);
        assertThat((Long) first.get("sizeBytes")).isPositive();
        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files.map(path -> path.getFileName().toString())
                    .noneMatch(name -> name.contains(".tmp-"))).isTrue();
        }

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


    @Test
    void backupPreservesReviewHistoryIncidentsAndEvidenceForRestore() throws Exception {
        Path database = tempDir.resolve("poultry.db");
        BackupService service = service(database);
        var jdbc = new JdbcTemplate(dataSources.get(dataSources.size() - 1));

        jdbc.execute("CREATE TABLE report_submissions (id TEXT PRIMARY KEY, status TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE report_submission_versions (submission_id TEXT NOT NULL, version_number INTEGER NOT NULL, payload_json TEXT NOT NULL, PRIMARY KEY (submission_id, version_number))");
        jdbc.execute("CREATE TABLE report_submission_comments (id TEXT PRIMARY KEY, submission_id TEXT NOT NULL, version_number INTEGER NOT NULL, comment TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE report_submission_checks (id TEXT PRIMARY KEY, submission_id TEXT NOT NULL, version_number INTEGER NOT NULL, outcome TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE report_submission_events (id TEXT PRIMARY KEY, submission_id TEXT NOT NULL, event_type TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE report_submission_attachments (id TEXT PRIMARY KEY, submission_id TEXT NOT NULL, version_number INTEGER NOT NULL, file_name TEXT NOT NULL, mime_type TEXT NOT NULL, file_size INTEGER NOT NULL, sha256 TEXT NOT NULL, file_content BLOB NOT NULL)");
        jdbc.execute("CREATE TABLE farm_incidents (id TEXT PRIMARY KEY, status TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE farm_incident_events (id TEXT PRIMARY KEY, incident_id TEXT NOT NULL, event_type TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE incident_attachments (id TEXT PRIMARY KEY, incident_id TEXT NOT NULL, file_name TEXT NOT NULL, mime_type TEXT NOT NULL, file_size INTEGER NOT NULL, sha256 TEXT NOT NULL, file_content BLOB NOT NULL)");
        jdbc.execute("CREATE TABLE audit_logs (id TEXT PRIMARY KEY, occurred_at TEXT NOT NULL, action TEXT NOT NULL, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, reason TEXT)");

        byte[] reportEvidence = new byte[] { 0, 1, 2, 3, 10, 13, (byte) 0xff };
        byte[] incidentEvidence = new byte[] { 80, 75, 3, 4, 0, 0, 1, 2 };
        jdbc.update("INSERT INTO report_submissions(id, status) VALUES (?, ?)", "submission-1", "MERGED");
        jdbc.update("INSERT INTO report_submission_versions(submission_id, version_number, payload_json) VALUES (?, ?, ?)",
                "submission-1", 2, "{\"eventDate\":\"2026-11-27\",\"quantity\":12}");
        jdbc.update("INSERT INTO report_submission_comments(id, submission_id, version_number, comment) VALUES (?, ?, ?, ?)",
                "comment-1", "submission-1", 2, "Verified against source record");
        jdbc.update("INSERT INTO report_submission_checks(id, submission_id, version_number, outcome) VALUES (?, ?, ?, ?)",
                "check-1", "submission-1", 2, "PASSED");
        jdbc.update("INSERT INTO report_submission_events(id, submission_id, event_type) VALUES (?, ?, ?)",
                "event-1", "submission-1", "MERGED");
        jdbc.update("INSERT INTO report_submission_attachments(id, submission_id, version_number, file_name, mime_type, file_size, sha256, file_content) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "report-file-1", "submission-1", 2, "receipt.png", "image/png",
                reportEvidence.length, "report-sha256", reportEvidence);
        jdbc.update("INSERT INTO farm_incidents(id, status) VALUES (?, ?)", "incident-1", "RESOLVED");
        jdbc.update("INSERT INTO audit_logs(id, occurred_at, action, entity_type, entity_id, reason) VALUES (?, ?, ?, ?, ?, ?)",
                "audit-1", "2026-11-27T10:00:00Z", "MERGE", "REPORT_SUBMISSION", "submission-1", "Manager approved report");
        jdbc.update("INSERT INTO farm_incident_events(id, incident_id, event_type) VALUES (?, ?, ?)",
                "incident-event-1", "incident-1", "RESOLVED");
        jdbc.update("INSERT INTO incident_attachments(id, incident_id, file_name, mime_type, file_size, sha256, file_content) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "incident-file-1", "incident-1", "incident.pdf", "application/pdf",
                incidentEvidence.length, "incident-sha256", incidentEvidence);

        var result = service.create(tempDir.toString());
        Path backup = Path.of(result.get("path").toString());
        assertThat(service.validate(backup.toString()).get("valid")).isEqualTo(true);

        // Reopen a disposable copy of the snapshot as the restored database.
        Path restored = tempDir.resolve("restored-poultry.db");
        Files.copy(backup, restored);
        var restoredDataSource = new SingleConnectionDataSource("jdbc:sqlite:" + restored, true);
        dataSources.add(restoredDataSource);
        var restoredJdbc = new JdbcTemplate(restoredDataSource);

        assertThat(restoredJdbc.queryForObject(
                "SELECT status FROM report_submissions WHERE id = 'submission-1'", String.class))
                .isEqualTo("MERGED");
        assertThat(restoredJdbc.queryForObject(
                "SELECT comment FROM report_submission_comments WHERE id = 'comment-1'", String.class))
                .isEqualTo("Verified against source record");
        assertThat(restoredJdbc.queryForObject(
                "SELECT outcome FROM report_submission_checks WHERE id = 'check-1'", String.class))
                .isEqualTo("PASSED");
        assertThat(restoredJdbc.queryForObject(
                "SELECT event_type FROM report_submission_events WHERE id = 'event-1'", String.class))
                .isEqualTo("MERGED");
        assertThat(restoredJdbc.queryForObject(
                "SELECT reason FROM audit_logs WHERE id = 'audit-1'", String.class))
                .isEqualTo("Manager approved report");
        assertThat(restoredJdbc.queryForObject(
                "SELECT status FROM farm_incidents WHERE id = 'incident-1'", String.class))
                .isEqualTo("RESOLVED");
        assertThat(restoredJdbc.queryForObject(
                "SELECT event_type FROM farm_incident_events WHERE id = 'incident-event-1'", String.class))
                .isEqualTo("RESOLVED");
        assertThat(restoredJdbc.queryForObject(
                "SELECT file_name FROM report_submission_attachments WHERE id = 'report-file-1'", String.class))
                .isEqualTo("receipt.png");
        assertThat(restoredJdbc.queryForObject(
                "SELECT file_content FROM report_submission_attachments WHERE id = 'report-file-1'", byte[].class))
                .containsExactly(reportEvidence);
        assertThat(restoredJdbc.queryForObject(
                "SELECT file_name FROM incident_attachments WHERE id = 'incident-file-1'", String.class))
                .isEqualTo("incident.pdf");
        assertThat(restoredJdbc.queryForObject(
                "SELECT file_content FROM incident_attachments WHERE id = 'incident-file-1'", byte[].class))
                .containsExactly(incidentEvidence);
        assertThat(restoredJdbc.queryForObject(
                "SELECT name FROM farm_data WHERE name = 'Grantino Farms'", String.class))
                .isEqualTo("Grantino Farms");
    }

}\n