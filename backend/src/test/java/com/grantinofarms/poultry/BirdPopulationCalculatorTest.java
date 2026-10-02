package com.grantinofarms.poultry;

import com.grantinofarms.poultry.domain.BirdPopulationCalculator;
import com.grantinofarms.poultry.domain.BirdPopulationEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirdPopulationCalculatorTest {

    @Test
    void calculatesCurrentBirdsFromPopulationEvents() {
        List<BirdPopulationEvent> events = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "MORTALITY", 83),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 3), "CULLING", 20),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 4), "SOLD", 70),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 5), "TRANSFER_IN", 25),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 6), "TRANSFER_OUT", 10)
        );

        assertThat(BirdPopulationCalculator.currentBirds(
                5000, events, LocalDate.of(2026, 1, 6)
        )).isEqualTo(4842);
    }

    @Test
    void asOfIgnoresEventsAfterHistoricalDate() {
        List<BirdPopulationEvent> events = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "MORTALITY", 83),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 10), "SOLD", 500)
        );

        assertThat(BirdPopulationCalculator.currentBirds(
                5000, events, LocalDate.of(2026, 1, 5)
        )).isEqualTo(4917);
    }

    @Test
    void rejectsMortalityGreaterThanAvailableBirds() {
        List<BirdPopulationEvent> events = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "MORTALITY", 80)
        );

        assertThatThrownBy(() -> BirdPopulationCalculator.validatePopulationChange(
                100,
                events,
                new BirdPopulationEvent(LocalDate.of(2026, 1, 3), "MORTALITY", 21)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mortality quantity exceeds available birds.");
    }

    @Test
    void rejectsBackdatedCullingThatWouldMakeHistoricalPopulationNegative() {
        List<BirdPopulationEvent> events = List.of(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 5), "MORTALITY", 60)
        );

        assertThatThrownBy(() -> BirdPopulationCalculator.validatePopulationChange(
                100,
                events,
                new BirdPopulationEvent(LocalDate.of(2026, 1, 4), "CULLING", 50)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("culling quantity exceeds available birds.");
    }

    @Test
    void rejectsSaleGreaterThanAvailableBirds() {
        assertThatThrownBy(() -> BirdPopulationCalculator.validatePopulationChange(
                100,
                List.of(),
                new BirdPopulationEvent(LocalDate.of(2026, 1, 2), "SOLD", 101)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sold quantity exceeds available birds.");
    }

    @Test
    void rejectsUnknownEventTypes() {
        assertThatThrownBy(() -> BirdPopulationCalculator.signedQuantity(
                new BirdPopulationEvent(LocalDate.of(2026, 1, 1), "UNKNOWN", 1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown population event type: UNKNOWN");
    }
}
