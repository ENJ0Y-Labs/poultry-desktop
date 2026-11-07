package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record FeedUsageResponse(
        String id,
        String batchId,
        String feedTypeId,
        String feedTypeName,
        String unit,
        LocalDate usageDate,
        long quantityMilli,
        Long feedCostMinor,
        long knownFeedCostMinor
) {
    public FeedUsageResponse(String id, String batchId, String feedTypeId, String feedTypeName,
                             String unit, LocalDate usageDate, long quantityMilli, Long feedCostMinor) {
        this(id, batchId, feedTypeId, feedTypeName, unit, usageDate, quantityMilli,
                feedCostMinor, feedCostMinor == null ? 0L : feedCostMinor);
    }
}
