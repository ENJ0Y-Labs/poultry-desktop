package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public record FeedTypeCreateRequest(
        @NotBlank String name,
        @NotBlank String unit,
        String applicableType,
        BigDecimal kgPerUnit,
        String brand,
        String packagingName,
        BigDecimal packageWeightKg,
        List<String> applicableTypes
) {
    public FeedTypeCreateRequest(String name, String unit, String applicableType, BigDecimal kgPerUnit,
                                 String brand, String packagingName, BigDecimal packageWeightKg) {
        this(name, unit, applicableType, kgPerUnit, brand, packagingName, packageWeightKg, null);
    }

    public FeedTypeCreateRequest(String name, String unit, String applicableType, BigDecimal kgPerUnit) {
        this(name, unit, applicableType, kgPerUnit, null, null, null, null);
    }

    public FeedTypeCreateRequest(String name, String unit, String applicableType) {
        this(name, unit, applicableType,
                unit != null && unit.trim().equalsIgnoreCase("kg") ? BigDecimal.ONE : null,
                null, null, null, null);
    }

    public FeedTypeCreateRequest(String name, String unit, List<String> applicableTypes) {
        this(name, unit, null,
                unit != null && unit.trim().equalsIgnoreCase("kg") ? BigDecimal.ONE : null,
                null, null, null, applicableTypes);
    }
}
