package com.grantinofarms.poultry.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BirdPopulationCalculator {
    private BirdPopulationCalculator() {}

    public static int currentBirds(int initialBirds, List<BirdPopulationEvent> events, LocalDate asOf) {
        if (initialBirds <= 0) {
            throw new IllegalArgumentException("Initial bird count must be positive.");
        }
        if (asOf == null) {
            throw new IllegalArgumentException("asOf date is required.");
        }

        int population = initialBirds;
        for (BirdPopulationEvent event : ordered(events)) {
            if (event.eventDate().isAfter(asOf)) {
                continue;
            }
            population += signedQuantity(event);
            if (population < 0) {
                throw new IllegalArgumentException("Bird population cannot become negative.");
            }
        }
        return population;
    }

    public static int availableBirds(int initialBirds, List<BirdPopulationEvent> events, LocalDate eventDate) {
        return currentBirds(initialBirds, events, eventDate.minusDays(1));
    }

    public static int signedQuantity(BirdPopulationEvent event) {
        return switch (event.eventType()) {
            case "MORTALITY", "CULLING", "SOLD", "TRANSFER_OUT" -> -event.quantity();
            case "TRANSFER_IN" -> event.quantity();
            default -> throw new IllegalArgumentException("Unknown population event type: " + event.eventType());
        };
    }

    public static void validatePopulationChange(
            int initialBirds,
            List<BirdPopulationEvent> existingEvents,
            BirdPopulationEvent proposedEvent
    ) {
        if (proposedEvent.quantity() <= 0) {
            throw new IllegalArgumentException("Population event quantity must be positive.");
        }

        int available = availableBirds(initialBirds, existingEvents, proposedEvent.eventDate());
        int signed = signedQuantity(proposedEvent);

        if (signed < 0 && proposedEvent.quantity() > available) {
            throw new IllegalArgumentException(
                    proposedEvent.eventType().toLowerCase() + " quantity exceeds available birds."
            );
        }

        List<BirdPopulationEvent> withProposed = new ArrayList<>(existingEvents);
        withProposed.add(proposedEvent);

        List<BirdPopulationEvent> ordered = ordered(withProposed);
        int population = initialBirds;
        for (BirdPopulationEvent event : ordered) {
            population += signedQuantity(event);
            if (population < 0) {
                throw new IllegalArgumentException("Bird population cannot become negative.");
            }
        }
    }

    private static List<BirdPopulationEvent> ordered(List<BirdPopulationEvent> events) {
        return events.stream()
                .sorted(Comparator.comparing(BirdPopulationEvent::eventDate))
                .toList();
    }
}
