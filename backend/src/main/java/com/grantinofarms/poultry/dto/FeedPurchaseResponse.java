package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record FeedPurchaseResponse(
        String id,
        String feedTypeId,
        String feedTypeName,
        String unit,
        LocalDate purchaseDate,
        long quantityMilli,
        long totalCostMinor,
        long remainingQuantityMilli,
        long remainingCostMinor
) {}
