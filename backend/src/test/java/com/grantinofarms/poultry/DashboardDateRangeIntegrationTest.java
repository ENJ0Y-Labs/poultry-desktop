package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.DashboardService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:dashboard-date-range-integration?mode=memory&cache=shared",
        "poultry.database-path=:memory:",
        "poultry.auth-required=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DashboardDateRangeIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired DashboardService dashboard;

    @BeforeEach
    void setUpFarm() {
        farmService.create(new FarmCreateRequest("Date Range Farm", "Port Harcourt", "Africa/Lagos", "NGN"));
        houseService.create(new HouseCreateRequest("House 1", "H001", null));
    }

    @Test
    void periodTotalsAreInclusiveAndReconcileWithoutChangingCurrentPopulation() {
        var farm = farmService.get();
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 2, 1), firstHouseId(), 100, null, 100_000L));

        insertMortality(batch.id(), "2026-02-28", 2, "mortality-from");
        insertMortality(batch.id(), "2026-03-01", 1, "mortality-to");
        insertMortality(batch.id(), "2026-03-02", 5, "mortality-outside");

        insertEggs(batch.id(), "2026-02-28", 10, 2, "eggs-from");
        insertEggs(batch.id(), "2026-03-01", 20, 1, "eggs-to");
        insertEggs(batch.id(), "2026-03-02", 100, 0, "eggs-outside");

        jdbc.update("""
                INSERT INTO customers (id, farm_id, name, phone, notes, created_at, updated_at)
                VALUES (?, ?, ?, NULL, NULL, ?, ?)
                """, "range-customer", farm.id(), "Range Customer",
                "2026-02-01T00:00:00Z", "2026-02-01T00:00:00Z");
        insertSale(farm.id(), batch.id(), "2026-02-28", 1_000L, "sale-from");
        insertSale(farm.id(), batch.id(), "2026-03-01", 2_000L, "sale-to");
        insertSale(farm.id(), batch.id(), "2026-03-02", 9_000L, "sale-outside");

        insertExpense(farm.id(), batch.id(), "2026-02-28", 400L, "expense-from");
        insertExpense(farm.id(), batch.id(), "2026-03-01", 600L, "expense-to");
        insertExpense(farm.id(), batch.id(), "2026-03-02", 9_000L, "expense-outside");

        Map<String, Object> result = dashboard.farmRange(
                LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1));
        @SuppressWarnings("unchecked")
        Map<String, Object> period = (Map<String, Object>) result.get("period");

        assertThat(period)
                .containsEntry("mortality", 3L)
                .containsEntry("eggsGood", 30L)
                .containsEntry("revenueMinor", 3_000L)
                .containsEntry("expensesMinor", 1_000L)
                .containsEntry("salesCount", 2L)
                .containsEntry("expenseCount", 2L);
        assertThat(result.get("totalBirds")).isEqualTo(92L);
        assertThat(result.get("from")).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(result.get("to")).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    private String firstHouseId() {
        return jdbc.queryForObject("SELECT id FROM houses LIMIT 1", String.class);
    }

    private void insertMortality(String batchId, String date, int quantity, String id) {
        jdbc.update("""
                INSERT INTO bird_population_events
                    (id, batch_id, event_date, event_type, quantity, reference_batch_id, reason, notes, created_at)
                VALUES (?, ?, ?, 'MORTALITY', ?, NULL, NULL, NULL, ?)
                """, id, batchId, date, quantity, date + "T08:00:00Z");
    }

    private void insertEggs(String batchId, String date, int good, int cracked, String id) {
        jdbc.update("""
                INSERT INTO egg_collections
                    (id, batch_id, record_date, good_eggs, cracked_eggs, notes, created_at)
                VALUES (?, ?, ?, ?, ?, NULL, ?)
                """, id, batchId, date, good, cracked, date + "T08:00:00Z");
    }

    private void insertSale(String farmId, String batchId, String date, long amount, String id) {
        jdbc.update("""
                INSERT INTO sales
                    (id, farm_id, batch_id, customer_id, sale_date, sale_type, quantity, unit,
                     unit_price_minor, total_amount_minor, reference_type, reference_id, created_at)
                VALUES (?, ?, ?, 'range-customer', ?, 'EGG', 1, 'CRATE', ?, ?, 'TEST', ?, ?)
                """, id, farmId, batchId, date, amount, amount, id, date + "T09:00:00Z");
    }

    private void insertExpense(String farmId, String batchId, String date, long amount, String id) {
        jdbc.update("""
                INSERT INTO expenses
                    (id, farm_id, batch_id, category, amount_minor, occurred_date,
                     reference_type, reference_id, description, created_at)
                VALUES (?, ?, ?, 'OTHER', ?, ?, 'TEST', ?, ?, ?)
                """, id, farmId, batchId, amount, date, id, "Test expense", date + "T10:00:00Z");
    }
}
