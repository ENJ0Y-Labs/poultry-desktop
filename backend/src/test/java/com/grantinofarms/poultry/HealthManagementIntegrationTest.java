package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HealthManagementService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:health-management-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HealthManagementIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired HealthManagementService healthService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void healthDrugAndVaccinationRecordsAreStoredSeparately() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 500, null, 5_000_000L));

        var health = healthService.addHealth(batch.id(), new HealthRecordRequest(
                LocalDate.of(2026, 1, 5),
                "Respiratory symptoms",
                "Several birds showed coughing.",
                "Isolated affected birds and monitored flock."
        ));
        var drug = healthService.addDrug(batch.id(), new DrugRecordRequest(
                LocalDate.of(2026, 1, 5),
                "Medication A",
                2,
                25_000L,
                "Respiratory treatment"
        ));
        var vaccination = healthService.addVaccination(batch.id(), new VaccinationRecordRequest(
                LocalDate.of(2026, 1, 6),
                "Newcastle vaccine",
                "1 dose",
                500,
                "Routine flock vaccination"
        ));

        assertThat(healthService.health(batch.id())).singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(health.id());
                    assertThat(row.conditionProblem()).isEqualTo("Respiratory symptoms");
                });
        assertThat(healthService.drugs(batch.id())).singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(drug.id());
                    assertThat(row.costMinor()).isEqualTo(25_000L);
                });
        assertThat(healthService.vaccinations(batch.id())).singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(vaccination.id());
                    assertThat(row.quantity()).isEqualTo(500);
                });

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE category = 'DRUGS' AND reference_type = 'DRUG_RECORD' AND reference_id = ?",
                Integer.class, drug.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE category = 'DRUGS'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void healthRecordsCannotBeBackdatedBeforeBatchPlacement() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 10), house.id(), 100, null, 1_000_000L));

        assertThatThrownBy(() -> healthService.addHealth(batch.id(), new HealthRecordRequest(
                LocalDate.of(2026, 1, 9), "Problem", "Description", "Action"
        ))).hasMessage("Health records cannot be dated before the batch placement date.");

        assertThatThrownBy(() -> healthService.addDrug(batch.id(), new DrugRecordRequest(
                LocalDate.of(2026, 1, 9), "Drug", 1, 1_000L, "Reason"
        ))).hasMessage("Health records cannot be dated before the batch placement date.");

        assertThatThrownBy(() -> healthService.addVaccination(batch.id(), new VaccinationRecordRequest(
                LocalDate.of(2026, 1, 9), "Vaccine", "1 dose", 100, null
        ))).hasMessage("Health records cannot be dated before the batch placement date.");
    }

    @Test
    void drugCostCreatesOnlyOneDrugsExpense() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        healthService.addDrug(batch.id(), new DrugRecordRequest(
                LocalDate.of(2026, 1, 2), "Drug B", 3, 50_000L, "Treatment"
        ));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE category = 'DRUGS'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT amount_minor FROM expenses WHERE category = 'DRUGS'",
                Long.class)).isEqualTo(50_000L);
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
