package com.grantinofarms.poultry.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FcrCalculator {
    private FcrCalculator() {}

    public static BigDecimal calculate(BigDecimal feedConsumedKg, BigDecimal liveWeightGainKg) {
        if (feedConsumedKg == null || liveWeightGainKg == null) {
            return null;
        }
        if (feedConsumedKg.signum() < 0 || liveWeightGainKg.signum() < 0) {
            throw new IllegalArgumentException("Feed consumed must be non-negative and live weight gain must be positive.");
        }
        if (liveWeightGainKg.signum() == 0) return null;
        return feedConsumedKg.divide(liveWeightGainKg, 6, RoundingMode.HALF_UP);
    }
}
