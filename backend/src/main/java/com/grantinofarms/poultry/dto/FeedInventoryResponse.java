package com.grantinofarms.poultry.dto;

public record FeedInventoryResponse(
        String feedTypeId,
        String feedTypeName,
        String unit,
        long purchasedQuantityMilli,
        long consumedQuantityMilli,
        long remainingQuantityMilli,
        long remainingCostMinor,
        long consumedCostMinor
) {}
