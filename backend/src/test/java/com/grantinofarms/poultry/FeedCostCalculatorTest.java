package com.grantinofarms.poultry;

import com.grantinofarms.poultry.domain.FeedCostCalculator;
import com.grantinofarms.poultry.domain.FeedPurchaseLot;
import com.grantinofarms.poultry.domain.FeedUsageEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedCostCalculatorTest {
    private static final Instant T1 = Instant.parse("2026-01-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T08:00:00Z");

    @Test
    void onePurchasePartialUsagePreservesRemainingCost() {
        var purchase = new FeedPurchaseLot("p1", "f1", LocalDate.of(2026,1,1), 100_000, 1_000_000, T1);
        var usage = new FeedUsageEvent("u1", "b1", "f1", LocalDate.of(2026,1,2), 25_000, T2);

        var result = FeedCostCalculator.calculate(List.of(purchase), List.of(usage), LocalDate.of(2026,1,2));

        assertThat(result.consumedQuantityMilli()).isEqualTo(25_000);
        assertThat(result.consumedCostMinor()).isEqualTo(250_000);
        assertThat(result.remainingQuantityMilli()).isEqualTo(75_000);
        assertThat(result.remainingCostMinor()).isEqualTo(750_000);
    }

    @Test
    void multiplePurchasesUseOldestLotFirst() {
        var first = new FeedPurchaseLot("p1", "f1", LocalDate.of(2026,1,1), 20_000, 160_000, T1);
        var second = new FeedPurchaseLot("p2", "f1", LocalDate.of(2026,1,2), 30_000, 300_000, T2);
        var usage = new FeedUsageEvent("u1", "b1", "f1", LocalDate.of(2026,1,3), 25_000, T2);

        var result = FeedCostCalculator.calculate(List.of(first, second), List.of(usage), LocalDate.of(2026,1,3));

        assertThat(result.consumedCostMinor()).isEqualTo(210_000);
        assertThat(result.remainingQuantityMilli()).isEqualTo(25_000);
        assertThat(result.remainingCostMinor()).isEqualTo(250_000);
    }

    @Test
    void exhaustedLotsAreFullyAllocatedWithoutLosingMinorUnits() {
        var first = new FeedPurchaseLot("p1", "f1", LocalDate.of(2026,1,1), 3_000, 100, T1);
        var usage1 = new FeedUsageEvent("u1", "b1", "f1", LocalDate.of(2026,1,2), 1_000, T2);
        var usage2 = new FeedUsageEvent("u2", "b1", "f1", LocalDate.of(2026,1,3), 2_000, T2);

        var result = FeedCostCalculator.calculate(List.of(first), List.of(usage1, usage2), LocalDate.of(2026,1,3));

        assertThat(result.consumedCostMinor()).isEqualTo(100);
        assertThat(result.remainingQuantityMilli()).isZero();
        assertThat(result.remainingCostMinor()).isZero();
    }

    @Test
    void insufficientInventoryIsRejected() {
        var purchase = new FeedPurchaseLot("p1", "f1", LocalDate.of(2026,1,1), 10_000, 100_000, T1);
        var usage = new FeedUsageEvent("u1", "b1", "f1", LocalDate.of(2026,1,2), 10_001, T2);

        assertThatThrownBy(() -> FeedCostCalculator.calculate(
                List.of(purchase), List.of(usage), LocalDate.of(2026,1,2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient feed inventory for FIFO consumption.");
    }
}
