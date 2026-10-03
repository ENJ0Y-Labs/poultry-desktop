package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;

public record InventoryItemResponse(
        String id,
        String farmId,
        String name,
        String category,
        String unit,
        BigDecimal reorderLevel,
        String status,
        BigDecimal quantityOnHand
) {}
