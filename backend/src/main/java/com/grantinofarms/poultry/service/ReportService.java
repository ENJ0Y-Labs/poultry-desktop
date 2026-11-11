package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.dto.BirdPopulationResponse;
import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.http.HttpStatus;
import com.grantinofarms.poultry.dto.ExpenseResponse;
import com.grantinofarms.poultry.dto.SaleResponse;
import com.grantinofarms.poultry.dto.HealthRecordResponse;
import com.grantinofarms.poultry.dto.DrugRecordResponse;
import com.grantinofarms.poultry.dto.VaccinationRecordResponse;
import com.grantinofarms.poultry.dto.EggCollectionResponse;
import com.grantinofarms.poultry.dto.EggSaleResponse;
import com.grantinofarms.poultry.dto.WeightRecordResponse;
import com.grantinofarms.poultry.dto.BirdSaleResponse;
import com.grantinofarms.poultry.domain.ReportDateRange;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Function;
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
    private final FarmService farmService;

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
            HealthManagementService health,
            FarmService farmService
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
        this.farmService = farmService;
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
                .filter(batch -> "BROILER".equals(batch.type()) || "TURKEY".equals(batch.type()))
                .map(batch -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("batch", batch);
                    row.put("growth", broilers.growth(batch.id(), date));
                    return row;
                })
                .toList();

        List<ExpenseResponse> expenseRows = expenses.list(null, date);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "FARM_SUMMARY");
        report.put("asOf", date);
        report.put("waterUnitLabel", farmService.getWaterUnitLabel());
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
        List<Map<String, Object>> turkeyRows = broilerRows.stream()
                .filter(row -> "TURKEY".equals(((BatchResponse) row.get("batch")).type()))
                .toList();
        List<Map<String, Object>> broilerOnlyRows = broilerRows.stream()
                .filter(row -> "BROILER".equals(((BatchResponse) row.get("batch")).type()))
                .toList();
        report.put("broilerGrowth", broilerOnlyRows);
        report.put("turkeyGrowth", turkeyRows);
        report.put("meatBirdGrowth", broilerRows);
        report.put("sales", sales.list(null, null, null, date));
        report.put("inventory", inventory.listItems());
        report.put("inventoryMovements", inventory.movements(null, date));
        return report;
    }

    public Map<String, Object> farm(LocalDate from, LocalDate to) {
        ReportDateRange range = ReportDateRange.require(from, to);
        Map<String, Object> report = farm((LocalDate) null);
        Map<String, Object> rangeDashboard = dashboard.farmRange(range.from(), range.to());
        report.put("from", range.from());
        report.put("to", range.to());
        report.put("period", rangeDashboard.get("period"));
        @SuppressWarnings("unchecked")
        List<ExpenseResponse> expenseRows = (List<ExpenseResponse>) report.get("expenses");
        report.put("expenses", expenseRows.stream()
                .filter(row -> range.includes(row.occurredDate()))
                .toList());
        @SuppressWarnings("unchecked")
        List<SaleResponse> salesRows = (List<SaleResponse>) report.get("sales");
        report.put("sales", salesRows.stream()
                .filter(row -> range.includes(row.saleDate()))
                .toList());
        return report;
    }

    public Map<String, Object> batch(String id, LocalDate from, LocalDate to) {
        ReportDateRange range = ReportDateRange.require(from, to);
        Map<String, Object> report = batch(id, (LocalDate) null);
        Map<String, Object> rangeDashboard = dashboard.batchRange(id, range.from(), range.to());
        report.put("from", range.from());
        report.put("to", range.to());
        @SuppressWarnings("unchecked")
        Map<String, Object> period = (Map<String, Object>) rangeDashboard.get("period");
        report.put("period", period);
        @SuppressWarnings("unchecked")
        List<SaleResponse> salesRows = (List<SaleResponse>) report.get("sales");
        report.put("sales", salesRows.stream()
                .filter(row -> range.includes(row.saleDate()))
                .toList());
        @SuppressWarnings("unchecked")
        Map<String, Object> healthRows = (Map<String, Object>) report.get("health");
        Map<String, Object> filteredHealth = new LinkedHashMap<>(healthRows);
        filteredHealth.put("healthRecords", filterDates(
                (List<HealthRecordResponse>) healthRows.get("healthRecords"), range, HealthRecordResponse::recordDate));
        filteredHealth.put("drugs", filterDates(
                (List<DrugRecordResponse>) healthRows.get("drugs"), range, DrugRecordResponse::recordDate));
        filteredHealth.put("vaccinations", filterDates(
                (List<VaccinationRecordResponse>) healthRows.get("vaccinations"), range, VaccinationRecordResponse::recordDate));
        report.put("health", filteredHealth);
        @SuppressWarnings("unchecked")
        Map<String, Object> productionRows = (Map<String, Object>) report.get("production");
        Map<String, Object> filteredProduction = new LinkedHashMap<>(productionRows);
        if ("LAYER".equals(productionRows.get("type"))) {
            filteredProduction.put("collections", filterDates(
                    (List<EggCollectionResponse>) productionRows.get("collections"), range, EggCollectionResponse::recordDate));
            filteredProduction.put("sales", filterDates(
                    (List<EggSaleResponse>) productionRows.get("sales"), range, EggSaleResponse::recordDate));
        } else {
            filteredProduction.put("weights", filterDates(
                    (List<WeightRecordResponse>) productionRows.get("weights"), range, WeightRecordResponse::recordDate));
            filteredProduction.put("sales", filterDates(
                    (List<BirdSaleResponse>) productionRows.get("sales"), range, BirdSaleResponse::recordDate));
        }
        report.put("production", filteredProduction);
        return report;
    }

    public Map<String, Object> compare(List<String> batchIds, LocalDate from, LocalDate to) {
        ReportDateRange range = ReportDateRange.require(from, to);
        if (batchIds == null || batchIds.size() < 2 || batchIds.size() > 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMPARISON_BATCH_COUNT",
                    "Select between 2 and 8 batches to compare.");
        }
        if (batchIds.stream().anyMatch(id -> id == null || id.isBlank())
                || new java.util.HashSet<>(batchIds).size() != batchIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COMPARISON_BATCH_IDS",
                    "Batch selections must be non-empty and unique.");
        }

        List<Map<String, Object>> reports = new java.util.ArrayList<>();
        for (String batchId : batchIds) {
            Map<String, Object> report = batch(batchId, range.to());
            Map<String, Object> rangeDashboard = dashboard.batchRange(batchId, range.from(), range.to());
            @SuppressWarnings("unchecked")
            Map<String, Object> period = (Map<String, Object>) rangeDashboard.get("period");
            report.put("from", range.from());
            report.put("to", range.to());
            report.put("period", period);
            BatchResponse batch = batches.get(batchId);
            report.put("batch", batch);
            BirdPopulationResponse populationData = (BirdPopulationResponse) report.get("population");
            @SuppressWarnings("unchecked")
            Map<String, Object> dashboardData = (Map<String, Object>) report.get("dashboard");
            report.put("comparisonMetrics", BatchComparisonMetrics.from(
                    batch, populationData, dashboardData, period, range.from(), range.to()));
            reports.add(report);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", range.from());
        result.put("to", range.to());
        result.put("reports", reports);
        return result;
    }

    private <T> List<T> filterDates(List<T> rows, ReportDateRange range, Function<T, LocalDate> date) {
        return rows.stream().filter(row -> range.includes(date.apply(row))).toList();
    }

    public Map<String, Object> batch(String id, LocalDate asOf) {
        LocalDate date = effectiveDate(asOf);
        Map<String, Object> dashboardData = dashboard.batch(id, date);
        BatchResponse batch = batches.get(id);
        BirdPopulationResponse populationData = population.get(id, date);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "BATCH_SUMMARY");
        report.put("asOf", date);
        report.put("waterUnitLabel", farmService.getWaterUnitLabel());
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
        production.put("type", batch.type());
        production.put("growth", dashboardData.get("broilerGrowth"));
        production.put("sales", dashboardData.get("broilerSales"));
        production.put("weights", broilers.weights(batch.id(), date));
        return production;
    }

    private LocalDate effectiveDate(LocalDate asOf) {
        return asOf == null ? LocalDate.now(ZoneId.of(farmService.get().timezone())) : asOf;
    }
}
