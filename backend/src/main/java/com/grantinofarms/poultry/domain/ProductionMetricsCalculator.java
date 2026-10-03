package com.grantinofarms.poultry.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProductionMetricsCalculator {
    private ProductionMetricsCalculator() {}

    public static BigDecimal livabilityPercent(int initialBirds, int mortality) {
        requireNonNegative(initialBirds, "Initial birds");
        requireNonNegative(mortality, "Mortality");
        if (initialBirds == 0) return null;
        if (mortality > initialBirds) throw new IllegalArgumentException("Mortality cannot exceed initial birds.");
        return BigDecimal.valueOf(initialBirds - mortality).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(initialBirds), 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal hdpPercent(int eggs, int averageBirds) {
        requireNonNegative(eggs, "Eggs");
        requireNonNegative(averageBirds, "Average birds");
        if (averageBirds == 0) return null;
        return BigDecimal.valueOf(eggs).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(averageBirds), 6, RoundingMode.HALF_UP);
    }

    public static int cratesForEggs(int eggs, int crateSize) {
        requireNonNegative(eggs, "Eggs");
        if (crateSize <= 0) throw new IllegalArgumentException("Crate size must be positive.");
        return eggs / crateSize;
    }

    public static BigDecimal marginPercent(long revenueMinor, long costMinor) {
        if (revenueMinor < 0 || costMinor < 0) throw new IllegalArgumentException("Revenue and cost cannot be negative.");
        if (revenueMinor == 0) return null;
        return BigDecimal.valueOf(revenueMinor - costMinor).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(revenueMinor), 6, RoundingMode.HALF_UP);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " cannot be negative.");
    }
}
