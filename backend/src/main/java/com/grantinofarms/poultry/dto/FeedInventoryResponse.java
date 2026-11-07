package com.grantinofarms.poultry.dto;

import java.util.List;

public record FeedInventoryResponse(
        String feedTypeId,
        String feedTypeName,
        String unit,
        long purchasedQuantityMilli,
        long consumedQuantityMilli,
        long remainingQuantityMilli,
        long remainingCostMinor,
        long consumedCostMinor,
        List<String> applicableTypes
) {
    public FeedInventoryResponse {
        applicableTypes = applicableTypes == null ? List.of() : List.copyOf(applicableTypes);
    }
}
