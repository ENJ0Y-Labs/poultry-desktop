package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;

public record FeedTypeResponse(
        String id,
        String name,
        String unit,
        String applicableType,
        String status,
        BigDecimal kgPerUnit,
        String brand,
        String packagingName,
        BigDecimal packageWeightKg
) {
    public FeedTypeResponse(String id, String name, String unit, String applicableType, String status, BigDecimal kgPerUnit) {
        this(id, name, unit, applicableType, status, kgPerUnit, null, null, null);
    }
}
