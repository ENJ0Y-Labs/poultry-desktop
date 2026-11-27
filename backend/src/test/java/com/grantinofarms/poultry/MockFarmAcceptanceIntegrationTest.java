package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:mock-farm-acceptance-test?mode=memory&cache=shared",
        "poultry.database-path=:memory:",
        "poultry.auth-required=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MockFarmAcceptanceIntegrationTest {
    private static final LocalDate PLACEMENT = LocalDate.of(2026, 11, 1);
    private static final LocalDate EVENT_DAY = LocalDate.of(2026, 11, 3);

    @Autowired FarmService farms;
    @Autowired HouseService houses;
    @Autowired BatchService batches;
    @Autowired FeedService feed;
    @Autowired DailyOperationsService daily;
    @Autowired BirdPopulationService population;
    @Autowired EggManagementService eggs;
    @Autowired CustomerService customers;
    @Autowired ExpenseService expenses;
    @Autowired DashboardService dashboard;
    @Autowired ReportService reports;
    @Autowired JdbcTemplate jdbc;

    @Test
    void seededLayerAndBroilerScenarioReconcilesOperationsInventorySalesAndReports() {
        farms.create(new FarmCreateRequest("Pomas Mock Acceptance Farm",
                "Port Harcourt", "Africa/Lagos", "NGN"));

        var layerHouse = houses.create(new HouseCreateRequest("Layer House", "LA-01", "Acceptance fixture"));
        var broilerHouse = houses.create(new HouseCreateRequest("Broiler House", "BR-01", "Acceptance fixture"));
        var layer = batches.create(new BatchCreateRequest("LAYER", PLACEMENT, layerHouse.id(), 500, null, 5_000_000L));
        var broiler = batches.create(new BatchCreateRequest("BROILER", PLACEMENT, broilerHouse.id(), 100, null, 1_000_000L));

        farms.updateSettings(new FarmSettingsRequest(30, 25, List.of(25, 75), "litres"));

        var broilerFeed = feed.createType(new FeedTypeCreateRequest("Mock Broiler Starter", "kg", "BROILER"));
        feed.purchase(new FeedPurchaseRequest(broilerFeed.id(), null, PLACEMENT,
                new BigDecimal("10.000"), "kg", 500_000L));
        feed.use(broiler.id(), new FeedUsageRequest(broilerFeed.id(), EVENT_DAY,
                new BigDecimal("2.000"), "kg"));

        population.addMortality(broiler.id(), new BirdPopulationEventRequest(
                EVENT_DAY, 3, "Mock respiratory observation", "Acceptance fixture only"));

        var dailyRecord = daily.create(layer.id(), new DailyRecordRequest(
                EVENT_DAY, "Mock morning inspection",
                List.of(new WaterContainerEntryRequest(25, 4))));

        eggs.addCollection(layer.id(), new EggCollectionRequest(EVENT_DAY, 300, 5, "Mock collection"));
        var customer = customers.create(new CustomerCreateRequest("Mock Market Buyer", "08000000000", "Acceptance fixture"));
        var eggSale = eggs.addSale(layer.id(), new EggSaleRequest(
                EVENT_DAY, customer.name(), new BigDecimal("1.000"), 200_000L, customer.id()));
        var expense = expenses.create(new ExpenseRequest(
                EVENT_DAY, "Mock litter purchase", 75_000L, "OTHER", broiler.id()));

        Map<String, Object> snapshot = dashboard.farm(EVENT_DAY);
        assertThat(((Number) snapshot.get("totalBirds")).longValue()).isEqualTo(597L);
        assertThat(((Number) snapshot.get("layerBirds")).longValue()).isEqualTo(500L);
        assertThat(((Number) snapshot.get("broilerBirds")).longValue()).isEqualTo(97L);
        assertThat(((Number) snapshot.get("mortality")).longValue()).isEqualTo(3L);
        assertThat(((Number) snapshot.get("eggsGood")).longValue()).isEqualTo(300L);
        assertThat(((Number) snapshot.get("revenueMinor")).longValue()).isEqualTo(200_000L);

        assertThat(dailyRecord.notes()).isEqualTo("Mock morning inspection");
        assertThat(eggSale.soldEggs()).isEqualTo(30);
        assertThat(expense.amountMinor()).isEqualTo(75_000L);
        assertThat(eggs.collections(layer.id(), EVENT_DAY)).hasSize(1);
        assertThat(feed.inventory(EVENT_DAY)).singleElement()
                .satisfies(stock -> assertThat(stock.remainingQuantityMilli()).isEqualTo(8_000L));

        Map<String, Object> report = reports.farm(EVENT_DAY);
        assertThat((List<?>) report.get("sales")).hasSize(1);
        assertThat((List<?>) report.get("expenses")).extracting("id").contains(expense.id());
        assertThat((List<?>) report.get("eggProduction")).hasSize(1);
        assertThat(((Number) jdbc.queryForObject(
                "SELECT COUNT(*) FROM bird_population_events WHERE batch_id=? AND event_type='MORTALITY'",
                Integer.class, broiler.id())).longValue()).isEqualTo(1L);

        // This fixture is synthetic and must never be mixed with a developer's or farm's real database.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM farms WHERE name='Pomas Mock Acceptance Farm'", Integer.class)).isEqualTo(1);
    }
}
