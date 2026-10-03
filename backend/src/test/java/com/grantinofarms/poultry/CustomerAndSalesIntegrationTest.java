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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:customer-sales-test?mode=memory&cache=shared"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CustomerAndSalesIntegrationTest {
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;
    @Autowired CustomerService customerService;
    @Autowired EggManagementService eggService;
    @Autowired BroilerProductionService broilerService;
    @Autowired SalesService salesService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsUpdatesAndListsCustomers() {
        setupFarm();
        var customer = customerService.create(new CustomerCreateRequest(
                "Ada Foods", "08012345678", "Wholesale customer"));
        assertThat(customer.name()).isEqualTo("Ada Foods");
        assertThat(customer.phone()).isEqualTo("08012345678");

        var updated = customerService.update(customer.id(), new CustomerUpdateRequest(
                "Ada Foods Ltd", "08099999999", "Updated notes"));
        assertThat(updated.name()).isEqualTo("Ada Foods Ltd");
        assertThat(customerService.list()).hasSize(1);
    }

    @Test
    void eggSaleCreatesCommonSaleUsingCrateUnit() {
        var batch = setupLayer();
        var customer = customerService.create(new CustomerCreateRequest(
                "Egg Buyer", null, null));

        eggService.addCollection(batch.id(), new EggCollectionRequest(
                LocalDate.of(2026, 2, 1), 100, 0, null));
        var sale = eggService.addSale(batch.id(), new EggSaleRequest(
                LocalDate.of(2026, 2, 1), customer.name(), new BigDecimal("0.5"), 300_000L, customer.id()));

        assertThat(sale.soldEggs()).isEqualTo(15);

        var common = salesService.list(null, customer.id(), "EGG", LocalDate.of(2026, 2, 1));
        assertThat(common).hasSize(1);
        assertThat(common.get(0).quantity()).isEqualByComparingTo("0.5");
        assertThat(common.get(0).unit()).isEqualTo("CRATE");
        assertThat(common.get(0).unitPriceMinor()).isEqualTo(300_000L);
        assertThat(common.get(0).totalAmountMinor()).isEqualTo(150_000L);
    }

    @Test
    void birdSaleCreatesCommonSaleUsingBirdUnit() {
        var batch = setupBroiler();
        var customer = customerService.create(new CustomerCreateRequest(
                "Meat Buyer", null, null));

        var sale = broilerService.addSale(batch.id(), new BirdSaleRequest(
                LocalDate.of(2026, 1, 10), 10, 25_000L, customer.name(), false, customer.id()));

        assertThat(sale.totalAmountMinor()).isEqualTo(250_000L);

        var common = salesService.list(null, customer.id(), "BROILER", LocalDate.of(2026, 1, 10));
        assertThat(common).hasSize(1);
        assertThat(common.get(0).quantity()).isEqualByComparingTo("10");
        assertThat(common.get(0).unit()).isEqualTo("BIRD");
        assertThat(common.get(0).totalAmountMinor()).isEqualTo(250_000L);
    }

    @Test
    void historicalSalesAreBackfilledIntoCommonLedger() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        var batch = batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));

        jdbc.update("""
                INSERT INTO egg_collections (id, batch_id, record_date, good_eggs, cracked_eggs, notes, created_at)
                VALUES ('collection-1', ?, '2026-01-02', 60, 0, NULL, '2026-01-02T10:00:00Z')
                """, batch.id());
        jdbc.update("""
                INSERT INTO egg_sales (id, batch_id, record_date, customer, sold_eggs, crate_size,
                                       price_per_crate_minor, total_amount_minor, created_at)
                VALUES ('sale-1', ?, '2026-01-02', 'Legacy Buyer', 30, 30, 300_000, 300_000, '2026-01-02T11:00:00Z')
                """, batch.id());

        // V14 runs before this test's runtime inserts, so simulate the backfill contract explicitly
        jdbc.update("""
                INSERT INTO customers (id, farm_id, name, created_at, updated_at)
                VALUES ('legacy-customer', ?, 'Legacy Buyer', '2026-01-02T10:00:00Z', '2026-01-02T10:00:00Z')
                """, farmService.get().id());
        jdbc.update("UPDATE egg_sales SET customer_id = 'legacy-customer' WHERE id = 'sale-1'");
        jdbc.update("""
                INSERT INTO sales (
                    id, farm_id, batch_id, customer_id, sale_date, sale_type,
                    quantity, unit, unit_price_minor, total_amount_minor,
                    reference_type, reference_id, created_at
                )
                VALUES ('sale-1', ?, ?, 'legacy-customer', '2026-01-02', 'EGG',
                        1, 'CRATE', 300000, 300000, 'EGG_SALE', 'sale-1', '2026-01-02T11:00:00Z')
                """, farmService.get().id(), batch.id());

        var common = salesService.list(null, "legacy-customer", "EGG", LocalDate.of(2026, 1, 2));
        assertThat(common).hasSize(1);
        assertThat(common.get(0).customerName()).isEqualTo("Legacy Buyer");
    }

    private BatchResponse setupLayer() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Layer House", "LH1", null));
        return batchService.create(new BatchCreateRequest(
                "LAYER", LocalDate.of(2026, 1, 1), house.id(), 500, null, 5_000_000L));
    }

    private BatchResponse setupBroiler() {
        setupFarm();
        var house = houseService.create(new HouseCreateRequest("Broiler House", "BH1", null));
        return batchService.create(new BatchCreateRequest(
                "BROILER", LocalDate.of(2026, 1, 1), house.id(), 100, null, 1_000_000L));
    }

    private void setupFarm() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
    }
}
