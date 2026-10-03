package com.grantinofarms.poultry.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PricingCalculator {
    private PricingCalculator() {}

    public static BigDecimal marginPercent(long salePriceMinor, long actualCostMinor) {
        if (salePriceMinor <= 0 || actualCostMinor < 0) {
            throw new IllegalArgumentException("Sale price must be positive and actual cost cannot be negative.");
        }
        return BigDecimal.valueOf(salePriceMinor - actualCostMinor)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(salePriceMinor), 6, RoundingMode.HALF_UP);
    }

    public static long priceForMargin(long actualCostMinor, BigDecimal marginPercent) {
        if (actualCostMinor < 0) {
            throw new IllegalArgumentException("Actual cost cannot be negative.");
        }
        if (marginPercent == null) {
            throw new IllegalArgumentException("Margin is required.");
        }
        if (marginPercent.signum() < 0 || marginPercent.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalArgumentException("Margin must be at least 0% and below 100%.");
        }

        BigDecimal divisor = BigDecimal.ONE.subtract(
                marginPercent.divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP)
        );
        return BigDecimal.valueOf(actualCostMinor)
                .divide(divisor, 0, RoundingMode.CEILING)
                .longValueExact();
    }

    public static boolean isBelowTarget(BigDecimal actualMargin, BigDecimal targetMargin) {
        return targetMargin != null
                && actualMargin.compareTo(targetMargin) < 0;
    }

    public static BigDecimal raiseWorkingMargin(BigDecimal currentWorking, BigDecimal actualMargin) {
        if (actualMargin == null) return currentWorking;
        if (currentWorking == null || actualMargin.compareTo(currentWorking) > 0) {
            return actualMargin;
        }
        return currentWorking;
    }
}
