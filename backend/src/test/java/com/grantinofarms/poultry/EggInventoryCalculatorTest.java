package com.grantinofarms.poultry;

import com.grantinofarms.poultry.domain.EggInventoryCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EggInventoryCalculatorTest {
    @Test
    void goodRemainingExcludesCrackedEggs() {
        assertThat(EggInventoryCalculator.goodRemaining(100, 40)).isEqualTo(60);
    }

    @Test
    void totalCollectedIncludesGoodAndCracked() {
        assertThat(EggInventoryCalculator.totalCollected(100, 7)).isEqualTo(107);
    }
}
