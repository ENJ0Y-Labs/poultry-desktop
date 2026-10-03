package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.dto.BirdPopulationResponse;
import com.grantinofarms.poultry.dto.ExpenseResponse;
import com.grantinofarms.poultry.service.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
                expenses, sales, inventory, health
        );

        Map<String, Object> report = service.farm(asOf);

        assertThat(report.get("dashboard")).isSameAs(dashboardData);
        assertThat(report.get("revenueMinor")).isEqualTo(500000L);
        assertThat(report.get("profitMinor")).isEqualTo(300000L);

        verify(dashboard).farm(asOf);
        verify(population).get("layer-1", asOf);
        verify(population).get("broiler-1", asOf);
        verify(eggs).inventory("layer-1", asOf);
        verify(broilers).growth("broiler-1", asOf);
        verify(sales).list(null, null, null, asOf);
        verifyNoInteractions(birdCost, health);
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
                expenses, sales, inventory, health
        );

        Map<String, Object> report = service.batch("batch-1", asOf);

        assertThat(report.get("dashboard")).isSameAs(dashboardData);
        assertThat(report.get("population")).isSameAs(populationData);
        assertThat(report.get("mortality")).isEqualTo(12);
        assertThat(report.get("profitMinor")).isEqualTo(500000L);

        verify(dashboard).batch("batch-1", asOf);
        verify(population).get("batch-1", asOf);
        verify(birdCost).get("batch-1", asOf);
        verify(eggs).collections("batch-1", asOf);
        verify(health).health("batch-1", asOf);
        verify(sales).list("batch-1", null, null, asOf);
        verifyNoInteractions(feed, expenses, broilers, inventory);
    }
}
