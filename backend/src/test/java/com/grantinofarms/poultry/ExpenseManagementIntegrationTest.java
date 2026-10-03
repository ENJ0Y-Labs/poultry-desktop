package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.ExpenseRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.ExpenseService;
import com.grantinofarms.poultry.service.FarmService;
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
        "spring.datasource.url=jdbc:sqlite:file:expense-management-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ExpenseManagementIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired ExpenseService expenseService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsAndListsFarmAndBatchExpenses() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        var farmExpense = expenseService.create(new ExpenseRequest(
                LocalDate.of(2026, 1, 3), "Generator fuel", 30_000L, "OTHER", null));
        var batchExpense = expenseService.create(new ExpenseRequest(
                LocalDate.of(2026, 1, 4), "Litter purchase", 45_000L, "OTHER", batch.id()));

        assertThat(expenseService.list(null)).extracting("id")
                .containsExactly(batchExpense.id(), farmExpense.id());
        assertThat(expenseService.list(batch.id())).singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(batchExpense.id());
                    assertThat(row.batchId()).isEqualTo(batch.id());
                    assertThat(row.amountMinor()).isEqualTo(45_000L);
                    assertThat(row.category()).isEqualTo("OTHER");
                });

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE batch_id = ?",
                Integer.class, batch.id())).isEqualTo(1);
    }

    @Test
    void rejectsExpenseForAnotherFarmBatch() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        assertThatThrownBy(() -> expenseService.create(new ExpenseRequest(
                LocalDate.of(2026, 1, 2), "Invalid", 1_000L, "OTHER", "missing-batch"
        ))).hasMessage("Batch not found for the active farm.");
    }

    @Test
    void drugExpenseKeepsBatchAssociation() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        var drugId = "drug-ref";
        jdbc.update("""
                INSERT INTO drug_records
                    (id, batch_id, record_date, drug, quantity, cost_minor, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                drugId, batch.id(), "2026-01-02", "Drug A", 1, 20_000L, "Treatment", "2026-01-02T00:00:00Z");
        jdbc.update("""
                INSERT INTO expenses
                    (id, farm_id, batch_id, category, amount_minor, occurred_date,
                     reference_type, reference_id, description, created_at)
                VALUES (?, ?, ?, 'DRUGS', ?, ?, 'DRUG_RECORD', ?, ?, ?)
                """,
                "expense-ref", farmService.get().id(), batch.id(), 20_000L,
                "2026-01-02", drugId, "Drug: Drug A - Treatment", "2026-01-02T00:00:00Z");

        assertThat(expenseService.list(batch.id())).singleElement()
                .satisfies(row -> assertThat(row.batchId()).isEqualTo(batch.id()));
    }

    private void setupFarm() {
        farmService.create(new com.grantinofarms.poultry.dto.FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
