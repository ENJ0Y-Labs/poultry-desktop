package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record InventoryItemCreateRequest(
        @NotBlank String name,
        @NotBlank String category,
        @NotBlank String unit,
        @DecimalMin("0.0") BigDecimal reorderLevel
) {}
