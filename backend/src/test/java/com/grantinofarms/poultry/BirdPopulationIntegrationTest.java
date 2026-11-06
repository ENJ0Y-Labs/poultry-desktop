package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BirdPopulationEventRequest;
import com.grantinofarms.poultry.dto.BirdTransferRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.BirdPopulationService;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import com.grantinofarms.poultry.service.CurrentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.AfterEach;
import java.util.UUID;
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
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearCurrentUser() { CurrentUserContext.clear(); }

    @Test
    void recordsPopulationEventsAndCalculatesHistoricalState() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Layer House", "LH1", null));
        var batch = batchService.create(new com.grantinofarms.poultry.dto.BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 500, null, 5_000_000L));

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
    void estimatedCountCanBePhysicallyVerifiedWithoutRewritingInitialPopulationOrMortality() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH-EST", null));
        var batch = batchService.create(new com.grantinofarms.poultry.dto.BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 11, 1), house.id(), 700, null, false, null, "ESTIMATED"));
        populationService.addMortality(batch.id(),
                new BirdPopulationEventRequest(LocalDate.of(2026, 11, 2), 10, "Recorded mortality"));
        assertThat(populationService.get(batch.id(), LocalDate.of(2026, 11, 2)).currentBirds()).isEqualTo(690);
        assertThat(populationService.get(batch.id(), LocalDate.of(2026, 11, 2)).countStatus()).isEqualTo("ESTIMATED");

        String userId = UUID.randomUUID().toString();
        String now = "2026-11-06T00:00:00Z";
        jdbc.update("INSERT INTO users (id,email,password_hash,full_name,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                userId, "owner-175@example.test", "test-hash", "Test Owner", now, now);
        CurrentUserContext.set(userId);

        var adjustment = populationService.adjustPhysicalCount(batch.id(),
                new com.grantinofarms.poultry.dto.PopulationAdjustmentRequest(
                        LocalDate.of(2026, 11, 6), 680, "Physical head count completed"));
        var verified = populationService.get(batch.id(), LocalDate.of(2026, 11, 6));
        assertThat(adjustment.previousCount()).isEqualTo(690);
        assertThat(adjustment.newCount()).isEqualTo(680);
        assertThat(adjustment.difference()).isEqualTo(-10);
        assertThat(adjustment.userId()).isEqualTo(userId);
        assertThat(verified.initialBirds()).isEqualTo(700);
        assertThat(verified.mortality()).isEqualTo(10);
        assertThat(verified.currentBirds()).isEqualTo(680);
        assertThat(verified.countStatus()).isEqualTo("VERIFIED");
        assertThat(populationService.adjustments(batch.id())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type='BIRD_POPULATION_ADJUSTMENT'", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsImpossiblePopulationChanges() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Broiler House", "BH1", null));
        var batch = batchService.create(new com.grantinofarms.poultry.dto.BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

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
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 200, null, 2_000_000L));
        var target = batchService.create(new com.grantinofarms.poultry.dto.BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

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
