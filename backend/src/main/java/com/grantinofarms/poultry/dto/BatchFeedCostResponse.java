package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record BatchFeedCostResponse(
        String batchId,
        LocalDate asOf,
        int currentBirds,
        long consumedQuantityMilli,
        Long feedCostMinor,
        Long feedCostPerBirdMinor,
        long knownFeedCostMinor
) {
    public BatchFeedCostResponse(String batchId, LocalDate asOf, int currentBirds,
                                 long consumedQuantityMilli, Long feedCostMinor,
                                 Long feedCostPerBirdMinor) {
        this(batchId, asOf, currentBirds, consumedQuantityMilli, feedCostMinor,
                feedCostPerBirdMinor, feedCostMinor == null ? 0L : feedCostMinor);
    }
}
