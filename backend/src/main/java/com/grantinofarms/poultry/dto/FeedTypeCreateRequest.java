package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record FeedTypeCreateRequest(
        @NotBlank String name,
        @NotBlank String unit,
        @NotBlank String applicableType,
        BigDecimal kgPerUnit
) {
    public FeedTypeCreateRequest(String name, String unit, String applicableType) {
        this(name, unit, applicableType, unit != null && unit.trim().equalsIgnoreCase("kg") ? BigDecimal.ONE : null);
    }
}
