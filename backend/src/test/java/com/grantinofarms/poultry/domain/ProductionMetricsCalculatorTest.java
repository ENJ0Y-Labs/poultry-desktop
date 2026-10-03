package com.grantinofarms.poultry.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class ProductionMetricsCalculatorTest {
    @Test void livabilityKnownVector() {
        assertThat(ProductionMetricsCalculator.livabilityPercent(5000, 103)).isEqualByComparingTo("97.940000");
    }

    @Test void hdpKnownVector() {
        assertThat(ProductionMetricsCalculator.hdpPercent(875, 1000)).isEqualByComparingTo("87.500000");
    }

    @Test void zeroDenominatorsAreUndefined() {
        assertThat(ProductionMetricsCalculator.livabilityPercent(0, 0)).isNull();
        assertThat(ProductionMetricsCalculator.hdpPercent(0, 0)).isNull();
        assertThat(ProductionMetricsCalculator.marginPercent(0, 0)).isNull();
    }

    @Test void crateConversionUsesWholeCrates() {
        assertThat(ProductionMetricsCalculator.cratesForEggs(95, 30)).isEqualTo(3);
    }

    @Test void marginUsesRevenueAsDenominator() {
        assertThat(ProductionMetricsCalculator.marginPercent(100_000, 70_000)).isEqualByComparingTo("30.000000");
    }
}
