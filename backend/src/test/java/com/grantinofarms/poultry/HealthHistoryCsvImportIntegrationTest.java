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
        "spring.datasource.url=jdbc:sqlite:file:health-import-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HealthHistoryCsvImportIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired HealthHistoryCsvImportService importer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void failedHealthImportRollsBackEarlierHealthAndAuditWrites() {
        var batch = setupBatch();
        jdbc.execute("""
                CREATE TRIGGER fail_health_import_row
                BEFORE INSERT ON health_records
                WHEN NEW.condition_problem = 'Trigger failure'
                BEGIN
                    SELECT RAISE(ABORT, 'intentional integration-test failure');
                END
                """);

        CsvImportRequest request = new CsvImportRequest("health", List.of(
                Map.of("batch_code", batch.code(), "record_date", "2026-01-02",
                        "condition_problem", "Coughing", "description", "First row", "action", "Observe"),
                Map.of("batch_code", batch.code(), "record_date", "2026-01-03",
                        "condition_problem", "Trigger failure", "description", "Second row", "action", "Treat")));

        assertThatThrownBy(() -> importer.importRecords(request)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM health_records", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'HEALTH_RECORD'", Integer.class)).isZero();
    }

    @Test
    void drugImportCreatesTheSameExpenseAndAuditAsManualEntry() {
        var batch = setupBatch();
        Map<String, Object> result = importer.importRecords(new CsvImportRequest("drugs", List.of(
                Map.of("batch_code", batch.code(), "record_date", "2026-01-02", "drug", "Drug A",
                        "quantity", "2", "cost_minor", "25000", "reason", "Treatment"))));

        assertThat(result.get("imported")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drug_records WHERE batch_id = ?", Integer.class, batch.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE batch_id = ? AND category = 'DRUGS' AND amount_minor = 25000", Integer.class, batch.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'DRUG_RECORD'", Integer.class)).isEqualTo(1);
    }


    @Test
    void failedDrugImportRollsBackDrugRowsExpensesAndAuditWrites() {
        var batch = setupBatch();
        jdbc.execute("""
                CREATE TRIGGER fail_drug_import_row
                BEFORE INSERT ON drug_records
                WHEN NEW.drug = 'Trigger failure'
                BEGIN
                    SELECT RAISE(ABORT, 'intentional integration-test failure');
                END
                """);

        CsvImportRequest request = new CsvImportRequest("drugs", List.of(
                Map.of("batch_code", batch.code(), "record_date", "2026-01-02", "drug", "Drug A",
                        "quantity", "2", "cost_minor", "25000", "reason", "First treatment"),
                Map.of("batch_code", batch.code(), "record_date", "2026-01-03", "drug", "Trigger failure",
                        "quantity", "1", "cost_minor", "10000", "reason", "Second treatment")));

        assertThatThrownBy(() -> importer.importRecords(request)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM drug_records", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE category = 'DRUGS'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'DRUG_RECORD'", Integer.class)).isZero();
    }

    private BatchResponse setupBatch() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        return batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));
    }
}
