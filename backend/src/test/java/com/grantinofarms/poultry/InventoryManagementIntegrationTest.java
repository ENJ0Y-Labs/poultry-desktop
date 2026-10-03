package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:inventory-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InventoryManagementIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired InventoryService inventoryService;
    @Autowired BatchService batchService;

    @Test
    void recordsReceiptsIssuesAndShowsRemainingStock() {
        setupFarm();

        var item = inventoryService.createItem(new InventoryItemCreateRequest(
                "Amprolium", "DRUG", "bottle", new BigDecimal("5")));

        inventoryService.recordMovement(item.id(), new InventoryMovementRequest(
                LocalDate.of(2026, 10, 1), "RECEIVE", new BigDecimal("10"),
                "Purchased for upcoming treatments", "SUPPLIER_INVOICE: INV-001", null));

        inventoryService.recordMovement(item.id(), new InventoryMovementRequest(
                LocalDate.of(2026, 10, 2), "ISSUE", new BigDecimal("2"),
                "Treatment for Layer batch", "HEALTH_TREATMENT", null));

        var current = inventoryService.listItems().getFirst();
        assertThat(current.quantityOnHand()).isEqualByComparingTo("8");
        assertThat(current.reorderLevel()).isEqualByComparingTo("5");

        var movements = inventoryService.movements(item.id(), null);
        assertThat(movements).hasSize(2);
        assertThat(movements).allSatisfy(m -> {
            assertThat(m.reason()).isNotBlank();
            assertThat(m.source()).isNotBlank();
        });
    }

    @Test
    void rejectsMovementThatWouldMakeStockNegative() {
        setupFarm();

        var item = inventoryService.createItem(new InventoryItemCreateRequest(
                "Newcastle Vaccine", "VACCINE", "dose", BigDecimal.ZERO));

        assertThatThrownBy(() -> inventoryService.recordMovement(item.id(), new InventoryMovementRequest(
                LocalDate.of(2026, 10, 2), "ISSUE", new BigDecimal("1"),
                "Vaccination", "VACCINATION_RECORD", null)))
                .hasMessageContaining("would make inventory negative");
    }

    @Test
    void batchAssociationMustBelongToTheFarm() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 10, 1), house.id(), 100, null, 1_000_000L));

        var item = inventoryService.createItem(new InventoryItemCreateRequest(
                "Egg Trays", "SUPPLY", "piece", BigDecimal.ZERO));

        inventoryService.recordMovement(item.id(), new InventoryMovementRequest(
                LocalDate.of(2026, 10, 1), "RECEIVE", new BigDecimal("100"),
                "Initial tray stock", "SUPPLIER_INVOICE: INV-002", batch.id()));

        assertThat(inventoryService.listItems().getFirst().quantityOnHand())
                .isEqualByComparingTo("100");
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }

    private record BatchServiceTestHelper() {
        BatchResponse create(BatchService ignored, String houseId) {
            throw new UnsupportedOperationException();
        }
    }
}
