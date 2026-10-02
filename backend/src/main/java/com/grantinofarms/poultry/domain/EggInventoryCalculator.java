package com.grantinofarms.poultry.domain;

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
}
