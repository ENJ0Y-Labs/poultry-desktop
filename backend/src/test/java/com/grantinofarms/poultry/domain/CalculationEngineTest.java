package com.grantinofarms.poultry.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculationEngineTest {
    private static final Instant T1 = Instant.parse("2026-01-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T08:00:00Z");

    @Test
    void knownPopulationVectorProduces4827Birds() {
        var events = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "MORTALITY", 83),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 3), "CULLING", 20),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 4), "SOLD", 70)
        );

        assertThat(BirdPopulationCalculator.currentBirds(
                5000, events, LocalDate.of(2026, 1, 4)
        )).isEqualTo(4827);
    }

    @Test
    void livabilityUsesMortalityOnly() {
        assertThat(ProductionMetricsCalculator.livabilityPercent(5000, 103))
                .isEqualByComparingTo("97.94");
    }

    @Test
    void hdpKnownVectorProduces87Point5Percent() {
        assertThat(ProductionMetricsCalculator.hdpPercent(875, 1000))
                .isEqualByComparingTo("87.5");
    }

    @Test
    void fcrKnownVectorProduces2Point0() {
        assertThat(FcrCalculator.calculate(
                new BigDecimal("400"), new BigDecimal("200")
        )).isEqualByComparingTo("2.0");
    }

    @Test
    void undefinedRatiosReturnNull() {
        assertThat(FcrCalculator.calculate(BigDecimal.ZERO, BigDecimal.ZERO)).isNull();
        assertThat(ProductionMetricsCalculator.hdpPercent(0, 0)).isNull();
        assertThat(ProductionMetricsCalculator.livabilityPercent(0, 0)).isNull();
        assertThat(ProductionMetricsCalculator.marginPercent(0, 0)).isNull();
    }

    @Test
    void fifoConsumesOldestFeedLotFirstAndKeepsCostAccountedFor() {
        var first = new FeedPurchaseLot(
                "p1", "f1", LocalDate.of(2026, 1, 1), 20_000, 160_000, T1);
        var second = new FeedPurchaseLot(
                "p2", "f1", LocalDate.of(2026, 1, 2), 30_000, 300_000, T2);
        var usage = new FeedUsageEvent(
                "u1", "b1", "f1", LocalDate.of(2026, 1, 3), 25_000, T2);

        var result = FeedCostCalculator.calculate(
                List.of(first, second), List.of(usage), LocalDate.of(2026, 1, 3));

        assertThat(result.consumedQuantityMilli()).isEqualTo(25_000);
        assertThat(result.consumedCostMinor()).isEqualTo(210_000);
        assertThat(result.remainingQuantityMilli()).isEqualTo(25_000);
        assertThat(result.remainingCostMinor()).isEqualTo(250_000);
        assertThat(result.usageCostsMinor()).containsEntry("u1", 210_000L);
    }

    @Test
    void costAllocationFollowsCurrentAverageCost() {
        var sale = new BirdPopulationEvent(
                LocalDate.of(2026, 1, 2), "SOLD", 20);

        assertThat(BirdCostCalculator.costForReduction(
                100, 1_000_000L, List.of(), List.of(), sale
        )).isEqualTo(200_000L);
    }

    @Test
    void eggInventoryAndCrateConversionAreConsistent() {
        assertThat(EggInventoryCalculator.goodRemaining(100, 40)).isEqualTo(60);
        assertThat(EggInventoryCalculator.totalCollected(100, 7)).isEqualTo(107);
        assertThat(ProductionMetricsCalculator.cratesForEggs(95, 30)).isEqualTo(3);
    }

    @Test
    void historicalEggSaleCannotExceedBalanceAtItsDate() {
        var existing = List.of(
                new EggLedgerEvent(LocalDate.of(2026, 1, 2), 60, 0),
                new EggLedgerEvent(LocalDate.of(2026, 1, 4), 20, 10)
        );

        assertThatThrownBy(() -> EggInventoryCalculator.validateHistoricalSale(
                existing, LocalDate.of(2026, 1, 3), 61
        )).isInstanceOf(IllegalArgumentException.class);

        EggInventoryCalculator.validateHistoricalSale(
                existing, LocalDate.of(2026, 1, 3), 60
        );
    }

    @Test
    void marginsUseRevenueAsDenominator() {
        assertThat(ProductionMetricsCalculator.marginPercent(100_000, 70_000))
                .isEqualByComparingTo("30");
        assertThat(PricingCalculator.marginPercent(100_000, 70_000))
                .isEqualByComparingTo("30");
        assertThat(PricingCalculator.priceForMargin(70_000, new BigDecimal("30")))
                .isEqualTo(100_000L);
    }
}
