package com.grantinofarms.poultry;

import com.grantinofarms.poultry.domain.PricingCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingCalculatorTest {
    @Test
    void calculatesActualMargin() {
        assertThat(PricingCalculator.marginPercent(100_000, 70_000))
                .isEqualByComparingTo("30.000000");
    }

    @Test
    void calculatesPriceForTargetMargin() {
        assertThat(PricingCalculator.priceForMargin(70_000, new BigDecimal("30")))
                .isEqualTo(100_000L);
    }

    @Test
    void knownCostAndMarginVectorProducesExpectedPrice() {
        assertThat(PricingCalculator.priceForMargin(80_000, new BigDecimal("20")))
                .isEqualTo(100_000L);
    }

    @Test
    void workingMarginOnlyMovesUp() {
        assertThat(PricingCalculator.raiseWorkingMargin(new BigDecimal("25"), new BigDecimal("30")))
                .isEqualByComparingTo("30");
        assertThat(PricingCalculator.raiseWorkingMargin(new BigDecimal("30"), new BigDecimal("20")))
                .isEqualByComparingTo("30");
    }

    @Test
    void rejectsHundredPercentMargin() {
        assertThatThrownBy(() -> PricingCalculator.priceForMargin(70_000, new BigDecimal("100")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
