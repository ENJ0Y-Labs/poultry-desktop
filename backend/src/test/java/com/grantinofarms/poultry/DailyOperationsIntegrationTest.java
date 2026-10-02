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
    @Autowired com.grantinofarms.poultry.service.BirdPopulationService birdPopulationService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void dailyRecordAggregatesBirdsFeedMortalityCullingAndWater() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 500, null, 5_000_000L));

        var settings = farmService.updateSettings(new FarmSettingsRequest(
                30, 25, java.util.List.of(25, 75)));

        var feedType = feedService.createType(new FeedTypeCreateRequest("Layer Mash", "bag", "LAYER"));
        feedService.purchase(new FeedPurchaseRequest(
                feedType.id(), null, LocalDate.of(2026, 1, 1),
                new BigDecimal("10"), "bag", 100_000L));
        feedService.use(batch.id(), new FeedUsageRequest(
                feedType.id(), LocalDate.of(2026, 1, 5),
                new BigDecimal("2"), "bag"));

        birdPopulationService.addMortality(batch.id(), new BirdPopulationEventRequest(
                LocalDate.of(2026, 1, 5), 12, "Disease", "Observed during morning check"));
        birdPopulationService.addCulling(batch.id(), new BirdPopulationEventRequest(
                LocalDate.of(2026, 1, 5), 3, "Weak birds", "Removed from flock"));
        var response = dailyOperationsService.create(batch.id(), new DailyRecordRequest(
                LocalDate.of(2026, 1, 5),
                "Morning operational notes",
                java.util.List.of(
                        new WaterContainerEntryRequest(25, 4),
                        new WaterContainerEntryRequest(75, 2)
                )
        ));

        assertThat(response.birds()).isEqualTo(485);
        assertThat(response.mortality()).isEqualTo(12);
        assertThat(response.culling()).isEqualTo(3);
        assertThat(response.feed()).hasSize(1);
        assertThat(response.feed().getFirst().quantity()).isEqualByComparingTo("2.000");
        assertThat(response.totalWaterUnits()).isEqualTo(250);
        assertThat(response.water()).hasSize(2);
        assertThat(response.notes()).isEqualTo("Morning operational notes");
        assertThat(response.populationEvents()).hasSize(2);
        assertThat(response.populationEvents().get(0).type()).isEqualTo("MORTALITY");
        assertThat(response.populationEvents().get(0).reason()).isEqualTo("Disease");
        assertThat(response.populationEvents().get(0).notes()).isEqualTo("Observed during morning check");
        assertThat(response.populationEvents().get(1).type()).isEqualTo("CULLING");
        assertThat(response.populationEvents().get(1).reason()).isEqualTo("Weak birds");
        assertThat(response.populationEvents().get(1).notes()).isEqualTo("Removed from flock");
        assertThat(settings.waterContainerSizes()).containsExactly(25, 75);
    }

    @Test
    void dailyRecordCannotBeDuplicatedForSameBatchAndDate() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        dailyOperationsService.create(batch.id(), new DailyRecordRequest(
                LocalDate.of(2026, 1, 2), "First record", java.util.List.of()
        ));

        assertThatThrownBy(() -> dailyOperationsService.create(batch.id(), new DailyRecordRequest(
                LocalDate.of(2026, 1, 2), "Duplicate", java.util.List.of()
        )))
                .hasMessage("A daily record already exists for this batch and date.");
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
