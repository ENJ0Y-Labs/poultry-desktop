package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.BatchResponse;
import com.grantinofarms.poultry.dto.BirdPopulationResponse;
import com.grantinofarms.poultry.dto.BroilerGrowthResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BatchComparisonMetricsTest {
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    @Test
    void layerComparisonUsesPeriodEggsAndExcludesMeatBirdMetrics() {
        Map<String, Object> period = Map.of(
                "mortality", 10L, "mortalityEventCount", 2L,
                "eggCollectionCount", 4L, "eggsGood", 1000L, "eggsCracked", 25L,
                "feedUsageCount", 3L, "feedQuantityMilli", 45000L, "feedQuantityComparable", true, "feedCostMinor", 120000L,
                "revenueMinor", 500000L, "expensesMinor", 200000L
        );

        Map<String, Object> metrics = BatchComparisonMetrics.from(
                batch("LAYER", "layer-1", LocalDate.of(2026, 1, 1)),
                population("layer-1", 500, 10, 490),
                Map.of(), period, FROM, TO);

        assertEquals(1025L, metrics.get("eggProduction"));
        assertNull(metrics.get("averageWeightKg"));
        assertNull(metrics.get("fcr"));
        assertEquals(new BigDecimal("2.00"), metrics.get("mortalityRatePercent"));
        assertEquals(300000L, metrics.get("profitMinor"));
        assertEquals(FROM, metrics.get("periodStart"));
        assertEquals(TO, metrics.get("periodEnd"));
        assertEquals(LocalDate.of(2026, 1, 1), metrics.get("placementDate"));
    }

    @Test
    void meatBirdComparisonUsesBackendGrowthAndExcludesEggProduction() {
        Map<String, Object> dashboard = Map.of("broilerGrowth",
                new BroilerGrowthResponse("broiler-1", TO, new BigDecimal("1.35"),
                        new BigDecimal("0.70"), new BigDecimal("320.00"),
                        new BigDecimal("1.75"), List.of()));
        Map<String, Object> period = Map.of(
                "mortality", 5L, "mortalityEventCount", 1L,
                "eggCollectionCount", 0L, "eggsGood", 0L, "eggsCracked", 0L,
                "feedUsageCount", 2L, "feedQuantityMilli", 18000L, "feedQuantityComparable", true, "feedCostMinor", 80000L,
                "revenueMinor", 450000L, "expensesMinor", 100000L
        );

        Map<String, Object> metrics = BatchComparisonMetrics.from(
                batch("BROILER", "broiler-1", LocalDate.of(2026, 3, 15)),
                population("broiler-1", 250, 5, 245),
                dashboard, period, FROM, TO);

        assertEquals(new BigDecimal("1.35"), metrics.get("averageWeightKg"));
        assertEquals(new BigDecimal("1.75"), metrics.get("fcr"));
        assertNull(metrics.get("eggProduction"));
        assertEquals(new BigDecimal("2.00"), metrics.get("mortalityRatePercent"));
    }

    @Test
    void absentActivityRecordsRemainNullRatherThanInventingZero() {
        Map<String, Object> period = Map.of(
                "mortality", 0L, "mortalityEventCount", 0L,
                "eggCollectionCount", 0L, "eggsGood", 0L, "eggsCracked", 0L,
                "feedUsageCount", 0L, "revenueMinor", 0L, "expensesMinor", 0L
        );

        Map<String, Object> metrics = BatchComparisonMetrics.from(
                batch("LAYER", "layer-2", LocalDate.of(2026, 6, 1)),
                population("layer-2", 100, 0, 100),
                Map.of(), period, FROM, TO);

        assertNull(metrics.get("mortalityRatePercent"));
        assertNull(metrics.get("eggProduction"));
        assertNull(metrics.get("feedQuantityMilli"));
        assertNull(metrics.get("feedCostMinor"));
        assertEquals(0L, metrics.get("profitMinor"));
    }

    @Test
    void mixedFeedUnitsKeepCostButDoNotExposeAnAggregatedQuantity() {
        Map<String, Object> period = Map.of(
                "mortality", 0L, "mortalityEventCount", 0L,
                "eggCollectionCount", 0L, "eggsGood", 0L, "eggsCracked", 0L,
                "feedUsageCount", 2L, "feedQuantityMilli", 15000L,
                "feedQuantityComparable", false, "feedQuantityUnit", "Mixed units: bag, kg",
                "feedCostMinor", 42000L, "revenueMinor", 0L, "expensesMinor", 0L
        );

        Map<String, Object> metrics = BatchComparisonMetrics.from(
                batch("BROILER", "broiler-mixed", LocalDate.of(2026, 7, 1)),
                population("broiler-mixed", 100, 0, 100),
                Map.of(), period, FROM, TO);

        assertNull(metrics.get("feedQuantityMilli"));
        assertEquals("Mixed units: bag, kg", metrics.get("feedQuantityUnit"));
        assertEquals(42_000L, metrics.get("feedCostMinor"));
        assertEquals(false, metrics.get("feedQuantityComparable"));
    }

    private static BatchResponse batch(String type, String id, LocalDate placement) {
        return new BatchResponse(id, "farm-1", "house-1", null, id.toUpperCase(), type,
                placement, 100, null, "ACTIVE", "created", "updated", "VERIFIED");
    }

    private static BirdPopulationResponse population(String id, int initial, int mortality, int current) {
        return new BirdPopulationResponse(id, TO, initial, mortality, 0, 0, 0, 0, current, "VERIFIED");
    }
}
