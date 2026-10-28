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
        "spring.datasource.url=jdbc:sqlite:file:production-history-import-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductionHistoryCsvImportIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired ProductionHistoryCsvImportService importer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void importsZeroEggCollectionAsAnExplicitRecordAndSkipsRetry() {
        var batch = setupBatch("LAYER", "L-2026-001");
        CsvImportRequest request = new CsvImportRequest("egg-collections", List.of(
                Map.of("batch_code", batch.code(), "record_date", "2026-01-02",
                        "good_eggs", "0", "cracked_eggs", "0", "notes", "")));

        assertThat(importer.preview(request).get("valid")).isEqualTo(true);
        assertThat(importer.importRecords(request).get("imported")).isEqualTo(1);
        Map<String, Object> retry = importer.importRecords(request);
        assertThat(retry.get("imported")).isEqualTo(0);
        assertThat(retry.get("duplicates")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM egg_collections", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT good_eggs + cracked_eggs FROM egg_collections", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'EGG_COLLECTION'", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsEggImportForBroilerAndWeightImportForLayer() {
        var layer = setupBatch("LAYER", "L-2026-001");
        var broiler = setupBatch("BROILER", "B-2026-001");
        Map<String, Object> eggPreview = importer.preview(new CsvImportRequest("egg-collections", List.of(
                Map.of("batch_code", broiler.code(), "record_date", "2026-01-02",
                        "good_eggs", "5", "cracked_eggs", "1", "notes", ""))));
        Map<String, Object> weightPreview = importer.preview(new CsvImportRequest("broiler-weights", List.of(
                Map.of("batch_code", layer.code(), "record_date", "2026-01-02",
                        "sample_quantity", "5", "total_weight_kg", "8.500", "notes", ""))));
        assertThat(eggPreview.get("valid")).isEqualTo(false);
        assertThat(weightPreview.get("valid")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM egg_collections", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM weight_records", Integer.class)).isZero();
    }

    @Test
    void importsBroilerWeightsThroughExistingServiceAndSkipsExactRetry() {
        var batch = setupBatch("BROILER", "B-2026-001");
        CsvImportRequest request = new CsvImportRequest("broiler-weights", List.of(
                Map.of("batch_code", batch.code(), "record_date", "2026-01-02",
                        "sample_quantity", "10", "total_weight_kg", "18.500", "notes", "Historical sample")));
        assertThat(importer.importRecords(request).get("imported")).isEqualTo(1);
        assertThat(importer.importRecords(request).get("duplicates")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM weight_records", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'WEIGHT_RECORD'", Integer.class)).isEqualTo(1);
    }

    @Test
    void failedMultiRowEggImportRollsBackCollectionsAndAuditRecords() {
        var batch = setupBatch("LAYER", "L-2026-001");
        jdbc.execute("""
                CREATE TRIGGER fail_egg_import_row
                BEFORE INSERT ON egg_collections
                WHEN NEW.notes = 'FAIL'
                BEGIN
                    SELECT RAISE(ABORT, 'intentional integration-test failure');
                END
                """);
        CsvImportRequest request = new CsvImportRequest("egg-collections", List.of(
                Map.of("batch_code", batch.code(), "record_date", "2026-01-02",
                        "good_eggs", "10", "cracked_eggs", "1", "notes", "first"),
                Map.of("batch_code", batch.code(), "record_date", "2026-01-03",
                        "good_eggs", "20", "cracked_eggs", "2", "notes", "FAIL")));
        assertThatThrownBy(() -> importer.importRecords(request)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM egg_collections", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'EGG_COLLECTION'", Integer.class)).isZero();
    }

    @Test
    void rejectsRecordsBeforePlacementDate() {
        var batch = setupBatch("BROILER", "B-2026-001");
        Map<String, Object> preview = importer.preview(new CsvImportRequest("broiler-weights", List.of(
                Map.of("batch_code", batch.code(), "record_date", "2025-12-31",
                        "sample_quantity", "10", "total_weight_kg", "18.500", "notes", ""))));
        assertThat(preview.get("valid")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM weight_records", Integer.class)).isZero();
    }

    private BatchResponse setupBatch(String type, String code) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM farms", Integer.class) == 0) {
            farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        }
        var house = houseService.create(new HouseCreateRequest("House " + code, "H-" + code, null));
        return batchService.create(new BatchCreateRequest(
                type, LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));
    }
}
