package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.CsvImportRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.BatchCsvImportService;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:batch-csv-import-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BatchCsvImportIntegrationTest {
    @Autowired BatchCsvImportService importer;
    @Autowired BatchService batches;
    @Autowired FarmService farms;
    @Autowired HouseService houses;

    private String houseId;

    @BeforeEach
    void setUp() {
        farms.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        houseId = houses.create(new HouseCreateRequest("Broiler House", "H-001", null)).id();
    }

    @Test
    void previewsAndImportsBatchOpeningRecordsThroughTheExistingBatchService() {
        CsvImportRequest request = new CsvImportRequest("batches", List.of(row(
                "BROILER", "2026-01-15", "H-001", "1000", "ESTIMATED", "", "true", "250000.00"
        )));

        Map<String, Object> preview = importer.preview(request);
        assertThat(preview.get("validRows")).isEqualTo(1);
        assertThat(preview.get("invalidRows")).isEqualTo(0);

        Map<String, Object> result = importer.importRecords(request);
        assertThat(result.get("imported")).isEqualTo(1);
        assertThat(batches.list()).hasSize(1);
        assertThat(batches.list().get(0).code()).isEqualTo("B-2026-001");
        assertThat(batches.list().get(0).houseId()).isEqualTo(houseId);
        assertThat(batches.list().get(0).purchaseCostMinor()).isEqualTo(25_000_000L);
    }

    @Test
    void rejectsDuplicateRowsInOneFileDuringPreview() {
        CsvImportRequest request = new CsvImportRequest("batches", List.of(
                row("BROILER", "2026-01-15", "H-001", "1000", "ESTIMATED", "", "false", ""),
                row("BROILER", "2026-01-15", "H-001", "1000", "ESTIMATED", "", "false", "")
        ));

        Map<String, Object> preview = importer.preview(request);
        assertThat(preview.get("validRows")).isEqualTo(1);
        assertThat(preview.get("invalidRows")).isEqualTo(1);
        assertThat(batches.list()).isEmpty();
    }

    @Test
    void rejectsUnknownHouseAndInvalidPurchaseCostWithoutWritingBatches() {
        CsvImportRequest request = new CsvImportRequest("batches", List.of(
                row("LAYER", "2026-01-15", "MISSING", "500", "ESTIMATED", "", "true", "12.345")
        ));

        Map<String, Object> preview = importer.preview(request);
        assertThat(preview.get("validRows")).isEqualTo(0);
        assertThat(preview.get("invalidRows")).isEqualTo(1);
        assertThat(batches.list()).isEmpty();
    }

    private Map<String, String> row(String type, String date, String houseCode, String count,
                                    String confidence, String supplier, String costRecorded, String costNaira) {
        return Map.of(
                "type", type,
                "placement_date", date,
                "house_code", houseCode,
                "initial_bird_count", count,
                "population_count_status", confidence,
                "supplier_name", supplier,
                "purchase_cost_recorded", costRecorded,
                "purchase_cost_naira", costNaira
        );
    }
}
