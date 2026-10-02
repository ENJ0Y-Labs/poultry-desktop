package com.grantinofarms.poultry.domain;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class BirdCostCalculator {
    private BirdCostCalculator() {}

    public static CostSnapshot calculate(
            int initialBirds,
            long initialCostMinor,
            List<BirdPopulationEvent> populationEvents,
            List<BirdCostEvent> costEvents,
            LocalDate asOf
    ) {
        validateInputs(initialBirds, initialCostMinor, asOf);

        int population = initialBirds;
        long carriedCost = initialCostMinor;
        long allocatedCost = 0L;

        for (LocalDate date : orderedDates(populationEvents, costEvents, asOf)) {
            for (BirdCostEvent costEvent : costEvents) {
                if (costEvent.eventDate().equals(date)) {
                    carriedCost = addExact(carriedCost, costEvent.amountMinor());
                }
            }

            for (BirdPopulationEvent event : populationEvents) {
                if (!event.eventDate().equals(date)) {
                    continue;
                }

                if (event.quantity() <= 0) {
                    throw new IllegalArgumentException("Population event quantity must be positive.");
                }

                int signed = BirdPopulationCalculator.signedQuantity(event);
                if (signed < 0) {
                    if (event.quantity() > population) {
                        throw new IllegalArgumentException("Bird population cannot become negative.");
                    }

                    long removedCost = allocateCost(carriedCost, population, event.quantity());
                    carriedCost = Math.subtractExact(carriedCost, removedCost);
                    allocatedCost = addExact(allocatedCost, removedCost);
                    population -= event.quantity();
                } else {
                    population = Math.addExact(population, event.quantity());
                }
            }
        }

        return new CostSnapshot(population, carriedCost, allocatedCost);
    }

    public static long costForReduction(
            int initialBirds,
            long initialCostMinor,
            List<BirdPopulationEvent> existingPopulationEvents,
            List<BirdCostEvent> costEvents,
            BirdPopulationEvent proposedReduction
    ) {
        validateInputs(initialBirds, initialCostMinor, proposedReduction.eventDate());

        if (BirdPopulationCalculator.signedQuantity(proposedReduction) >= 0) {
            throw new IllegalArgumentException("Cost can only be allocated to a reducing population event.");
        }

        int population = initialBirds;
        long carriedCost = initialCostMinor;

        for (LocalDate date : orderedDates(
                existingPopulationEvents,
                costEvents,
                proposedReduction.eventDate(),
                proposedReduction.eventDate()
        )) {
            for (BirdCostEvent costEvent : costEvents) {
                if (costEvent.eventDate().equals(date)) {
                    carriedCost = addExact(carriedCost, costEvent.amountMinor());
                }
            }

            for (BirdPopulationEvent event : existingPopulationEvents) {
                if (!event.eventDate().equals(date)) {
                    continue;
                }
                if (event.quantity() <= 0) {
                    throw new IllegalArgumentException("Population event quantity must be positive.");
                }

                int signed = BirdPopulationCalculator.signedQuantity(event);
                if (signed < 0) {
                    if (event.quantity() > population) {
                        throw new IllegalArgumentException("Bird population cannot become negative.");
                    }
                    long removedCost = allocateCost(carriedCost, population, event.quantity());
                    carriedCost = Math.subtractExact(carriedCost, removedCost);
                    population -= event.quantity();
                } else {
                    population = Math.addExact(population, event.quantity());
                }
            }

            if (date.equals(proposedReduction.eventDate())) {
                if (proposedReduction.quantity() > population) {
                    throw new IllegalArgumentException("Bird population cannot become negative.");
                }
                return allocateCost(carriedCost, population, proposedReduction.quantity());
            }
        }

        throw new IllegalStateException("Unable to evaluate the proposed population reduction.");
    }

    public static long allocateCost(long carriedCostMinor, int availableBirds, int quantity) {
        if (carriedCostMinor < 0) {
            throw new IllegalArgumentException("Carried bird cost cannot be negative.");
        }
        if (availableBirds <= 0 || quantity <= 0 || quantity > availableBirds) {
            throw new IllegalArgumentException("Invalid cost allocation quantity.");
        }
        if (quantity == availableBirds) {
            return carriedCostMinor;
        }

        return BigInteger.valueOf(carriedCostMinor)
                .multiply(BigInteger.valueOf(quantity))
                .divide(BigInteger.valueOf(availableBirds))
                .longValueExact();
    }

    private static List<LocalDate> orderedDates(
            List<BirdPopulationEvent> populationEvents,
            List<BirdCostEvent> costEvents,
            LocalDate asOf,
            LocalDate requiredDate
    ) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(
                                populationEvents.stream().map(BirdPopulationEvent::eventDate),
                                costEvents.stream().map(BirdCostEvent::eventDate)
                        ),
                        java.util.stream.Stream.of(requiredDate)
                )
                .filter(date -> !date.isAfter(asOf))
                .distinct()
                .sorted()
                .toList();
    }

    private static void validateInputs(int initialBirds, long initialCostMinor, LocalDate asOf) {
        if (initialBirds <= 0) {
            throw new IllegalArgumentException("Initial bird count must be positive.");
        }
        if (initialCostMinor < 0) {
            throw new IllegalArgumentException("Initial bird cost cannot be negative.");
        }
        if (asOf == null) {
            throw new IllegalArgumentException("asOf date is required.");
        }
    }

    private static long addExact(long left, long right) {
        return Math.addExact(left, right);
    }

    public record CostSnapshot(
            int currentBirds,
            long carriedCostMinor,
            long allocatedCostMinor
    ) {}
}
