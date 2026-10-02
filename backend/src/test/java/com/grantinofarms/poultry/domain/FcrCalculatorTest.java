package com.grantinofarms.poultry.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FcrCalculatorTest {
    @Test
    void knownVectorProducesTwoPointZero() {
        assertThat(FcrCalculator.calculate(new BigDecimal("400"), new BigDecimal("200")))
                .isEqualByComparingTo("2.0");
    }

    @Test
    void zeroWeightGainIsUndefined() {
        assertThatThrownBy(() -> FcrCalculator.calculate(new BigDecimal("400"), BigDecimal.ZERO))
                .hasMessage("Feed consumed must be non-negative and live weight gain must be positive.");
    }
}
