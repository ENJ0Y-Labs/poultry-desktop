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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:attention-test?mode=memory&cache=shared",
        "poultry.database-path=:memory:"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AttentionServiceIntegrationTest {
    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 3);

    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired FeedService feedService;
    @Autowired InventoryService inventoryService;
    @Autowired BirdPopulationService populationService;
    @Autowired DailyOperationsService dailyOperationsService;
    @Autowired AttentionService attentionService;
    @Autowired DashboardService dashboardService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void derivesAllOperationalAttentionRulesFromBackendData() {
        setupFarm();

        var layerHouse = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var broilerHouse = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));

        var layer = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 8, 1), layerHouse.id(), 1000, null, 10_000_000L));
        var broiler = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 8, 1), broilerHouse.id(), 500, null, 5_000_000L));

        populationService.addMortality(layer.id(), new BirdPopulationEventRequest(
                AS_OF, 60, "Disease", "Attention test"));

        var feedType = feedService.createType(new FeedTypeCreateRequest(
                "Layer Mash", "bag", "LAYER"));
        feedService.purchase(new FeedPurchaseRequest(
                feedType.id(), null, LocalDate.of(2026, 8, 1),
                new BigDecimal("10"), "bag", 100_000L));
        feedService.use(layer.id(), new FeedUsageRequest(
                feedType.id(), AS_OF.minusDays(1),
                new BigDecimal("9"), "bag"));

        var lowInventory = inventoryService.createItem(new InventoryItemCreateRequest(
                "Amprolium", "DRUG", "bottle", new BigDecimal("5")));
        inventoryService.recordMovement(lowInventory.id(), new InventoryMovementRequest(
                AS_OF, "RECEIVE", new BigDecimal("4"),
                "Attention test", "TEST", null));

        var emptyInventory = inventoryService.createItem(new InventoryItemCreateRequest(
                "Newcastle Vaccine", "VACCINE", "dose", new BigDecimal("1")));

        seedEggProductionDrop(layer.id());

        List<Map<String, Object>> attention = attentionService.list(AS_OF);
        Set<String> types = attention.stream()
                .map(row -> String.valueOf(row.get("type")))
                .collect(Collectors.toSet());

        assertThat(types).contains(
                "LOW_INVENTORY",
                "INSUFFICIENT_INVENTORY",
                "LOW_FEED_STOCK",
                "HIGH_MORTALITY",
                "MISSING_DAILY_RECORD",
                "VACCINATION_DUE",
                "UNUSUAL_PRODUCTION_DROP",
                "BATCH_NEARING_SALE"
        );

        assertThat(attention.stream()
                .filter(row -> "HIGH_MORTALITY".equals(row.get("type")))
                .findFirst().orElseThrow()
                .get("batchCode")).isEqualTo(layer.code());

        assertThat(attention.stream()
                .filter(row -> "BATCH_NEARING_SALE".equals(row.get("type")))
                .findFirst().orElseThrow()
                .get("batchCode")).isEqualTo(broiler.code());

        assertThat(attention.stream()
                .filter(row -> "INSUFFICIENT_INVENTORY".equals(row.get("type")))
                .findFirst().orElseThrow()
                .get("item")).isEqualTo(emptyInventory.name());

        assertThat(attention.stream()
                .filter(row -> "LOW_INVENTORY".equals(row.get("type")))
                .findFirst().orElseThrow()
                .get("item")).isEqualTo(lowInventory.name());

        assertThat(attention.stream()
                .filter(row -> "LOW_FEED_STOCK".equals(row.get("type")))
                .findFirst().orElseThrow()
                .get("feedTypeId")).isEqualTo(feedType.id());

        assertThat(attention.stream()
                .filter(row -> "LOW_INVENTORY".equals(row.get("type")))
                .findFirst().orElseThrow()
                .get("itemId")).isEqualTo(lowInventory.id());

        assertThat(attention.stream()
                .filter(row -> "INSUFFICIENT_INVENTORY".equals(row.get("type")))
                .findFirst().orElseThrow()
                .get("itemId")).isEqualTo(emptyInventory.id());

        Map<String, Object> mortalityInsight = attention.stream()
                .filter(row -> "HIGH_MORTALITY".equals(row.get("type")))
                .findFirst().orElseThrow();
        assertThat(mortalityInsight)
                .containsEntry("detectedAt", AS_OF)
                .containsEntry("affectedMetric", "Mortality")
                .containsEntry("ruleId", "HIGH_MORTALITY")
                .containsEntry("thresholdContext", "Warning at 5%; critical at 10% of initial birds.")
                .containsKey("suggestedAction")
                .containsEntry("houseId", layerHouse.id())
                .containsEntry("houseName", "Layer House");
    }

    @Test
    void detectsRecentFeedIncreaseWeightDeclineAndRisingExpensesWithRuleContext() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        var broiler = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 8, 1), house.id(), 500, null, 5_000_000L));

        var feedType = feedService.createType(new FeedTypeCreateRequest("Broiler Grower", "kg", "BROILER"));
        feedService.purchase(new FeedPurchaseRequest(
                feedType.id(), null, LocalDate.of(2026, 8, 1), new BigDecimal("100"), "kg", 500_000L));
        feedService.use(broiler.id(), new FeedUsageRequest(
                feedType.id(), AS_OF.minusDays(10), new BigDecimal("10"), "kg"));
        feedService.use(broiler.id(), new FeedUsageRequest(
                feedType.id(), AS_OF.minusDays(3), new BigDecimal("15"), "kg"));

        jdbc.update("""
                INSERT INTO weight_records(id,batch_id,record_date,sample_quantity,total_weight_grams,notes,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), broiler.id(), AS_OF.minusDays(10).toString(),
                10, 15_000, "Earlier weight", AS_OF.minusDays(10).atStartOfDay().toString());
        jdbc.update("""
                INSERT INTO weight_records(id,batch_id,record_date,sample_quantity,total_weight_grams,notes,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), broiler.id(), AS_OF.minusDays(2).toString(),
                10, 12_000, "Latest weight", AS_OF.minusDays(2).atStartOfDay().toString());

        String farmId = jdbc.queryForObject("SELECT id FROM farms LIMIT 1", String.class);
        jdbc.update("""
                INSERT INTO expenses(id,farm_id,category,amount_minor,occurred_date,description,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), farmId, "OTHER", 10_000L,
                AS_OF.minusDays(10).toString(), "Earlier expense", AS_OF.minusDays(10).atStartOfDay().toString());
        jdbc.update("""
                INSERT INTO expenses(id,farm_id,category,amount_minor,occurred_date,description,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), farmId, "OTHER", 15_000L,
                AS_OF.minusDays(2).toString(), "Recent expense", AS_OF.minusDays(2).atStartOfDay().toString());

        List<Map<String, Object>> attention = attentionService.list(AS_OF);
        Set<String> types = attention.stream().map(row -> String.valueOf(row.get("type"))).collect(Collectors.toSet());

        assertThat(types).contains("UNUSUAL_FEED_CONSUMPTION", "DECLINING_WEIGHT", "RISING_EXPENSES");
        assertThat(attention.stream().filter(row -> "UNUSUAL_FEED_CONSUMPTION".equals(row.get("type")))
                .findFirst().orElseThrow())
                .containsEntry("batchId", broiler.id())
                .containsEntry("feedTypeId", feedType.id())
                .containsEntry("houseId", house.id())
                .containsEntry("detectedAt", AS_OF);
        assertThat(attention.stream().filter(row -> "DECLINING_WEIGHT".equals(row.get("type")))
                .findFirst().orElseThrow().get("declinePercent")).isEqualTo(20.0);
        assertThat(attention.stream().filter(row -> "RISING_EXPENSES".equals(row.get("type")))
                .findFirst().orElseThrow()).containsEntry("increasePercent", 50.0)
                .containsEntry("affectedMetric", "Expenses");
    }

    @Test
    void detectsRecentMortalitySpikeAndFallingRevenueWithoutInventingBaseline() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var layer = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 8, 1), house.id(), 1000, null, 10_000_000L));
        populationService.addMortality(layer.id(), new BirdPopulationEventRequest(
                AS_OF.minusDays(1), 25, "Attention test", "Recent mortality spike"));

        String farmId = jdbc.queryForObject("SELECT id FROM farms LIMIT 1", String.class);
        String customerId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO customers(id,farm_id,name,created_at,updated_at)
                VALUES(?,?,?,?,?)
                """, customerId, farmId, "Attention Test Customer",
                AS_OF.atStartOfDay().toString(), AS_OF.atStartOfDay().toString());
        jdbc.update("""
                INSERT INTO sales(id,farm_id,batch_id,customer_id,sale_date,sale_type,quantity,unit,
                                  unit_price_minor,total_amount_minor,reference_type,reference_id,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), farmId, layer.id(), customerId,
                AS_OF.minusDays(10).toString(), "EGG", 100, "CRATE", 1_000L, 100_000L,
                "TEST", UUID.randomUUID().toString(), AS_OF.minusDays(10).atStartOfDay().toString());
        jdbc.update("""
                INSERT INTO sales(id,farm_id,batch_id,customer_id,sale_date,sale_type,quantity,unit,
                                  unit_price_minor,total_amount_minor,reference_type,reference_id,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), farmId, layer.id(), customerId,
                AS_OF.minusDays(2).toString(), "EGG", 50, "CRATE", 1_000L, 50_000L,
                "TEST", UUID.randomUUID().toString(), AS_OF.minusDays(2).atStartOfDay().toString());

        List<Map<String, Object>> attention = attentionService.list(AS_OF);
        assertThat(attention).filteredOn(row -> "MORTALITY_INCREASE".equals(row.get("type")))
                .singleElement().satisfies(row -> assertThat(row)
                        .containsEntry("batchId", layer.id())
                        .containsEntry("houseId", house.id())
                        .containsEntry("thresholdContext", "7-day mortality >= 2% of initial birds; critical at 5%."));
        assertThat(attention).filteredOn(row -> "FALLING_REVENUE".equals(row.get("type")))
                .singleElement().satisfies(row -> assertThat(row)
                        .containsEntry("decreasePercent", 50.0)
                        .containsEntry("affectedMetric", "Sales revenue")
                        .containsKey("suggestedAction"));
    }

    @Test
    void ignoresDecliningWeightWhenLatestMeasurementIsStale() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        var broiler = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 8, 1), house.id(), 500, null, 5_000_000L));
        jdbc.update("""
                INSERT INTO weight_records(id,batch_id,record_date,sample_quantity,total_weight_grams,notes,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), broiler.id(), AS_OF.minusDays(20).toString(),
                10, 15_000, "Earlier weight", AS_OF.minusDays(20).atStartOfDay().toString());
        jdbc.update("""
                INSERT INTO weight_records(id,batch_id,record_date,sample_quantity,total_weight_grams,notes,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), broiler.id(), AS_OF.minusDays(15).toString(),
                10, 12_000, "Stale lower weight", AS_OF.minusDays(15).atStartOfDay().toString());

        assertThat(attentionService.list(AS_OF)).noneMatch(row -> "DECLINING_WEIGHT".equals(row.get("type")));
    }

    @Test
    void emptyUnstockedFeedAndZeroReorderItemsDoNotCreateFalseStockAlerts() {
        setupFarm();
        feedService.createType(new FeedTypeCreateRequest("Unused Broiler Feed", "bag", "BROILER"));
        var noThreshold = inventoryService.createItem(new InventoryItemCreateRequest(
                "Optional Supply", "SUPPLY", "pack", BigDecimal.ZERO));

        List<Map<String, Object>> attention = attentionService.list(AS_OF);
        Set<String> alertedItems = attention.stream()
                .filter(row -> Set.of("LOW_INVENTORY", "INSUFFICIENT_INVENTORY").contains(row.get("type")))
                .map(row -> String.valueOf(row.get("item")))
                .collect(Collectors.toSet());

        assertThat(attention.stream()
                .filter(row -> "LOW_FEED_STOCK".equals(row.get("type")))
                .map(row -> row.get("item")))
                .doesNotContain("Unused Broiler Feed");
        assertThat(alertedItems).doesNotContain(noThreshold.name());
        assertThat(dashboardService.farm(AS_OF).get("inventoryAlerts")).isEqualTo(0);
    }

    @Test
    void depletedPreviouslyStockedFeedRemainsCriticalAndPositiveReorderThresholdStillAlerts() {
        setupFarm();
        var feedType = feedService.createType(new FeedTypeCreateRequest("Layer Mash", "bag", "LAYER"));
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var layer = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 8, 1), house.id(), 100, null, 1_000_000L));
        feedService.purchase(new FeedPurchaseRequest(
                feedType.id(), null, LocalDate.of(2026, 8, 1),
                new BigDecimal("1"), "bag", 10_000L));
        feedService.use(layer.id(), new FeedUsageRequest(
                feedType.id(), AS_OF.minusDays(1), new BigDecimal("1"), "bag"));

        var lowInventory = inventoryService.createItem(new InventoryItemCreateRequest(
                "Vitamin Bottle", "SUPPLY", "bottle", new BigDecimal("2")));
        inventoryService.recordMovement(lowInventory.id(), new InventoryMovementRequest(
                AS_OF, "RECEIVE", BigDecimal.ONE, "Test stock", "TEST", null));

        List<Map<String, Object>> attention = attentionService.list(AS_OF);
        assertThat(attention.stream()
                .filter(row -> "LOW_FEED_STOCK".equals(row.get("type")))
                .anyMatch(row -> "Layer Mash".equals(row.get("item"))
                        && "CRITICAL".equals(row.get("severity")))).isTrue();
        assertThat(attention.stream()
                .filter(row -> "LOW_INVENTORY".equals(row.get("type")))
                .anyMatch(row -> lowInventory.name().equals(row.get("item")))).isTrue();
    }

    @Test
    void farmDashboardAttentionCountUsesTheSameBackendAttentionSystem() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 8, 1), house.id(), 100, null, 1_000_000L));

        List<Map<String, Object>> attention = attentionService.list(AS_OF);
        Map<String, Object> dashboard = dashboardService.farm(AS_OF);

        assertThat(dashboard.get("attention")).isEqualTo(attention.size());
    }

    private void seedEggProductionDrop(String batchId) {
        for (int day = 14; day >= 8; day--) {
            insertEgg(batchId, AS_OF.minusDays(day), 100);
        }
        for (int day = 7; day >= 1; day--) {
            insertEgg(batchId, AS_OF.minusDays(day), 70);
        }
    }

    private void insertEgg(String batchId, LocalDate date, int goodEggs) {
        jdbc.update("""
                INSERT INTO egg_collections
                    (id, batch_id, record_date, good_eggs, cracked_eggs, notes, created_at)
                VALUES (?, ?, ?, ?, 0, ?, ?)
                """,
                UUID.randomUUID().toString(), batchId, date.toString(), goodEggs,
                "Attention integration test", date.atStartOfDay().toString());
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
