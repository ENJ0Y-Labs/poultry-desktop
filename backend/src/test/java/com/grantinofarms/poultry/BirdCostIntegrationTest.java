package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.BirdAdditionalCostRequest;
import com.grantinofarms.poultry.dto.BirdPopulationEventRequest;
import com.grantinofarms.poultry.dto.BirdTransferRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.BirdCostService;
import com.grantinofarms.poultry.service.BirdPopulationService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:bird-cost-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BirdCostIntegrationTest {

    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired BirdPopulationService populationService;
    @Autowired BirdCostService costService;

    @Test
    void recordsPurchaseCostAndCarriesCostThroughSale() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Broiler House", "BH1", null));

        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        populationService.addSale(
                batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 1, 10), 20, "Partial sale")
        );

        var costs = costService.get(batch.id(), LocalDate.of(2026, 1, 10));

        assertThat(costs.initialPurchaseCostMinor()).isEqualTo(1_000_000L);
        assertThat(costs.soldBirdCostMinor()).isEqualTo(200_000L);
        assertThat(costs.carriedCostMinor()).isEqualTo(800_000L);
    }

    @Test
    void additionalCostIsAttributableToBirdsBeforeSale() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Layer House", "LH1", null));

        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 2, 1), house.id(), 100, null, 1_000_000L));

        costService.addAdditionalCost(
                batch.id(),
                new BirdAdditionalCostRequest(
                        LocalDate.of(2026, 2, 5), 200_000L, "Applicable farm cost"
                )
        );

        populationService.addSale(
                batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 2, 10), 20, "Partial sale")
        );

        var costs = costService.get(batch.id(), LocalDate.of(2026, 2, 10));

        assertThat(costs.additionalCostsMinor()).isEqualTo(200_000L);
        assertThat(costs.soldBirdCostMinor()).isEqualTo(240_000L);
        assertThat(costs.carriedCostMinor()).isEqualTo(960_000L);
    }

    @Test
    void transferCarriesAttributableCostToTargetBatch() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Transfer House", "TH1", null));

        var source = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 3, 1), house.id(), 100, null, 1_000_000L));
        var target = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 3, 1), house.id(), 50, null, 500_000L));

        populationService.transfer(
                source.id(),
                new BirdTransferRequest(
                        target.id(), LocalDate.of(2026, 3, 5), 20, "Move birds"
                )
        );

        assertThat(costService.get(source.id(), LocalDate.of(2026, 3, 5)).carriedCostMinor())
                .isEqualTo(800_000L);
        assertThat(costService.get(target.id(), LocalDate.of(2026, 3, 5)).carriedCostMinor())
                .isEqualTo(700_000L);
    }
}
