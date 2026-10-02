package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BirdPopulationEventRequest;
import com.grantinofarms.poultry.dto.BirdTransferRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.BirdPopulationService;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:bird-population-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BirdPopulationIntegrationTest {

    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired BirdPopulationService populationService;

    @Test
    void recordsPopulationEventsAndCalculatesHistoricalState() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Layer House", "LH1", null));
        var batch = batchService.create(new com.grantinofarms.poultry.dto.BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 500, null, null));

        populationService.addMortality(
                batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 1, 5), 20, "Daily mortality")
        );
        populationService.addCulling(
                batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 1, 8), 10, "Weak birds")
        );
        populationService.addSale(
                batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 1, 10), 50, "Bird sale")
        );

        var historical = populationService.get(batch.id(), LocalDate.of(2026, 1, 7));
        assertThat(historical.currentBirds()).isEqualTo(480);

        var current = populationService.get(batch.id(), LocalDate.of(2026, 1, 10));
        assertThat(current.initialBirds()).isEqualTo(500);
        assertThat(current.mortality()).isEqualTo(20);
        assertThat(current.culling()).isEqualTo(10);
        assertThat(current.sold()).isEqualTo(50);
        assertThat(current.currentBirds()).isEqualTo(420);
    }

    @Test
    void rejectsImpossiblePopulationChanges() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Broiler House", "BH1", null));
        var batch = batchService.create(new com.grantinofarms.poultry.dto.BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, null));

        populationService.addMortality(
                batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 1, 2), 70, null)
        );

        assertThatThrownBy(() -> populationService.addCulling(
                batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 1, 3), 31, null)
        ))
                .hasMessage("culling quantity exceeds available birds.");

        assertThatThrownBy(() -> populationService.addSale(
                batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 1, 3), 31, null)
        ))
                .hasMessage("sold quantity exceeds available birds.");
    }

    @Test
    void supportsAtomicTransfersAndValidatesSourcePopulation() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Transfer House", "TH1", null));

        var source = batchService.create(new com.grantinofarms.poultry.dto.BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 200, null, null));
        var target = batchService.create(new com.grantinofarms.poultry.dto.BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, null));

        var result = populationService.transfer(
                source.id(),
                new BirdTransferRequest(
                        target.id(), LocalDate.of(2026, 1, 5), 50, "Moved birds"
                )
        );

        assertThat(result.currentBirds()).isEqualTo(150);
        assertThat(populationService.get(target.id(), LocalDate.of(2026, 1, 5)).currentBirds())
                .isEqualTo(150);

        assertThatThrownBy(() -> populationService.transfer(
                source.id(),
                new BirdTransferRequest(
                        target.id(), LocalDate.of(2026, 1, 6), 151, null
                )
        ))
                .hasMessage("transfer_out quantity exceeds available birds.");
    }
}
