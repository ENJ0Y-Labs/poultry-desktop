package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record FeedTypeCreateRequest(
        @NotBlank String name,
        @NotBlank String unit,
        @NotBlank String applicableType,
        BigDecimal kgPerUnit,
        String brand,
        String packagingName,
        BigDecimal packageWeightKg
) {
    public FeedTypeCreateRequest(String name, String unit, String applicableType, BigDecimal kgPerUnit) {
        this(name, unit, applicableType, kgPerUnit, null, null, null);
    }
    public FeedTypeCreateRequest(String name, String unit, String applicableType) {
        this(name, unit, applicableType, unit != null && unit.trim().equalsIgnoreCase("kg") ? BigDecimal.ONE : null, null, null, null);
    }
}
