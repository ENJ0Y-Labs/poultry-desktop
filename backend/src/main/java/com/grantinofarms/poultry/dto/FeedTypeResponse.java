package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.util.List;

public record FeedTypeResponse(
        String id,
        String name,
        String unit,
        String applicableType,
        String status,
        BigDecimal kgPerUnit,
        String brand,
        String packagingName,
        BigDecimal packageWeightKg,
        List<String> applicableTypes
) {
    public FeedTypeResponse {
        applicableTypes = applicableTypes == null ? List.of() : List.copyOf(applicableTypes);
    }

    public FeedTypeResponse(String id, String name, String unit, String applicableType, String status,
                            BigDecimal kgPerUnit, String brand, String packagingName, BigDecimal packageWeightKg) {
        this(id, name, unit, applicableType, status, kgPerUnit, brand, packagingName, packageWeightKg,
                legacyTypes(applicableType));
    }

    public FeedTypeResponse(String id, String name, String unit, String applicableType, String status, BigDecimal kgPerUnit) {
        this(id, name, unit, applicableType, status, kgPerUnit, null, null, null, legacyTypes(applicableType));
    }

    private static List<String> legacyTypes(String applicableType) {
        if ("BOTH".equals(applicableType)) return List.of("LAYER", "BROILER", "TURKEY");
        if (applicableType == null || applicableType.isBlank()) return List.of();
        return List.of(applicableType);
    }
}
