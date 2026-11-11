package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.dto.BirdPopulationResponse;
import com.grantinofarms.poultry.dto.ExpenseResponse;
import com.grantinofarms.poultry.dto.SaleResponse;
import com.grantinofarms.poultry.dto.FarmResponse;
import com.grantinofarms.poultry.service.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReportServiceTest {

    @Test
    void farmReportDelegatesMetricsToTheSameServicesUsedByDashboards() {
        LocalDate asOf = LocalDate.of(2026, 1, 10);
        DashboardService dashboard = mock(DashboardService.class);
        BatchService batches = mock(BatchService.class);
        BirdPopulationService population = mock(BirdPopulationService.class);
        BirdCostService birdCost = mock(BirdCostService.class);
        FeedService feed = mock(FeedService.class);
        EggManagementService eggs = mock(EggManagementService.class);
        BroilerProductionService broilers = mock(BroilerProductionService.class);
        ExpenseService expenses = mock(ExpenseService.class);
        SalesService sales = mock(SalesService.class);
        InventoryService inventory = mock(InventoryService.class);
        HealthManagementService health = mock(HealthManagementService.class);
        FarmService farmService = mock(FarmService.class);
        when(farmService.getWaterUnitLabel()).thenReturn("litres");

        BatchResponse layer = mock(BatchResponse.class);
        when(layer.id()).thenReturn("layer-1");
        when(layer.type()).thenReturn("LAYER");

        BatchResponse broiler = mock(BatchResponse.class);
        when(broiler.id()).thenReturn("broiler-1");
        when(broiler.type()).thenReturn("BROILER");

        BirdPopulationResponse layerPopulation = mock(BirdPopulationResponse.class);
        BirdPopulationResponse broilerPopulation = mock(BirdPopulationResponse.class);
        when(layerPopulation.mortality()).thenReturn(3);
        when(broilerPopulation.mortality()).thenReturn(4);

        Map<String, Object> dashboardData = Map.of(
                "totalBirds", 993,
                "mortality", 7,
                "revenueMinor", 500000L,
                "profitMinor", 300000L
        );

        when(dashboard.farm(asOf)).thenReturn(dashboardData);
        when(batches.list()).thenReturn(List.of(layer, broiler));
        when(population.get("layer-1", asOf)).thenReturn(layerPopulation);
        when(population.get("broiler-1", asOf)).thenReturn(broilerPopulation);
        when(eggs.inventory("layer-1", asOf)).thenReturn(mock(com.grantinofarms.poultry.dto.EggInventoryResponse.class));
        when(broilers.growth("broiler-1", asOf)).thenReturn(mock(com.grantinofarms.poultry.dto.BroilerGrowthResponse.class));
        when(expenses.list(null, asOf)).thenReturn(List.<ExpenseResponse>of());
        when(sales.list(null, null, null, asOf)).thenReturn(List.of());
        when(feed.inventory(asOf)).thenReturn(List.of());
        when(inventory.listItems()).thenReturn(List.of());
        when(inventory.movements(null, asOf)).thenReturn(List.of());

        ReportService service = new ReportService(
                dashboard, batches, population, birdCost, feed, eggs, broilers,
                expenses, sales, inventory, health, farmService
        );

        Map<String, Object> report = service.farm(asOf);

        assertThat(report.get("dashboard")).isSameAs(dashboardData);
        assertThat(report.get("revenueMinor")).isEqualTo(500000L);
        assertThat(report.get("profitMinor")).isEqualTo(300000L);
        assertThat(report.get("waterUnitLabel")).isEqualTo("litres");

        verify(dashboard).farm(asOf);
        verify(population).get("layer-1", asOf);
        verify(population).get("broiler-1", asOf);
        verify(eggs).inventory("layer-1", asOf);
        verify(broilers).growth("broiler-1", asOf);
        verify(sales).list(null, null, null, asOf);
        verifyNoInteractions(birdCost, health);
    }

    @Test
    void farmRangeFiltersRowsAndKeepsPeriodTotalsReconciledToTheReturnedRecords() {
        LocalDate from = LocalDate.of(2026, 2, 28);
        LocalDate to = LocalDate.of(2026, 3, 1);
        LocalDate asOf = LocalDate.now(ZoneId.of("Africa/Lagos"));
        DashboardService dashboard = mock(DashboardService.class);
        BatchService batches = mock(BatchService.class);
        BirdPopulationService population = mock(BirdPopulationService.class);
        BirdCostService birdCost = mock(BirdCostService.class);
        FeedService feed = mock(FeedService.class);
        EggManagementService eggs = mock(EggManagementService.class);
        BroilerProductionService broilers = mock(BroilerProductionService.class);
        ExpenseService expenses = mock(ExpenseService.class);
        SalesService sales = mock(SalesService.class);
        InventoryService inventory = mock(InventoryService.class);
        HealthManagementService health = mock(HealthManagementService.class);
        FarmService farmService = mock(FarmService.class);
        when(farmService.get()).thenReturn(new FarmResponse(
                "farm-1", "Test Farm", "Port Harcourt", "Africa/Lagos", "NGN",
                "ACTIVE", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"));
        when(farmService.getWaterUnitLabel()).thenReturn("litres");
        when(dashboard.farm(asOf)).thenReturn(Map.of(
                "asOf", asOf, "totalBirds", 90, "mortality", 8,
                "revenueMinor", 9000L, "expensesMinor", 7000L, "profitMinor", 2000L));
        Map<String, Object> period = Map.of(
                "from", from, "to", to, "mortality", 3L, "eggsGood", 30L,
                "revenueMinor", 3000L, "expensesMinor", 1000L,
                "salesCount", 2L, "expenseCount", 2L);
        when(dashboard.farmRange(from, to)).thenReturn(Map.of("period", period));
        when(batches.list()).thenReturn(List.of());

        ExpenseResponse expenseFrom = new ExpenseResponse("expense-1", "farm-1", null, from,
                "Feed", 400L, "FEED", null, null, "2026-02-28T08:00:00Z");
        ExpenseResponse expenseTo = new ExpenseResponse("expense-2", "farm-1", null, to,
                "Medicine", 600L, "DRUGS", null, null, "2026-03-01T08:00:00Z");
        ExpenseResponse expenseOutside = new ExpenseResponse("expense-3", "farm-1", null,
                LocalDate.of(2026, 3, 2), "Outside", 9000L, "OTHER", null, null, "2026-03-02T08:00:00Z");
        when(expenses.list(null, asOf)).thenReturn(List.of(expenseFrom, expenseTo, expenseOutside));
        SaleResponse saleFrom = new SaleResponse("sale-1", "farm-1", "batch-1", "customer-1",
                "Customer", null, from, "EGG", java.math.BigDecimal.ONE, "CRATE", 1000L, 1000L, "TEST", "sale-1");
        SaleResponse saleTo = new SaleResponse("sale-2", "farm-1", "batch-1", "customer-1",
                "Customer", null, to, "EGG", java.math.BigDecimal.ONE, "CRATE", 2000L, 2000L, "TEST", "sale-2");
        SaleResponse saleOutside = new SaleResponse("sale-3", "farm-1", "batch-1", "customer-1",
                "Customer", null, LocalDate.of(2026, 3, 2), "EGG", java.math.BigDecimal.ONE, "CRATE", 9000L, 9000L, "TEST", "sale-3");
        when(sales.list(null, null, null, asOf)).thenReturn(List.of(saleFrom, saleTo, saleOutside));
        when(feed.inventory(asOf)).thenReturn(List.of());
        when(inventory.listItems()).thenReturn(List.of());
        when(inventory.movements(null, asOf)).thenReturn(List.of());

        ReportService service = new ReportService(
                dashboard, batches, population, birdCost, feed, eggs, broilers,
                expenses, sales, inventory, health, farmService);

        Map<String, Object> report = service.farm(from, to);

        @SuppressWarnings("unchecked")
        List<ExpenseResponse> returnedExpenses = (List<ExpenseResponse>) report.get("expenses");
        @SuppressWarnings("unchecked")
        List<SaleResponse> returnedSales = (List<SaleResponse>) report.get("sales");
        assertThat(returnedExpenses).containsExactly(expenseFrom, expenseTo);
        assertThat(returnedSales).containsExactly(saleFrom, saleTo);
        assertThat(period.get("expenseCount")).isEqualTo(returnedExpenses.size());
        assertThat(period.get("salesCount")).isEqualTo(returnedSales.size());
        assertThat(report.get("period")).isSameAs(period);
        verify(dashboard).farmRange(from, to);
    }

    @Test
    void batchReportReusesDashboardAndDomainServicesInsteadOfReimplementingFormulas() {
        LocalDate asOf = LocalDate.of(2026, 1, 10);
        DashboardService dashboard = mock(DashboardService.class);
        BatchService batches = mock(BatchService.class);
        BirdPopulationService population = mock(BirdPopulationService.class);
        BirdCostService birdCost = mock(BirdCostService.class);
        FeedService feed = mock(FeedService.class);
        EggManagementService eggs = mock(EggManagementService.class);
        BroilerProductionService broilers = mock(BroilerProductionService.class);
        ExpenseService expenses = mock(ExpenseService.class);
        SalesService sales = mock(SalesService.class);
        InventoryService inventory = mock(InventoryService.class);
        HealthManagementService health = mock(HealthManagementService.class);
        FarmService farmService = mock(FarmService.class);
        when(farmService.getWaterUnitLabel()).thenReturn("litres");

        BatchResponse batch = mock(BatchResponse.class);
        when(batch.id()).thenReturn("batch-1");
        when(batch.type()).thenReturn("LAYER");

        BirdPopulationResponse populationData = mock(BirdPopulationResponse.class);
        when(populationData.mortality()).thenReturn(12);

        Map<String, Object> dashboardData = Map.of(
                "population", populationData,
                "feed", mock(com.grantinofarms.poultry.dto.BatchFeedCostResponse.class),
                "expensesMinor", 200000L,
                "revenueMinor", 700000L,
                "profitMinor", 500000L,
                "eggInventory", mock(com.grantinofarms.poultry.dto.EggInventoryResponse.class),
                "eggQuality", Map.of("goodRatePercent", 98.0)
        );

        when(dashboard.batch("batch-1", asOf)).thenReturn(dashboardData);
        when(batches.get("batch-1")).thenReturn(batch);
        when(population.get("batch-1", asOf)).thenReturn(populationData);
        when(birdCost.get("batch-1", asOf)).thenReturn(mock(com.grantinofarms.poultry.dto.BirdCostResponse.class));
        when(eggs.collections("batch-1", asOf)).thenReturn(List.of());
        when(eggs.sales("batch-1", asOf)).thenReturn(List.of());
        when(health.health("batch-1", asOf)).thenReturn(List.of());
        when(health.drugs("batch-1", asOf)).thenReturn(List.of());
        when(health.vaccinations("batch-1", asOf)).thenReturn(List.of());
        when(sales.list("batch-1", null, null, asOf)).thenReturn(List.of());

        ReportService service = new ReportService(
                dashboard, batches, population, birdCost, feed, eggs, broilers,
                expenses, sales, inventory, health, farmService
        );

        Map<String, Object> report = service.batch("batch-1", asOf);

        assertThat(report.get("dashboard")).isSameAs(dashboardData);
        assertThat(report.get("population")).isSameAs(populationData);
        assertThat(report.get("mortality")).isEqualTo(12);
        assertThat(report.get("profitMinor")).isEqualTo(500000L);
        assertThat(report.get("waterUnitLabel")).isEqualTo("litres");

        verify(dashboard).batch("batch-1", asOf);
        verify(population).get("batch-1", asOf);
        verify(birdCost).get("batch-1", asOf);
        verify(eggs).collections("batch-1", asOf);
        verify(health).health("batch-1", asOf);
        verify(sales).list("batch-1", null, null, asOf);
        verifyNoInteractions(feed, expenses, broilers, inventory);
    }
}
