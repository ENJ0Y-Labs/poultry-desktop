package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record BirdCostResponse(
        String batchId,
        LocalDate asOf,
        int currentBirds,
        long initialPurchaseCostMinor,
        long additionalCostsMinor,
        long transferInCostMinor,
        long soldBirdCostMinor,
        long mortalityCostMinor,
        long cullingCostMinor,
        long transferOutCostMinor,
        long carriedCostMinor
) {}
