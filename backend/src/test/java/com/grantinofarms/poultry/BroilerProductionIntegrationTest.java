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
        "spring.datasource.url=jdbc:sqlite:file:broiler-production-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BroilerProductionIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired FeedService feedService;
    @Autowired BroilerProductionService broilerService;

    @Test
    void recordsWeightsCalculatesGrowthAndFcr() {
        var batch = setupBroiler();

        var first = broilerService.addWeight(batch.id(), new WeightRecordRequest(
                LocalDate.of(2026, 1, 8), 10, new BigDecimal("10.000"), "Baseline"));
        var second = broilerService.addWeight(batch.id(), new WeightRecordRequest(
                LocalDate.of(2026, 1, 15), 10, new BigDecimal("30.000"), "Weekly"));

        assertThat(first.averageWeightKg()).isEqualByComparingTo("1.0");
        assertThat(second.averageWeightKg()).isEqualByComparingTo("3.0");
        assertThat(second.weightGainKg()).isEqualByComparingTo("2.0");

        var feedType = feedService.createType(new FeedTypeCreateRequest("Broiler Grower", "kg", "BROILER"));
        feedService.purchase(new FeedPurchaseRequest(
                feedType.id(), null, LocalDate.of(2026, 1, 2),
                new BigDecimal("500"), "kg", 500_000L));
        feedService.use(batch.id(), new FeedUsageRequest(
                feedType.id(), LocalDate.of(2026, 1, 15),
                new BigDecimal("400"), "kg"));

        var growth = broilerService.growth(batch.id(), LocalDate.of(2026, 1, 15));
        assertThat(growth.averageWeightKg()).isEqualByComparingTo("3.0");
        assertThat(growth.weightGainKg()).isEqualByComparingTo("200.0");
        assertThat(growth.fcr()).isEqualByComparingTo("2.0");
        assertThat(growth.trend()).hasSize(2);
    }

    @Test
    void birdSalesReducePopulationAndAutomaticallyLockWhenAllBirdsAreSold() {
        var batch = setupBroiler();

        var firstSale = broilerService.addSale(batch.id(), new BirdSaleRequest(
                LocalDate.of(2026, 1, 10), 40, 25_000L, "Customer A"));
        assertThat(firstSale.totalAmountMinor()).isEqualTo(1_000_000L);

        var secondSale = broilerService.addSale(batch.id(), new BirdSaleRequest(
                LocalDate.of(2026, 1, 20), 60, 25_000L, "Customer B"));
        assertThat(secondSale.quantity()).isEqualTo(60);

        assertThat(batchService.get(batch.id()).status()).isEqualTo("SOLD");
        assertThatThrownBy(() -> broilerService.addSale(batch.id(), new BirdSaleRequest(
                LocalDate.of(2026, 1, 21), 1, 25_000L, "Late customer")))
                .hasMessage("Broiler production records cannot be added to a non-ACTIVE batch.");
    }

    @Test
    void layerCannotUseBroilerProduction() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));
        assertThatThrownBy(() -> broilerService.weights(batch.id(), LocalDate.of(2026, 1, 10)))
                .hasMessage("This feature is only available for BROILER batches.");
    }

    private BatchResponse setupBroiler() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        return batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
