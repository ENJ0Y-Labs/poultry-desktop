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
        return calculate(purchases, List.of(), usages, asOf);
    }

    public static FifoSnapshot calculate(
            List<FeedPurchaseLot> purchases,
            List<FeedOpeningStockLot> openingBalances,
            List<FeedUsageEvent> usages,
            LocalDate asOf
    ) {
        if (asOf == null) throw new IllegalArgumentException("asOf date is required.");

        List<LotState> states = new ArrayList<>();
        for (FeedPurchaseLot lot : purchases) {
            if (!lot.purchaseDate().isAfter(asOf)) {
                if (lot.quantityMilli() <= 0 || lot.totalCostMinor() < 0) {
                    throw new IllegalArgumentException("Feed purchase quantity and cost are invalid.");
                }
                states.add(new LotState(lot.id(), lot.purchaseDate(), lot.createdAt(),
                        lot.quantityMilli(), lot.totalCostMinor()));
            }
        }
        for (FeedOpeningStockLot opening : openingBalances) {
            if (!opening.effectiveDate().isAfter(asOf)) {
                if (opening.quantityMilli() <= 0) {
                    throw new IllegalArgumentException("Opening feed quantity must be positive.");
                }
                states.add(new LotState(opening.id(), opening.effectiveDate(), opening.createdAt(),
                        opening.quantityMilli(), null));
            }
        }
        states.sort(Comparator.comparing((LotState lot) -> lot.date)
                .thenComparing(lot -> lot.createdAt)
                .thenComparing(lot -> lot.id));

        List<FeedUsageEvent> orderedUsages = usages.stream()
                .filter(u -> !u.usageDate().isAfter(asOf))
                .sorted(Comparator.comparing(FeedUsageEvent::usageDate)
                        .thenComparing(FeedUsageEvent::createdAt))
                .toList();

        Map<String, Long> usageCosts = new LinkedHashMap<>();
        Map<String, Long> usageKnownCosts = new LinkedHashMap<>();
        long consumedQuantity = 0L;
        long knownConsumedCost = 0L;
        boolean consumedCostUnknown = false;

        for (FeedUsageEvent usage : orderedUsages) {
            if (usage.quantityMilli() <= 0) {
                throw new IllegalArgumentException("Feed usage quantity must be positive.");
            }

            long remaining = usage.quantityMilli();
            long knownUsageCost = 0L;
            boolean usageCostUnknown = false;

            for (LotState lot : states) {
                if (!lot.hasQuantity() || remaining == 0 || !lot.availableAt(usage)) continue;

                long take = Math.min(remaining, lot.quantityRemaining);
                if (lot.costRemaining == null) {
                    usageCostUnknown = true;
                } else {
                    long cost = allocateLotCost(lot.costRemaining, lot.quantityRemaining, take);
                    lot.costRemaining = Math.subtractExact(lot.costRemaining, cost);
                    knownUsageCost = Math.addExact(knownUsageCost, cost);
                }

                lot.quantityRemaining = Math.subtractExact(lot.quantityRemaining, take);
                remaining = Math.subtractExact(remaining, take);
            }

            if (remaining > 0) {
                throw new IllegalArgumentException("Insufficient feed inventory for FIFO consumption.");
            }

            consumedQuantity = Math.addExact(consumedQuantity, usage.quantityMilli());
            knownConsumedCost = Math.addExact(knownConsumedCost, knownUsageCost);
            usageKnownCosts.put(usage.id(), knownUsageCost);
            if (usageCostUnknown) {
                consumedCostUnknown = true;
                usageCosts.put(usage.id(), null);
            } else {
                usageCosts.put(usage.id(), knownUsageCost);
            }
        }

        long remainingQuantity = 0L;
        long knownRemainingCost = 0L;
        boolean remainingCostUnknown = false;
        for (LotState lot : states) {
            remainingQuantity = Math.addExact(remainingQuantity, lot.quantityRemaining);
            if (lot.quantityRemaining > 0) {
                if (lot.costRemaining == null) remainingCostUnknown = true;
                else knownRemainingCost = Math.addExact(knownRemainingCost, lot.costRemaining);
            }
        }

        return new FifoSnapshot(consumedQuantity,
                consumedCostUnknown ? null : knownConsumedCost,
                remainingQuantity,
                remainingCostUnknown ? null : knownRemainingCost,
                knownConsumedCost,
                knownRemainingCost,
                java.util.Collections.unmodifiableMap(usageCosts),
                java.util.Collections.unmodifiableMap(usageKnownCosts));
    }

    public static long costForUsage(
            List<FeedPurchaseLot> purchases,
            List<FeedUsageEvent> existingUsages,
            FeedUsageEvent proposedUsage
    ) {
        Long cost = costForUsage(purchases, List.of(), existingUsages, proposedUsage);
        if (cost == null) {
            throw new IllegalStateException("Opening stock costing requires the opening-balance FIFO overload.");
        }
        return cost;
    }

    public static Long costForUsage(
            List<FeedPurchaseLot> purchases,
            List<FeedOpeningStockLot> openingBalances,
            List<FeedUsageEvent> existingUsages,
            FeedUsageEvent proposedUsage
    ) {
        return costBreakdownForUsage(purchases, openingBalances, existingUsages, proposedUsage).totalCostMinor();
    }

    public static UsageCost costBreakdownForUsage(
            List<FeedPurchaseLot> purchases,
            List<FeedOpeningStockLot> openingBalances,
            List<FeedUsageEvent> existingUsages,
            FeedUsageEvent proposedUsage
    ) {
        if (proposedUsage == null) throw new IllegalArgumentException("Proposed feed usage is required.");
        List<FeedUsageEvent> withProposed = new ArrayList<>(existingUsages);
        withProposed.add(proposedUsage);
        FifoSnapshot after = calculate(purchases, openingBalances, withProposed, proposedUsage.usageDate());
        if (!after.usageCostsMinor().containsKey(proposedUsage.id())) {
            throw new IllegalStateException("Proposed feed usage was not included in FIFO calculation.");
        }
        return new UsageCost(after.usageCostsMinor().get(proposedUsage.id()),
                after.usageKnownCostsMinor().getOrDefault(proposedUsage.id(), 0L));
    }

    public record UsageCost(Long totalCostMinor, long knownCostMinor) {}

    private static long allocateLotCost(long costRemaining, long quantityRemaining, long quantityTaken) {
        if (quantityTaken == quantityRemaining) return costRemaining;
        return BigInteger.valueOf(costRemaining)
                .multiply(BigInteger.valueOf(quantityTaken))
                .divide(BigInteger.valueOf(quantityRemaining))
                .longValueExact();
    }

    private static final class LotState {
        private final String id;
        private final LocalDate date;
        private final java.time.Instant createdAt;
        private long quantityRemaining;
        private Long costRemaining;

        private LotState(String id, LocalDate date, java.time.Instant createdAt, long quantity, Long cost) {
            this.id = id;
            this.date = date;
            this.createdAt = createdAt;
            this.quantityRemaining = quantity;
            this.costRemaining = cost;
        }

        private boolean hasQuantity() {
            return quantityRemaining > 0;
        }

        private boolean availableAt(FeedUsageEvent usage) {
            if (date.isBefore(usage.usageDate())) return true;
            if (date.isAfter(usage.usageDate())) return false;
            return !createdAt.isAfter(usage.createdAt());
        }
    }

    public record FifoSnapshot(
            long consumedQuantityMilli,
            Long consumedCostMinor,
            long remainingQuantityMilli,
            Long remainingCostMinor,
            long knownConsumedCostMinor,
            long knownRemainingCostMinor,
            Map<String, Long> usageCostsMinor,
            Map<String, Long> usageKnownCostsMinor
    ) {}
}
