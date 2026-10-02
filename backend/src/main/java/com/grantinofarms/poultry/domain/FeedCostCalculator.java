package com.grantinofarms.poultry.domain;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FeedCostCalculator {
    private FeedCostCalculator() {}

    public static FifoSnapshot calculate(
            List<FeedPurchaseLot> purchases,
            List<FeedUsageEvent> usages,
            LocalDate asOf
    ) {
        if (asOf == null) throw new IllegalArgumentException("asOf date is required.");

        List<FeedPurchaseLot> lots = purchases.stream()
                .filter(p -> !p.purchaseDate().isAfter(asOf))
                .sorted(Comparator.comparing(FeedPurchaseLot::purchaseDate)
                        .thenComparing(FeedPurchaseLot::createdAt))
                .toList();

        List<FeedUsageEvent> orderedUsages = usages.stream()
                .filter(u -> !u.usageDate().isAfter(asOf))
                .sorted(Comparator.comparing(FeedUsageEvent::usageDate)
                        .thenComparing(FeedUsageEvent::createdAt))
                .toList();

        List<LotState> states = new ArrayList<>();
        for (FeedPurchaseLot lot : lots) {
            if (lot.quantityMilli() <= 0 || lot.totalCostMinor() < 0) {
                throw new IllegalArgumentException("Feed purchase quantity and cost are invalid.");
            }
            states.add(new LotState(lot));
        }

        Map<String, Long> usageCosts = new LinkedHashMap<>();
        long consumedQuantity = 0L;
        long consumedCost = 0L;

        for (FeedUsageEvent usage : orderedUsages) {
            if (usage.quantityMilli() <= 0) {
                throw new IllegalArgumentException("Feed usage quantity must be positive.");
            }

            long remaining = usage.quantityMilli();
            long usageCost = 0L;

            for (LotState lot : states) {
                if (!lot.hasQuantity() || remaining == 0) continue;

                long take = Math.min(remaining, lot.quantityRemaining);
                long cost = allocateLotCost(lot.costRemaining, lot.quantityRemaining, take);

                lot.quantityRemaining = Math.subtractExact(lot.quantityRemaining, take);
                lot.costRemaining = Math.subtractExact(lot.costRemaining, cost);

                remaining = Math.subtractExact(remaining, take);
                usageCost = Math.addExact(usageCost, cost);
            }

            if (remaining > 0) {
                throw new IllegalArgumentException("Insufficient feed inventory for FIFO consumption.");
            }

            consumedQuantity = Math.addExact(consumedQuantity, usage.quantityMilli());
            consumedCost = Math.addExact(consumedCost, usageCost);
            usageCosts.put(usage.id(), usageCost);
        }

        long remainingQuantity = 0L;
        long remainingCost = 0L;
        for (LotState lot : states) {
            remainingQuantity = Math.addExact(remainingQuantity, lot.quantityRemaining);
            remainingCost = Math.addExact(remainingCost, lot.costRemaining);
        }

        return new FifoSnapshot(consumedQuantity, consumedCost, remainingQuantity, remainingCost, usageCosts);
    }

    public static long costForUsage(
            List<FeedPurchaseLot> purchases,
            List<FeedUsageEvent> existingUsages,
            FeedUsageEvent proposedUsage
    ) {
        if (proposedUsage == null) throw new IllegalArgumentException("Proposed feed usage is required.");
        FifoSnapshot before = calculate(purchases, existingUsages, proposedUsage.usageDate());
        List<FeedUsageEvent> withProposed = new ArrayList<>(existingUsages);
        withProposed.add(proposedUsage);
        FifoSnapshot after = calculate(purchases, withProposed, proposedUsage.usageDate());
        return Math.subtractExact(after.consumedCostMinor(), before.consumedCostMinor());
    }

    private static long allocateLotCost(long costRemaining, long quantityRemaining, long quantityTaken) {
        if (quantityTaken == quantityRemaining) return costRemaining;
        return BigInteger.valueOf(costRemaining)
                .multiply(BigInteger.valueOf(quantityTaken))
                .divide(BigInteger.valueOf(quantityRemaining))
                .longValueExact();
    }

    private static final class LotState {
        private long quantityRemaining;
        private long costRemaining;

        private LotState(FeedPurchaseLot lot) {
            this.quantityRemaining = lot.quantityMilli();
            this.costRemaining = lot.totalCostMinor();
        }

        private boolean hasQuantity() {
            return quantityRemaining > 0;
        }
    }

    public record FifoSnapshot(
            long consumedQuantityMilli,
            long consumedCostMinor,
            long remainingQuantityMilli,
            long remainingCostMinor,
            Map<String, Long> usageCostsMinor
    ) {}
}
