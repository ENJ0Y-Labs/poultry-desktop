package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.FeedService;
import com.grantinofarms.poultry.service.FeedHistoryCsvImportService;
import com.grantinofarms.poultry.service.HouseService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:feed-management-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FeedManagementIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired FeedService feedService;
    @Autowired FeedHistoryCsvImportService feedHistoryImport;
    @Autowired JdbcTemplate jdbc;

    @Test
    void feedConversionMustBePositiveAndCanBeLeftUnknown() {
        setupFarm();
        var unknown = feedService.createType(new FeedTypeCreateRequest("Unknown Bag", "bag", "BROILER"));
        assertThat(unknown.kgPerUnit()).isNull();

        assertThatThrownBy(() -> feedService.createType(
                new FeedTypeCreateRequest("Invalid Bag", "bag", "BROILER", BigDecimal.ZERO)))
                .hasMessage("Kilograms per unit must be greater than zero.");

        assertThatThrownBy(() -> feedService.updateConversion(unknown.id(),
                new FeedTypeConversionRequest(new BigDecimal("-1"))))
                .hasMessage("Kilograms per unit must be greater than zero, or left blank when unknown.");

        var updated = feedService.updateConversion(unknown.id(),
                new FeedTypeConversionRequest(new BigDecimal("25")));
        assertThat(updated.kgPerUnit()).isEqualByComparingTo("25");
    }

    @Test
    void feedProductRetainsBrandAndPackageWeightWithoutGuessingUnknowns() {
        setupFarm();
        var known = feedService.createType(new FeedTypeCreateRequest(
                "Hybrid Broiler Finisher Pellets", "bag", "BROILER", new BigDecimal("25"),
                "Hybrid", "25 kg bag", new BigDecimal("25")));
        var unknown = feedService.createType(new FeedTypeCreateRequest(
                "Hybrid Layer Mash", "bag", "LAYER", null, "Hybrid", "bag", null));

        assertThat(known.brand()).isEqualTo("Hybrid");
        assertThat(known.packagingName()).isEqualTo("25 kg bag");
        assertThat(known.packageWeightKg()).isEqualByComparingTo("25");
        assertThat(unknown.packageWeightKg()).isNull();
        assertThat(unknown.kgPerUnit()).isNull();
    }


    @Test
    void openingFeedStockIsAuditedWithoutCreatingPurchaseOrExpense() {
        setupFarm();
        var type = feedService.createType(new FeedTypeCreateRequest("Hybrid Layer Mash", "bag", "LAYER"));
        var opening = feedService.createOpeningBalance(new FeedOpeningBalanceRequest(
                type.id(), LocalDate.of(2026, 11, 6), new BigDecimal("55"), "bag", "Opening physical stock count"));

        assertThat(opening.quantityMilli()).isEqualTo(55_000L);
        assertThat(feedService.openingBalances(type.id())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_purchases", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE category = 'FEED'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'FEED_OPENING_BALANCE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void openingStockIsIncludedInInventoryAndConsumedBeforeLaterPurchasedFeedWithoutExpense() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Opening Stock House", "OSH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        var type = feedService.createType(new FeedTypeCreateRequest("Opening Layer Mash", "kg", "LAYER"));

        feedService.createOpeningBalance(new FeedOpeningBalanceRequest(
                type.id(), LocalDate.of(2026,1,1), new BigDecimal("10"), "kg", "Existing farm stock"));
        var openingOnly = feedService.inventory(LocalDate.of(2026,1,1)).get(0);
        assertThat(openingOnly.openingQuantityMilli()).isEqualTo(10_000L);
        assertThat(openingOnly.purchasedQuantityMilli()).isZero();
        assertThat(openingOnly.remainingQuantityMilli()).isEqualTo(10_000L);
        assertThat(openingOnly.remainingCostMinor()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE category = 'FEED'", Integer.class)).isZero();

        feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,2), new BigDecimal("10"), "kg", 100_000L));
        var firstUsage = feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,3), new BigDecimal("5"), "kg", "Use opening stock first"));

        assertThat(firstUsage.feedCostMinor()).isNull();
        var afterOpeningConsumption = feedService.inventory(LocalDate.of(2026,1,3)).get(0);
        assertThat(afterOpeningConsumption.openingQuantityMilli()).isEqualTo(10_000L);
        assertThat(afterOpeningConsumption.purchasedQuantityMilli()).isEqualTo(10_000L);
        assertThat(afterOpeningConsumption.consumedQuantityMilli()).isEqualTo(5_000L);
        assertThat(afterOpeningConsumption.remainingQuantityMilli()).isEqualTo(15_000L);
        assertThat(afterOpeningConsumption.remainingCostMinor()).isNull();

        var secondUsage = feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,4), new BigDecimal("7"), "kg", "Finish opening stock"));
        assertThat(secondUsage.feedCostMinor()).isNull();

        var thirdUsage = feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,5), new BigDecimal("1"), "kg", "Consume purchased stock"));
        assertThat(thirdUsage.feedCostMinor()).isEqualTo(10_000L);
        assertThat(feedService.batchCost(batch.id(), LocalDate.of(2026,1,5)).feedCostMinor()).isNull();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_purchases", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE category = 'FEED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bird_cost_events WHERE batch_id = ? AND event_type = 'ADDITIONAL_COST'",
                Integer.class, batch.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'FEED_OPENING_BALANCE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void openingStockCannotBeBackdatedBeforeExistingUsage() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Chronology House", "CH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        var type = feedService.createType(new FeedTypeCreateRequest("Chronology Feed", "kg", "BROILER"));
        feedService.createOpeningBalance(new FeedOpeningBalanceRequest(
                type.id(), LocalDate.of(2026,1,1), new BigDecimal("10"), "kg", "Initial stock"));
        feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,3), new BigDecimal("1"), "kg", "Recorded usage"));

        assertThatThrownBy(() -> feedService.createOpeningBalance(new FeedOpeningBalanceRequest(
                type.id(), LocalDate.of(2026,1,2), new BigDecimal("2"), "kg", "Backdated correction")))
                .hasMessage("Opening stock cannot be backdated before existing feed usage for this feed type.");
        assertThat(feedService.openingBalances(type.id())).hasSize(1);
    }

    @Test
    void purchaseCreatesInventoryAndOneExpense() {
        setupFarm();
        var type = feedService.createType(new FeedTypeCreateRequest("Starter", "bag", "BROILER"));

        var purchase = feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,1), new BigDecimal("20"), "bag", 160_000L));

        assertThat(purchase.quantityMilli()).isEqualTo(20_000L);
        assertThat(feedService.inventory(LocalDate.of(2026,1,1)).get(0).remainingQuantityMilli())
                .isEqualTo(20_000L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE reference_type = 'FEED_PURCHASE' AND reference_id = ?",
                Integer.class, purchase.id())).isEqualTo(1);
    }

    @Test
    void fifoUsageUsesMultiplePricesAndDoesNotCreateAnotherExpense() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        var type = feedService.createType(new FeedTypeCreateRequest("Finisher", "bag", "BROILER"));

        feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,1), new BigDecimal("20"), "bag", 160_000L));
        feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,2), new BigDecimal("30"), "bag", 300_000L));

        var usage = feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,3), new BigDecimal("25"), "bag"));

        assertThat(usage.quantityMilli()).isEqualTo(25_000L);
        assertThat(usage.feedCostMinor()).isEqualTo(210_000L);

        var inventory = feedService.inventory(LocalDate.of(2026,1,3)).get(0);
        assertThat(inventory.remainingQuantityMilli()).isEqualTo(25_000L);
        assertThat(inventory.remainingCostMinor()).isEqualTo(250_000L);
        assertThat(inventory.consumedCostMinor()).isEqualTo(210_000L);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE category = 'FEED'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void usageCannotExceedAvailableFeed() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        var type = feedService.createType(new FeedTypeCreateRequest("Layer Mash", "kg", "LAYER"));

        feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,1), new BigDecimal("10"), "kg", 100_000L));

        assertThatThrownBy(() -> feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,2), new BigDecimal("10.001"), "kg")))
                .hasMessage("Insufficient feed inventory for FIFO consumption.");
    }

    @Test
    void feedCostIsAddedOnceToBatchBirdCost() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        var type = feedService.createType(new FeedTypeCreateRequest("Grower", "bag", "LAYER"));

        feedService.purchase(new FeedPurchaseRequest(
                type.id(), null, LocalDate.of(2026,1,1), new BigDecimal("10"), "bag", 100_000L));
        feedService.use(batch.id(), new FeedUsageRequest(
                type.id(), LocalDate.of(2026,1,2), new BigDecimal("5"), "bag"));

        var cost = feedService.batchCost(batch.id(), LocalDate.of(2026,1,2));
        assertThat(cost.feedCostMinor()).isEqualTo(50_000L);
        assertThat(cost.feedCostPerBirdMinor()).isEqualTo(500L);
    }


    @Test
    void feedPurchaseImportConvertsNairaExactlyAndRetryDoesNotDoubleCount() {
        setupFarm();
        feedService.createType(new FeedTypeCreateRequest("Starter", "bag", "BROILER"));
        var row = Map.of("feed_type_name", "Starter", "supplier_name", "",
                "purchase_date", "2026-01-01", "quantity", "2.5", "unit", "bag",
                "total_cost_naira", "1600.25");
        var request = new CsvImportRequest("feed-purchases", List.of(row));

        var first = feedHistoryImport.importRecords(request);
        var second = feedHistoryImport.importRecords(request);

        assertThat(first.get("importedPurchases")).isEqualTo(1);
        assertThat(second.get("importedPurchases")).isEqualTo(0);
        assertThat(second.get("skippedDuplicates")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_cost_minor FROM feed_purchases", Long.class)).isEqualTo(160025L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE category = 'FEED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'FEED_PURCHASE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void feedUsageImportPreservesFifoAndDoesNotInventUnknownKilogramConversion() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Usage House", "UH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        var type = feedService.createType(new FeedTypeCreateRequest("Unknown Bag", "bag", "BROILER"));
        feedService.purchase(new FeedPurchaseRequest(type.id(), null, LocalDate.of(2026,1,1),
                new BigDecimal("20"), "bag", 160_000L));
        feedService.purchase(new FeedPurchaseRequest(type.id(), null, LocalDate.of(2026,1,2),
                new BigDecimal("30"), "bag", 300_000L));

        var result = feedHistoryImport.importRecords(new CsvImportRequest("feed-usage", List.of(
                Map.of("feed_type_name", "Unknown Bag", "batch_code", batch.code(),
                        "usage_date", "2026-01-03", "quantity", "25", "unit", "bag", "reason", "Historical feeding"))));

        assertThat(result.get("importedUsage")).isEqualTo(1);
        assertThat(feedService.types().stream().filter(t -> t.id().equals(type.id())).findFirst().orElseThrow().kgPerUnit()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_usage WHERE batch_id = ?", Integer.class, batch.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE category = 'FEED'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(amount_minor) FROM bird_cost_events WHERE batch_id = ? AND event_type = 'ADDITIONAL_COST'", Long.class, batch.id())).isEqualTo(210_000L);
    }

    @Test
    void feedUsagePreviewRejectsConsumptionWithoutDatedStock() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("No Stock House", "NSH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026,1,1), house.id(), 100, null, 1_000_000L));
        feedService.createType(new FeedTypeCreateRequest("No Stock Feed", "kg", "BROILER"));

        var preview = feedHistoryImport.preview(new CsvImportRequest("feed-usage", List.of(
                Map.of("feed_type_name", "No Stock Feed", "batch_code", batch.code(),
                        "usage_date", "2026-01-02", "quantity", "1", "unit", "kg", "reason", "Historical feeding"))));

        assertThat(preview.get("valid")).isEqualTo(false);
        assertThat(preview.get("rejected")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_usage", Integer.class)).isZero();
    }

    @Test
    void failedMultiPurchaseImportRollsBackPurchaseExpenseAndAuditRows() {
        setupFarm();
        feedService.createType(new FeedTypeCreateRequest("Rollback Feed", "bag", "BROILER"));
        jdbc.execute("""
                CREATE TRIGGER fail_second_imported_feed_purchase
                BEFORE INSERT ON feed_purchases
                WHEN NEW.total_cost_minor = 99999
                BEGIN
                    SELECT RAISE(ABORT, 'intentional import failure');
                END
                """);
        var request = new CsvImportRequest("feed-purchases", List.of(
                Map.of("feed_type_name", "Rollback Feed", "supplier_name", "",
                        "purchase_date", "2026-01-01", "quantity", "2", "unit", "bag", "total_cost_naira", "1000.00"),
                Map.of("feed_type_name", "Rollback Feed", "supplier_name", "",
                        "purchase_date", "2026-01-02", "quantity", "3", "unit", "bag", "total_cost_naira", "999.99")));

        assertThatThrownBy(() -> feedHistoryImport.importRecords(request)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_purchases", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE category = 'FEED'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'FEED_PURCHASE'", Integer.class)).isZero();
    }

    @Test
    void feedApplicabilitySupportsEveryNonEmptyCombinationAndEnforcesEachBirdType() {
        setupFarm();
        LocalDate placed = LocalDate.of(2026, 1, 1);
        var layerHouse = houseService.create(new HouseCreateRequest("Layer House", "LA1", null));
        var broilerHouse = houseService.create(new HouseCreateRequest("Broiler House", "BR1", null));
        var turkeyHouse = houseService.create(new HouseCreateRequest("Turkey House", "TU1", null));
        var layer = batchService.create(new BatchCreateRequest("LAYER", placed, layerHouse.id(), 10, null, 1000L));
        var broiler = batchService.create(new BatchCreateRequest("BROILER", placed, broilerHouse.id(), 10, null, 1000L));
        var turkey = batchService.create(new BatchCreateRequest("TURKEY", placed, turkeyHouse.id(), 10, null, 1000L));
        List<BatchResponse> batches = List.of(layer, broiler, turkey);

        List<List<String>> combinations = List.of(
                List.of("LAYER"),
                List.of("BROILER"),
                List.of("TURKEY"),
                List.of("LAYER", "BROILER"),
                List.of("LAYER", "TURKEY"),
                List.of("BROILER", "TURKEY"),
                List.of("LAYER", "BROILER", "TURKEY")
        );

        int index = 0;
        for (List<String> combination : combinations) {
            var feedType = feedService.createType(new FeedTypeCreateRequest(
                    "Combination Feed " + (++index), "kg", null, BigDecimal.ONE,
                    null, null, null, combination));
            assertThat(feedType.applicableTypes()).containsExactlyElementsOf(combination);

            for (BatchResponse batch : batches) {
                boolean applicable = combination.contains(batch.type());
                String expected = applicable
                        ? "Insufficient feed inventory for FIFO consumption."
                        : "This feed type is not configured for the batch type.";
                assertThatThrownBy(() -> feedService.use(batch.id(), new FeedUsageRequest(
                        feedType.id(), placed.plusDays(1), BigDecimal.ONE, "kg", "Applicability test")))
                        .hasMessage(expected);
            }
        }

        var legacyBoth = feedService.createType(new FeedTypeCreateRequest("Legacy Both", "kg", "BOTH"));
        assertThat(legacyBoth.applicableTypes()).containsExactly("LAYER", "BROILER", "TURKEY");

        assertThatThrownBy(() -> feedService.createType(new FeedTypeCreateRequest(
                "No Bird Type", "kg", null, BigDecimal.ONE, null, null, null, List.of())))
                .hasMessage("Select at least one supported bird type: LAYER, BROILER, or TURKEY.");
    }

    @Test
    void feedHistoryImportUsesTheSameMultiTypeApplicabilitySetAsManualUsage() {
        setupFarm();
        LocalDate placed = LocalDate.of(2026, 1, 1);
        var layerHouse = houseService.create(new HouseCreateRequest("Import Layer House", "IL1", null));
        var broilerHouse = houseService.create(new HouseCreateRequest("Import Broiler House", "IB1", null));
        var turkeyHouse = houseService.create(new HouseCreateRequest("Import Turkey House", "IT1", null));
        var layer = batchService.create(new BatchCreateRequest("LAYER", placed, layerHouse.id(), 10, null, 1000L));
        var broiler = batchService.create(new BatchCreateRequest("BROILER", placed, broilerHouse.id(), 10, null, 1000L));
        var turkey = batchService.create(new BatchCreateRequest("TURKEY", placed, turkeyHouse.id(), 10, null, 1000L));
        var feedType = feedService.createType(new FeedTypeCreateRequest(
                "Layer and Turkey Feed", "kg", null, BigDecimal.ONE, null, null, null,
                List.of("LAYER", "TURKEY")));
        feedService.purchase(new FeedPurchaseRequest(feedType.id(), null, placed,
                BigDecimal.TEN, "kg", 100_000L));

        var valid = feedHistoryImport.preview(new CsvImportRequest("feed-usage", List.of(
                Map.of("feed_type_name", "Layer and Turkey Feed", "batch_code", turkey.code(),
                        "usage_date", "2026-01-02", "quantity", "1", "unit", "kg", "reason", "Turkey feeding"))));
        var invalid = feedHistoryImport.preview(new CsvImportRequest("feed-usage", List.of(
                Map.of("feed_type_name", "Layer and Turkey Feed", "batch_code", broiler.code(),
                        "usage_date", "2026-01-02", "quantity", "1", "unit", "kg", "reason", "Invalid broiler feeding"))));

        assertThat(valid.get("valid")).isEqualTo(true);
        assertThat(invalid.get("valid")).isEqualTo(false);
        assertThat(invalid.get("rejected")).isEqualTo(1);
        assertThat(layer.type()).isEqualTo("LAYER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_usage", Integer.class)).isZero();
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
