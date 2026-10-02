package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.EggManagementService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
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
        "spring.datasource.url=jdbc:sqlite:file:egg-management-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EggManagementIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired EggManagementService eggService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void recordsLayerEggsAndTracksGoodCrackedAndRemaining() {
        var batch = setupLayer();

        eggService.addCollection(batch.id(), new EggCollectionRequest(
                LocalDate.of(2026, 2, 1), 300, 12, "Morning collection"));
        eggService.addCollection(batch.id(), new EggCollectionRequest(
                LocalDate.of(2026, 2, 1), 200, 8, "Evening collection"));

        var inventory = eggService.inventory(batch.id(), LocalDate.of(2026, 2, 1));
        assertThat(inventory.goodCollected()).isEqualTo(500);
        assertThat(inventory.crackedCollected()).isEqualTo(20);
        assertThat(inventory.goodSold()).isZero();
        assertThat(inventory.goodRemaining()).isEqualTo(500);
        assertThat(inventory.totalCollected()).isEqualTo(520);
    }

    @Test
    void sellsHalfCrateUsingConfiguredThirtyEggCrate() {
        var batch = setupLayer();
        eggService.addCollection(batch.id(), new EggCollectionRequest(
                LocalDate.of(2026, 2, 1), 100, 5, null));

        var sale = eggService.addSale(batch.id(), new EggSaleRequest(
                LocalDate.of(2026, 2, 1), "Customer A", new BigDecimal("0.5"), 300_000L));

        assertThat(sale.soldEggs()).isEqualTo(15);
        assertThat(sale.crateSize()).isEqualTo(30);
        assertThat(sale.totalAmountMinor()).isEqualTo(150_000L);

        var inventory = eggService.inventory(batch.id(), LocalDate.of(2026, 2, 1));
        assertThat(inventory.goodRemaining()).isEqualTo(85);
        assertThat(inventory.crackedCollected()).isEqualTo(5);
    }

    @Test
    void crackedEggsCannotIncreaseSellableInventory() {
        var batch = setupLayer();
        eggService.addCollection(batch.id(), new EggCollectionRequest(
                LocalDate.of(2026, 2, 1), 0, 50, null));

        assertThatThrownBy(() -> eggService.addSale(batch.id(), new EggSaleRequest(
                LocalDate.of(2026, 2, 1), "Customer A", new BigDecimal("0.5"), 300_000L)))
                .hasMessage("The sale exceeds good eggs available on that date.");
    }

    @Test
    void broilerCannotUseEggFeatures() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        assertThatThrownBy(() -> eggService.inventory(batch.id(), LocalDate.of(2026, 2, 1)))
                .hasMessage("Egg production is only available for LAYER batches.");
    }

    @Test
    void configurableCrateSizeIsUsedForSales() {
        setupFarm();
        farmService.updateSettings(new FarmSettingsRequest(24, null, java.util.List.of(25, 50)));
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        eggService.addCollection(batch.id(), new EggCollectionRequest(
                LocalDate.of(2026, 2, 1), 120, 0, null));
        var sale = eggService.addSale(batch.id(), new EggSaleRequest(
                LocalDate.of(2026, 2, 1), "Customer B", new BigDecimal("2.5"), 240_000L));

        assertThat(sale.soldEggs()).isEqualTo(60);
        assertThat(sale.crateSize()).isEqualTo(24);
        assertThat(sale.totalAmountMinor()).isEqualTo(600_000L);
    }

    @Test
    void saleCannotExceedGoodEggsAvailableOnSaleDate() {
        var batch = setupLayer();
        eggService.addCollection(batch.id(), new EggCollectionRequest(
                LocalDate.of(2026, 2, 2), 30, 0, null));

        assertThatThrownBy(() -> eggService.addSale(batch.id(), new EggSaleRequest(
                LocalDate.of(2026, 2, 1), "Customer C", new BigDecimal("1"), 300_000L)))
                .hasMessage("The sale exceeds good eggs available on that date.");
    }

    private BatchResponse setupLayer() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        return batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 500, null, 5_000_000L));
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
