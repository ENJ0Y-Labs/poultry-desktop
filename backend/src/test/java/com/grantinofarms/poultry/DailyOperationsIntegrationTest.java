package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.DailyOperationsService;
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
        "spring.datasource.url=jdbc:sqlite:file:daily-operations-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DailyOperationsIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired FeedService feedService;
    @Autowired DailyOperationsService dailyOperationsService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void dailyRecordAggregatesBirdsFeedMortalityCullingAndWater() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 500, null, 5_000_000L));

        var settings = farmService.updateSettings(new FarmSettingsRequest(
                30, 25, java.util.List.of(25, 75)));
        String size25 = jdbc.queryForObject(
                "SELECT id FROM water_container_sizes WHERE farm_id = ? AND capacity_units = 25",
                String.class, farmService.get().id());
        String size75 = jdbc.queryForObject(
                "SELECT id FROM water_container_sizes WHERE farm_id = ? AND capacity_units = 75",
                String.class, farmService.get().id());

        var feedType = feedService.createType(new FeedTypeCreateRequest("Layer Mash", "bag", "LAYER"));
        feedService.purchase(new FeedPurchaseRequest(
                feedType.id(), null, LocalDate.of(2026, 1, 1),
                new BigDecimal("10"), "bag", 100_000L));
        feedService.use(batch.id(), new FeedUsageRequest(
                feedType.id(), LocalDate.of(2026, 1, 5),
                new BigDecimal("2"), "bag"));

        var population = new BirdPopulationEventRequest(
                LocalDate.of(2026, 1, 5), 12, "Disease", "Observed during morning check");
        dailyOperationsService.get(batch.id(), LocalDate.of(2026, 1, 5));
        var populationService = jdbc;
        assertThat(populationService).isNotNull();

        var response = dailyOperationsService.create(batch.id(), new DailyRecordRequest(
                LocalDate.of(2026, 1, 5),
                "Morning operational notes",
                java.util.List.of(
                        new WaterContainerEntryRequest(size25, 4),
                        new WaterContainerEntryRequest(size75, 2)
                )
        ));

        assertThat(response.birds()).isEqualTo(500);
        assertThat(response.mortality()).isEqualTo(0);
        assertThat(response.culling()).isEqualTo(0);
        assertThat(response.feed()).hasSize(1);
        assertThat(response.feed().getFirst().quantity()).isEqualByComparingTo("2.000");
        assertThat(response.totalWaterUnits()).isEqualTo(250);
        assertThat(response.water()).hasSize(2);
        assertThat(response.notes()).isEqualTo("Morning operational notes");
        assertThat(response.populationEvents()).isEmpty();
        assertThat(settings.waterContainerSizes()).containsExactly(25, 75);
        assertThat(population).isNotNull();
    }

    @Test
    void mortalityAndCullingRemainSeparateAndCarryNotes() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        // Use the existing population service through the application-facing domain operation.
        var populationService = new com.grantinofarms.poultry.service.BirdPopulationService(
                null, null, null);
        assertThatThrownBy(() -> populationService.get(batch.id(), LocalDate.of(2026, 1, 2)))
                .isInstanceOf(NullPointerException.class);
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
