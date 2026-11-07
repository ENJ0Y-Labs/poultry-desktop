package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:master-record-editing-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MasterRecordEditingIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired FeedService feedService;
    @Autowired SupplierService supplierService;
    @Autowired InventoryService inventoryService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void feedDefinitionCanChangeDescriptiveFieldsAndAuditsBeforeAndAfter() {
        setupFarm();
        var type = feedService.createType(new FeedTypeCreateRequest(
                "Layer Mash", "bag", "LAYER", null, "Brand A", "25kg bag", new BigDecimal("25")));

        var updated = feedService.updateType(type.id(), new FeedTypeUpdateRequest(
                "Premium Layer Mash", "bag", "LAYER", "Brand B", "25kg bag", new BigDecimal("25")));

        assertThat(updated.name()).isEqualTo("Premium Layer Mash");
        assertThat(updated.brand()).isEqualTo("Brand B");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type='FEED_TYPE' AND entity_id=? AND action='UPDATE'",
                Integer.class, type.id())).isEqualTo(1);
    }

    @Test
    void feedUnitCannotChangeAfterAnOpeningBalanceExists() {
        setupFarm();
        var type = feedService.createType(new FeedTypeCreateRequest("Layer Mash", "bag", "LAYER"));
        feedService.createOpeningBalance(new FeedOpeningBalanceRequest(
                type.id(), LocalDate.of(2026, 11, 1), new BigDecimal("10"), "bag", "Opening stock count"));

        assertThatThrownBy(() -> feedService.updateType(type.id(), new FeedTypeUpdateRequest(
                "Layer Mash", "kg", "LAYER", null, null, null)))
                .hasMessageContaining("cannot change after purchases or usage exist");
    }

    @Test
    void supplierCanBeEditedWithoutChangingItsIdentityAndAuditIsWritten() {
        setupFarm();
        var supplier = supplierService.create(new SupplierCreateRequest(
                "Feed Supplier", "08010000000", "supplier@example.com", "Port Harcourt", "Original notes"));

        var updated = supplierService.update(supplier.id(), new SupplierUpdateRequest(
                "Feed Supplier Ltd", "08020000000", "accounts@example.com", "Rumuokoro", "Updated notes"));

        assertThat(updated.id()).isEqualTo(supplier.id());
        assertThat(updated.name()).isEqualTo("Feed Supplier Ltd");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type='SUPPLIER' AND entity_id=? AND action='UPDATE'",
                Integer.class, supplier.id())).isEqualTo(1);
    }

    @Test
    void inventoryItemCanEditMetadataButCannotRewriteUnitAfterMovements() {
        setupFarm();
        var item = inventoryService.createItem(new InventoryItemCreateRequest(
                "Disinfectant", "SUPPLY", "litre", new BigDecimal("5")));
        inventoryService.recordMovement(item.id(), new InventoryMovementRequest(
                LocalDate.of(2026, 11, 1), "RECEIVE", new BigDecimal("20"),
                "Initial stock", "SUPPLIER_INVOICE", null));

        var updated = inventoryService.updateItem(item.id(), new InventoryItemUpdateRequest(
                "Farm Disinfectant", "SUPPLY", "litre", new BigDecimal("8")));
        assertThat(updated.name()).isEqualTo("Farm Disinfectant");
        assertThat(updated.quantityOnHand()).isEqualByComparingTo("20");

        assertThatThrownBy(() -> inventoryService.updateItem(item.id(), new InventoryItemUpdateRequest(
                "Farm Disinfectant", "SUPPLY", "bottle", new BigDecimal("8"))))
                .hasMessageContaining("unit cannot change after stock movements exist");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type='INVENTORY_ITEM' AND entity_id=? AND action='UPDATE'",
                Integer.class, item.id())).isEqualTo(1);
    }

    @Test
    void batchSetupCanChangeHouseWithoutChangingImmutableIdentityOrInitialPopulation() {
        setupFarm();
        var firstHouse = houseService.create(new HouseCreateRequest("House A", "H001", null));
        var secondHouse = houseService.create(new HouseCreateRequest("House B", "H002", null));
        var supplier = supplierService.create(new SupplierCreateRequest("Bird Supplier", null, null, null, null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 11, 1), firstHouse.id(), 300, supplier.id(), false, null));

        var updated = batchService.updateSetup(batch.id(), new BatchSetupUpdateRequest(secondHouse.id(), supplier.id()));

        assertThat(updated.houseId()).isEqualTo(secondHouse.id());
        assertThat(updated.code()).isEqualTo(batch.code());
        assertThat(updated.initialBirdCount()).isEqualTo(300);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type='BATCH' AND entity_id=? AND action='UPDATE'",
                Integer.class, batch.id())).isEqualTo(1);
    }

    @Test
    void batchSetupCannotBeChangedAfterBatchIsSold() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("House A", "H001", null));
        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 11, 1), house.id(), 10, null, false, null));
        jdbc.update("UPDATE batches SET status='SOLD' WHERE id=?", batch.id());

        assertThatThrownBy(() -> batchService.updateSetup(batch.id(),
                new BatchSetupUpdateRequest(house.id(), null)))
                .hasMessageContaining("Only active batches");
    }

    @Test
    void houseWithAnActiveBatchCannotBeArchived() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Occupied House", "H001", null));
        batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 11, 1), house.id(), 50, null, false, null));

        assertThatThrownBy(() -> houseService.update(house.id(), new HouseUpdateRequest(
                "Occupied House", "H001", null, "ARCHIVED", "PERMANENT")))
                .hasMessageContaining("assigned to an active batch cannot be archived");
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
