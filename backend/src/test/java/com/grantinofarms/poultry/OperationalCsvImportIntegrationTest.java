package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:operational-import-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OperationalCsvImportIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired OperationalCsvImportService importer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void failedConfirmedImportRollsBackEarlierRowsAndAuditEntries() {
        var batch = setupBatch();
        jdbc.execute("""
                CREATE TRIGGER fail_second_daily_record
                BEFORE INSERT ON daily_records
                WHEN NEW.record_date = '2026-01-03'
                BEGIN
                    SELECT RAISE(ABORT, 'intentional integration-test failure');
                END
                """);

        CsvImportRequest request = new CsvImportRequest("daily-records", List.of(
                Map.of("batch_code", batch.code(), "record_date", "2026-01-02", "notes", "First row", "water_entries", ""),
                Map.of("batch_code", batch.code(), "record_date", "2026-01-03", "notes", "Second row", "water_entries", "")));

        assertThatThrownBy(() -> importer.importRecords(request)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_records", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'DAILY_RECORD'", Integer.class)).isZero();
    }

    @Test
    void exactRetrySkipsPopulationEventAndDoesNotDuplicateAudit() {
        var batch = setupBatch();
        CsvImportRequest request = new CsvImportRequest("mortality", List.of(
                Map.of("batch_code", batch.code(), "event_date", "2026-01-02", "quantity", "2",
                        "reason", "Disease", "notes", "Recorded from notebook")));

        Map<String, Object> first = importer.importRecords(request);
        Map<String, Object> retry = importer.importRecords(request);

        assertThat(first.get("imported")).isEqualTo(1);
        assertThat(retry.get("imported")).isEqualTo(0);
        assertThat(retry.get("duplicates")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bird_population_events WHERE batch_id = ?", Integer.class, batch.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'BIRD_POPULATION_EVENT'", Integer.class)).isEqualTo(1);
    }

    private BatchResponse setupBatch() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        return batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));
    }
}
