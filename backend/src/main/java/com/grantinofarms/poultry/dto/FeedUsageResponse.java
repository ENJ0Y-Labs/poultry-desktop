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
        long feedCostMinor
) {}
