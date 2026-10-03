package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.dto.BirdPopulationResponse;
import com.grantinofarms.poultry.dto.ExpenseResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private final DashboardService dashboard;
    private final BatchService batches;
    private final BirdPopulationService population;
    private final BirdCostService birdCost;
    private final FeedService feed;
    private final EggManagementService eggs;
    private final BroilerProductionService broilers;
    private final ExpenseService expenses;
    private final SalesService sales;
    private final InventoryService inventory;
    private final HealthManagementService health;

    public ReportService(
            DashboardService dashboard,
            BatchService batches,
            BirdPopulationService population,
            BirdCostService birdCost,
            FeedService feed,
            EggManagementService eggs,
            BroilerProductionService broilers,
            ExpenseService expenses,
            SalesService sales,
            InventoryService inventory,
            HealthManagementService health
    ) {
        this.dashboard = dashboard;
        this.batches = batches;
        this.population = population;
        this.birdCost = birdCost;
        this.feed = feed;
        this.eggs = eggs;
        this.broilers = broilers;
        this.expenses = expenses;
        this.sales = sales;
        this.inventory = inventory;
        this.health = health;
    }

    public Map<String, Object> farm(LocalDate asOf) {
        LocalDate date = effectiveDate(asOf);
        Map<String, Object> dashboardData = dashboard.farm(date);
        List<BatchResponse> farmBatches = batches.list();

        List<Map<String, Object>> populationRows = farmBatches.stream()
                .map(batch -> batchPopulation(batch, date))
                .toList();

        List<Map<String, Object>> eggRows = farmBatches.stream()
                .filter(batch -> "LAYER".equals(batch.type()))
                .map(batch -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("batch", batch);
                    row.put("production", eggs.inventory(batch.id(), date));
                    return row;
                })
                .toList();

        List<Map<String, Object>> broilerRows = farmBatches.stream()
                .filter(batch -> "BROILER".equals(batch.type()))
                .map(batch -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("batch", batch);
                    row.put("growth", broilers.growth(batch.id(), date));
                    return row;
                })
                .toList();

        List<ExpenseResponse> expenseRows = expenses.list(null).stream()
                .filter(expense -> !expense.occurredDate().isAfter(date))
                .toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "FARM_SUMMARY");
        report.put("asOf", date);
        report.put("dashboard", dashboardData);
        report.put("farmSummary", dashboardData);
        report.put("birdPopulation", populationRows);
        report.put("mortality", Map.of(
                "total", dashboardData.get("mortality"),
                "byBatch", populationRows.stream()
                        .map(row -> Map.of(
                                "batch", row.get("batch"),
                                "mortality", ((BirdPopulationResponse) row.get("population")).mortality()
                        ))
                        .toList()
        ));
        report.put("feed", feed.inventory(date));
        report.put("expenses", expenseRows);
        report.put("revenueMinor", dashboardData.get("revenueMinor"));
        report.put("profitMinor", dashboardData.get("profitMinor"));
        report.put("eggProduction", eggRows);
        report.put("broilerGrowth", broilerRows);
        report.put("sales", sales.list(null, null, null, date));
        report.put("inventory", inventory.listItems());
        report.put("inventoryMovements", inventory.movements(null, date));
        return report;
    }

    public Map<String, Object> batch(String id, LocalDate asOf) {
        LocalDate date = effectiveDate(asOf);
        Map<String, Object> dashboardData = dashboard.batch(id, date);
        BatchResponse batch = batches.get(id);
        BirdPopulationResponse populationData = population.get(id, date);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "BATCH_SUMMARY");
        report.put("asOf", date);
        report.put("dashboard", dashboardData);
        report.put("batchSummary", dashboardData);
        report.put("population", populationData);
        report.put("mortality", populationData.mortality());
        report.put("feed", dashboardData.get("feed"));
        report.put("costs", Map.of(
                "birdCost", birdCost.get(id, date),
                "feed", dashboardData.get("feed"),
                "expensesMinor", dashboardData.get("expensesMinor")
        ));
        report.put("revenueMinor", dashboardData.get("revenueMinor"));
        report.put("profitMinor", dashboardData.get("profitMinor"));
        report.put("production", production(batch, date, dashboardData));
        report.put("health", Map.of(
                "healthRecords", health.health(id, date),
                "drugs", health.drugs(id, date),
                "vaccinations", health.vaccinations(id, date)
        ));
        report.put("sales", sales.list(id, null, null, date));
        return report;
    }

    private Map<String, Object> batchPopulation(BatchResponse batch, LocalDate date) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("batch", batch);
        row.put("population", population.get(batch.id(), date));
        return row;
    }

    private Object production(BatchResponse batch, LocalDate date, Map<String, Object> dashboardData) {
        if ("LAYER".equals(batch.type())) {
            Map<String, Object> production = new LinkedHashMap<>();
            production.put("type", "LAYER");
            production.put("eggInventory", dashboardData.get("eggInventory"));
            production.put("eggQuality", dashboardData.get("eggQuality"));
            production.put("collections", eggs.collections(batch.id(), date));
            production.put("sales", eggs.sales(batch.id(), date));
            return production;
        }

        Map<String, Object> production = new LinkedHashMap<>();
        production.put("type", "BROILER");
        production.put("growth", dashboardData.get("broilerGrowth"));
        production.put("sales", dashboardData.get("broilerSales"));
        production.put("weights", broilers.weights(batch.id(), date));
        return production;
    }

    private LocalDate effectiveDate(LocalDate asOf) {
        return asOf == null ? LocalDate.now() : asOf;
    }
}
