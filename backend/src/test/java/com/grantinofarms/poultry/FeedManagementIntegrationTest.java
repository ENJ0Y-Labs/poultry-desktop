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
        assertThat(jdbc.queryForObject("SELECT SUM(amount_minor) FROM bird_additional_costs WHERE batch_id = ?", Long.class, batch.id())).isEqualTo(210_000L);
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

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
