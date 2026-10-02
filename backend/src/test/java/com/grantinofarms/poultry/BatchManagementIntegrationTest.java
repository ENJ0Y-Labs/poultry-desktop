package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.BatchReopenRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:batch-management-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BatchManagementIntegrationTest {

    @Autowired BatchService batchService;
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsTransactionalCodesByTypeAndYear() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Layer House", "LH1", null));

        var layerOne = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 10), house.id(), 1000, null, 5_000_000L));
        var layerTwo = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 2, 10), house.id(), 900, null, null));
        var broilerOne = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 3, 10), house.id(), 500, null, 2_500_000L));

        assertThat(layerOne.code()).isEqualTo("L-2026-001");
        assertThat(layerTwo.code()).isEqualTo("L-2026-002");
        assertThat(broilerOne.code()).isEqualTo("B-2026-001");
        assertThat(layerOne.status()).isEqualTo("ACTIVE");
        assertThat(layerOne.purchaseCostMinor()).isEqualTo(5_000_000L);
    }

    @Test
    void soldBroilerCanOnlyReopenWithReasonAndReopenIsAudited() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Broiler House", "BH1", null));

        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 4, 1), house.id(), 300, null, null));

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
                "LAYER", LocalDate.of(2026, 5, 1), house.id(), 100, null, null));

        assertThatThrownBy(() -> batchService.markSold(batch.id()))
                .hasMessage("Only BROILER batches use the SOLD terminal lifecycle in this stage.");
    }
}
