package com.grantinofarms.poultry;

import com.grantinofarms.poultry.domain.BirdCostCalculator;
import com.grantinofarms.poultry.domain.BirdCostEvent;
import com.grantinofarms.poultry.domain.BirdPopulationEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirdCostCalculatorTest {

    @Test
    void carriesInitialCostAndAllocatesPartialSaleAtCurrentAverageCost() {
        List<BirdPopulationEvent> population = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "SOLD", 20)
        );

        long soldCost = BirdCostCalculator.costForReduction(
                100,
                1_000_000L,
                List.of(),
                List.of(),
                population.get(0)
        );

        var snapshot = BirdCostCalculator.calculate(
                100,
                1_000_000L,
                population,
                List.of(),
                LocalDate.of(2026, 1, 2)
        );

        assertThat(soldCost).isEqualTo(200_000L);
        assertThat(snapshot.currentBirds()).isEqualTo(80);
        assertThat(snapshot.carriedCostMinor()).isEqualTo(800_000L);
    }

    @Test
    void additionalCostRaisesCarriedCostBeforeLaterSale() {
        List<BirdPopulationEvent> population = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 5), "SOLD", 20)
        );
        List<BirdCostEvent> costs = List.of(
                new BirdCostEvent(LocalDate.of(2026, 1, 4), "ADDITIONAL_COST", 200_000L)
        );

        long soldCost = BirdCostCalculator.costForReduction(
                100,
                1_000_000L,
                List.of(),
                costs,
                population.get(0)
        );

        assertThat(soldCost).isEqualTo(240_000L);
    }


    @Test
    void allocatesCostToMortalityAndCullingToo() {
        List<BirdPopulationEvent> population = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "MORTALITY", 10),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 3), "CULLING", 10)
        );

        var snapshot = BirdCostCalculator.calculate(
                100,
                1_000_000L,
                population,
                List.of(),
                LocalDate.of(2026, 1, 3)
        );

        assertThat(snapshot.allocatedCostMinor()).isEqualTo(200_000L);
        assertThat(snapshot.carriedCostMinor()).isEqualTo(800_000L);
        assertThat(snapshot.currentBirds()).isEqualTo(80);
    }

    @Test
    void exactIntegerAllocationKeepsEveryKoboAccountedFor() {
        List<BirdPopulationEvent> population = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "SOLD", 1),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 3), "SOLD", 2)
        );

        var snapshot = BirdCostCalculator.calculate(
                3,
                100L,
                population,
                List.of(),
                LocalDate.of(2026, 1, 3)
        );

        assertThat(snapshot.allocatedCostMinor()).isEqualTo(100L);
        assertThat(snapshot.carriedCostMinor()).isEqualTo(0L);
    }

    @Test
    void largeNonDivisiblePurchaseTotalReconcilesWithoutOverflow() {
        long purchaseCost = Long.MAX_VALUE;
        List<BirdPopulationEvent> population = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "SOLD", 1),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 3), "SOLD", 2)
        );

        var snapshot = BirdCostCalculator.calculate(
                3,
                purchaseCost,
                population,
                List.of(),
                LocalDate.of(2026, 1, 3)
        );

        assertThat(snapshot.currentBirds()).isZero();
        assertThat(snapshot.allocatedCostMinor()).isEqualTo(purchaseCost);
        assertThat(snapshot.carriedCostMinor()).isZero();
    }

    @Test
    void rejectsInvalidCostAllocationInputs() {
        assertThatThrownBy(() -> BirdCostCalculator.allocateCost(-1L, 3, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BirdCostCalculator.allocateCost(100L, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BirdCostCalculator.allocateCost(100L, 3, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transferInCostBecomesPartOfTargetCarriedCost() {
        List<BirdPopulationEvent> population = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "TRANSFER_IN", 20)
        );
        List<BirdCostEvent> costs = List.of(
                new BirdCostEvent(LocalDate.of(2026, 1, 2), "TRANSFER_IN_COST", 200_000L)
        );

        var snapshot = BirdCostCalculator.calculate(
                80,
                800_000L,
                population,
                costs,
                LocalDate.of(2026, 1, 2)
        );

        assertThat(snapshot.currentBirds()).isEqualTo(100);
        assertThat(snapshot.carriedCostMinor()).isEqualTo(1_000_000L);
    }
}
