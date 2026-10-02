package com.grantinofarms.poultry.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EggInventoryCalculator {
    private EggInventoryCalculator() {}

    public static int goodRemaining(int goodCollected, int goodSold) {
        if (goodCollected < 0 || goodSold < 0) {
            throw new IllegalArgumentException("Egg quantities cannot be negative.");
        }
        if (goodSold > goodCollected) {
            throw new IllegalArgumentException("Egg sales cannot exceed good eggs collected.");
        }
        return goodCollected - goodSold;
    }

    public static int totalCollected(int goodCollected, int crackedCollected) {
        if (goodCollected < 0 || crackedCollected < 0) {
            throw new IllegalArgumentException("Egg quantities cannot be negative.");
        }
        return goodCollected + crackedCollected;
    }

    public static void validateHistoricalSale(List<EggLedgerEvent> existingEvents,
                                               LocalDate proposedDate,
                                               int proposedSoldEggs) {
        if (proposedSoldEggs <= 0) {
            throw new IllegalArgumentException("Egg sale quantity must be greater than zero.");
        }

        List<EggLedgerEvent> events = new ArrayList<>(existingEvents);
        events.add(new EggLedgerEvent(proposedDate, 0, proposedSoldEggs));
        events.sort(Comparator.comparing(EggLedgerEvent::date));

        int balance = 0;
        for (EggLedgerEvent event : events) {
            balance += event.goodCollected();
            balance -= event.goodSold();
            if (balance < 0) {
                throw new IllegalArgumentException(
                        "The sale exceeds good eggs available on that date.");
            }
        }
    }
}
