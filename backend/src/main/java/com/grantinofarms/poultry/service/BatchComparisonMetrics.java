package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.dto.BirdPopulationResponse;
import com.grantinofarms.poultry.dto.BroilerGrowthResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds backend-owned comparison metrics from existing report calculations. */
public final class BatchComparisonMetrics {
    private BatchComparisonMetrics() {}

    public static Map<String, Object> from(
            BatchResponse batch,
            BirdPopulationResponse population,
            Map<String, Object> dashboard,
            Map<String, Object> period,
            LocalDate from,
            LocalDate to
    ) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("initialBirds", population.initialBirds());
        metrics.put("currentBirds", population.currentBirds());
        metrics.put("populationCountStatus", population.countStatus());

        long mortality = number(period.get("mortality"));
        metrics.put("mortality", mortality);
        metrics.put("mortalityRatePercent", number(period.get("mortalityEventCount")) > 0 && population.initialBirds() > 0
                ? BigDecimal.valueOf(mortality).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(population.initialBirds()), 2, RoundingMode.HALF_UP)
                : null);

        metrics.put("feedQuantityMilli", number(period.get("feedUsageCount")) > 0 ? period.get("feedQuantityMilli") : null);
        metrics.put("feedQuantityUnit", period.getOrDefault("feedQuantityUnit",
                "recorded feed units; feed products may use different units"));
        metrics.put("feedCostMinor", number(period.get("feedUsageCount")) > 0 ? period.get("feedCostMinor") : null);

        metrics.put("averageWeightKg", null);
        metrics.put("fcr", null);
        metrics.put("eggProduction", null);
        if ("LAYER".equals(batch.type())) {
            long good = number(period.get("eggsGood"));
            long cracked = number(period.get("eggsCracked"));
            metrics.put("eggProduction", number(period.get("eggCollectionCount")) > 0 ? Math.addExact(good, cracked) : null);
        } else {
            Object growthValue = dashboard.get("broilerGrowth");
            BroilerGrowthResponse growth = growthValue instanceof BroilerGrowthResponse value ? value : null;
            if (growth != null) {
                metrics.put("averageWeightKg", growth.averageWeightKg());
                metrics.put("fcr", growth.fcr());
            }
        }

        long revenue = number(period.get("revenueMinor"));
        long expenses = number(period.get("expensesMinor"));
        metrics.put("revenueMinor", revenue);
        metrics.put("expensesMinor", expenses);
        metrics.put("profitMinor", Math.subtractExact(revenue, expenses));
        metrics.put("periodStart", from);
        metrics.put("periodEnd", to);
        metrics.put("birdType", batch.type());
        metrics.put("houseId", batch.houseId());
        metrics.put("placementDate", batch.placementDate());
        return metrics;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
