package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.FeedService;
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
        "spring.datasource.url=jdbc:sqlite:file:feed-management-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FeedManagementIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired FeedService feedService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void purchaseCreatesInventoryAndOneExpense() {
        setupFarm();
        var type = feedService.createType(new FeedTypeCreateRequest("Starter", "bag", "BROILER"));

        var purchase = feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,1), new BigDecimal("20"), "bag", 160_000L));

        assertThat(purchase.quantityMilli()).isEqualTo(20_000L);
        assertThat(feedService.inventory(LocalDate.of(2026,1,1)).get(0).remainingQuantityMilli())
                .isEqualTo(20_000L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE reference_type = 'FEED_PURCHASE' AND reference_id = ?",
                Integer.class, purchase.id())).isEqualTo(1);
    }

    @Test
    void fifoUsageUsesMultiplePricesAndDoesNotCreateAnotherExpense() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        var type = feedService.createType(new FeedTypeCreateRequest("Finisher", "bag", "BROILER"));

        feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,1), new BigDecimal("20"), "bag", 160_000L));
        feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,2), new BigDecimal("30"), "bag", 300_000L));

        var usage = feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,3), new BigDecimal("25"), "bag"));

        assertThat(usage.quantityMilli()).isEqualTo(25_000L);
        assertThat(usage.feedCostMinor()).isEqualTo(210_000L);

        var inventory = feedService.inventory(LocalDate.of(2026,1,3)).get(0);
        assertThat(inventory.remainingQuantityMilli()).isEqualTo(25_000L);
        assertThat(inventory.remainingCostMinor()).isEqualTo(250_000L);
        assertThat(inventory.consumedCostMinor()).isEqualTo(210_000L);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE category = 'FEED'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void usageCannotExceedAvailableFeed() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        var type = feedService.createType(new FeedTypeCreateRequest("Layer Mash", "kg", "LAYER"));

        feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,1), new BigDecimal("10"), "kg", 100_000L));

        assertThatThrownBy(() -> feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,2), new BigDecimal("10.001"), "kg")))
                .hasMessage("Insufficient feed inventory for FIFO consumption.");
    }

    @Test
    void feedCostIsAddedOnceToBatchBirdCost() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        var type = feedService.createType(new FeedTypeCreateRequest("Grower", "bag", "LAYER"));

        feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,1), new BigDecimal("10"), "bag", 100_000L));
        feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,2), new BigDecimal("5"), "bag"));

        var cost = feedService.batchCost(batch.id(), LocalDate.of(2026,1,2));
        assertThat(cost.feedCostMinor()).isEqualTo(50_000L);
        assertThat(cost.feedCostPerBirdMinor()).isEqualTo(500L);
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
