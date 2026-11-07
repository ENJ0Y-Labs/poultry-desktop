package com.grantinofarms.poultry.dto;

import java.util.List;

public record FeedInventoryResponse(
        String feedTypeId,
        String feedTypeName,
        String unit,
        long purchasedQuantityMilli,
        long openingQuantityMilli,
        long consumedQuantityMilli,
        long remainingQuantityMilli,
        Long remainingCostMinor,
        Long consumedCostMinor,
        List<String> applicableTypes,
        String brand,
        String packagingName,
        java.math.BigDecimal packageWeightKg
) {
    public FeedInventoryResponse {
        applicableTypes = applicableTypes == null ? List.of() : List.copyOf(applicableTypes);
    }

    public FeedInventoryResponse(String feedTypeId, String feedTypeName, String unit,
                                 long purchasedQuantityMilli, long openingQuantityMilli,
                                 long consumedQuantityMilli, long remainingQuantityMilli,
                                 Long remainingCostMinor, Long consumedCostMinor, List<String> applicableTypes) {
        this(feedTypeId, feedTypeName, unit, purchasedQuantityMilli, openingQuantityMilli,
                consumedQuantityMilli, remainingQuantityMilli, remainingCostMinor, consumedCostMinor,
                applicableTypes, null, null, null);
    }

    public FeedInventoryResponse(String feedTypeId, String feedTypeName, String unit,
                                 long purchasedQuantityMilli, long consumedQuantityMilli,
                                 long remainingQuantityMilli, Long remainingCostMinor,
                                 Long consumedCostMinor, List<String> applicableTypes) {
        this(feedTypeId, feedTypeName, unit, purchasedQuantityMilli, 0L, consumedQuantityMilli,
                remainingQuantityMilli, remainingCostMinor, consumedCostMinor, applicableTypes,
                null, null, null);
    }

    public FeedInventoryResponse(String feedTypeId, String feedTypeName, String unit,
                                 long purchasedQuantityMilli, long consumedQuantityMilli,
                                 long remainingQuantityMilli, Long remainingCostMinor,
                                 Long consumedCostMinor) {
        this(feedTypeId, feedTypeName, unit, purchasedQuantityMilli, 0L, consumedQuantityMilli,
                remainingQuantityMilli, remainingCostMinor, consumedCostMinor, List.of(),
                null, null, null);
    }
}
