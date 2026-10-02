package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record BatchFeedCostResponse(
        String batchId,
        LocalDate asOf,
        int currentBirds,
        long consumedQuantityMilli,
        long feedCostMinor,
        Long feedCostPerBirdMinor
) {}
