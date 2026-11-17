package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.BatchReopenRequest;
import com.grantinofarms.poultry.dto.BirdPopulationEventRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.BirdPopulationService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:batch-management-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BatchManagementIntegrationTest {

    @Autowired BatchService batchService;
    @Autowired BirdPopulationService populationService;
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired JdbcTemplate jdbc;
    @Autowired DashboardService dashboardService;
    @Autowired ReportService reportService;

    @Test
    void createsTransactionalCodesByTypeAndYear() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Layer House", "LH1", null));

        var layerOne = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 10), house.id(), 1000, null, 5_000_000L));
        var layerTwo = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 2, 10), house.id(), 900, null, 4_500_000L));
        var broilerOne = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 3, 10), house.id(), 500, null, 2_500_000L));

        assertThat(layerOne.code()).isEqualTo("L-2026-001");
        assertThat(layerTwo.code()).isEqualTo("L-2026-002");
        assertThat(broilerOne.code()).isEqualTo("B-2026-001");
        assertThat(layerOne.status()).isEqualTo("ACTIVE");
        assertThat(layerOne.purchaseCostMinor()).isEqualTo(5_000_000L);
        Long unitCost = jdbc.queryForObject(
                "SELECT unit_cost_minor FROM bird_purchases WHERE batch_id = ?",
                Long.class,
                layerOne.id()
        );
        assertThat(unitCost).isEqualTo(5_000L);
    }


    @Test
    void registersExistingStockWithoutCreatingPurchaseOrExpenseRows() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 6, 1), house.id(), 250, null, false, null
        ));

        assertThat(batch.purchaseCostMinor()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bird_purchases WHERE batch_id = ?",
                Integer.class, batch.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE batch_id = ?",
                Integer.class, batch.id())).isZero();
    }

    @Test
    void rejectsPurchaseCostWhenModeAndAmountDisagree() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));

        assertThatThrownBy(() -> batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 6, 1), house.id(), 100, null, true, null
        ))).hasMessage("Record purchase cost requires an actual cost. Existing stock must not include a purchase cost.");
        assertThatThrownBy(() -> batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 6, 1), house.id(), 100, null, false, 100L
        ))).hasMessage("Record purchase cost requires an actual cost. Existing stock must not include a purchase cost.");
    }

    @Test
    void acceptsPurchaseCostWithRemainderAndPreservesExactTotal() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Layer House", "LH1", null));

        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 6, 1), house.id(), 3, null, 100L
        ));

        assertThat(batch.purchaseCostMinor()).isEqualTo(100L);
        Long unitCost = jdbc.queryForObject(
                "SELECT unit_cost_minor FROM bird_purchases WHERE batch_id = ?",
                Long.class,
                batch.id()
        );
        Long totalCost = jdbc.queryForObject(
                "SELECT total_cost_minor FROM bird_purchases WHERE batch_id = ?",
                Long.class,
                batch.id()
        );

        assertThat(unitCost).isEqualTo(33L);
        assertThat(totalCost).isEqualTo(100L);
    }

    @Test
    void soldBroilerCanOnlyReopenWithReasonAndReopenIsAudited() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Broiler House", "BH1", null));

        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 4, 1), house.id(), 300, null, 3_000_000L));

        populationService.addSale(
                batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 4, 2), 300, "All birds sold")
        );

        var sold = batchService.markSold(batch.id());
        assertThat(sold.status()).isEqualTo("SOLD");

        assertThatThrownBy(() -> batchService.reopen(
                batch.id(), new BatchReopenRequest("   ")))
                .hasMessage("A reason is required to reopen a SOLD batch.");

        var reopened = batchService.reopen(
                batch.id(), new BatchReopenRequest("Corrected an accidental sale entry."));
        assertThat(reopened.status()).isEqualTo("ACTIVE");

        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_logs
                WHERE entity_type = 'BATCH' AND entity_id = ? AND action = 'REOPEN'
                """, Integer.class, batch.id());
        String reason = jdbc.queryForObject("""
                SELECT reason FROM audit_logs
                WHERE entity_type = 'BATCH' AND entity_id = ? AND action = 'REOPEN'
                ORDER BY occurred_at DESC
                LIMIT 1
                """, String.class, batch.id());

        assertThat(auditCount).isEqualTo(1);
        assertThat(reason).isEqualTo("Corrected an accidental sale entry.");
    }

    @Test
    void layerCannotUseSoldLifecycleInThisStage() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Layer House", "LH1", null));

        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 5, 1), house.id(), 100, null, 1_000_000L));

        assertThatThrownBy(() -> batchService.markSold(batch.id()))
                .hasMessage("Only meat-production batches (BROILER or TURKEY) use the SOLD terminal lifecycle.");
    }

    @Test
    void retriesWithSameIdempotencyKeyReturnOriginalBatchButNewKeysCreateNewBatches() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var request = new BatchCreateRequest("LAYER", LocalDate.of(2026, 7, 1), house.id(), 250, null, false, null);
        var first = batchService.create(request, "batch-create-retry-1");
        var retry = batchService.create(request, "batch-create-retry-1");
        var intentional = batchService.create(request, "batch-create-retry-2");
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(retry.code()).isEqualTo(first.code());
        assertThat(intentional.id()).isNotEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batches", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> batchService.create(
                new BatchCreateRequest("LAYER", LocalDate.of(2026, 7, 2), house.id(), 250, null, false, null),
                "batch-create-retry-1"))
                .hasMessageContaining("This retry key belongs to another request");
    }

    @Test
    void cancelsEmptyBatchWithReasonAndKeepsAuditedHistory() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest("LAYER", LocalDate.of(2026, 7, 1), house.id(), 250, null, false, null));
        var cancelled = batchService.cancel(batch.id(), "Duplicate created after an unclear save result.");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batches WHERE id=?", Integer.class, batch.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_id=? AND action='CANCEL' AND reason=?", Integer.class, batch.id(), "Duplicate created after an unclear save result.")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT before_json FROM audit_logs WHERE entity_id=? AND action='CANCEL'", String.class, batch.id())).contains("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT after_json FROM audit_logs WHERE entity_id=? AND action='CANCEL'", String.class, batch.id())).contains("CANCELLED");
        assertThat(((Number) dashboardService.farm(LocalDate.of(2026, 7, 2)).get("totalBirds")).longValue()).isZero();
        assertThat(((List<?>) reportService.farm(LocalDate.of(2026, 7, 2)).get("birdPopulation"))).isEmpty();
    }

    @Test
    void refusesToCancelBatchWithFinancialOrOperationalReferences() {
        farmService.create(new FarmCreateRequest("Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest("LAYER", LocalDate.of(2026, 7, 1), house.id(), 250, null, 2_500_000L));
        assertThatThrownBy(() -> batchService.cancel(batch.id(), "Accidental duplicate."))
                .hasMessageContaining("bird_purchases");
        assertThat(batchService.get(batch.id()).status()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_id=? AND action='CANCEL'", Integer.class, batch.id())).isZero();
    }
    @Test
    void createsIndependentTurkeyBatchWithTurkeyCodeAndPopulation() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Turkey House", "TH1", null));
        var turkey = batchService.create(new BatchCreateRequest(
                "TURKEY", LocalDate.of(2026, 6, 1), house.id(), 75, null, 750_000L));
        assertThat(turkey.type()).isEqualTo("TURKEY");
        assertThat(turkey.code()).isEqualTo("TY-2026-001");
        assertThat(populationService.get(turkey.id(), LocalDate.of(2026, 6, 1)).currentBirds()).isEqualTo(75);
    }
}
