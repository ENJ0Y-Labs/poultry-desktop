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
