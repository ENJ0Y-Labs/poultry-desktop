package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record BirdCostResponse(
        String batchId,
        LocalDate asOf,
        int currentBirds,
        Long initialPurchaseCostMinor,
        long additionalCostsMinor,
        long transferInCostMinor,
        Long soldBirdCostMinor,
        Long mortalityCostMinor,
        Long cullingCostMinor,
        Long transferOutCostMinor,
        Long carriedCostMinor
) {}
