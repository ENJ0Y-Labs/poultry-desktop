package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InventoryItemUpdateRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Pattern(regexp = "DRUG|VACCINE|SUPPLY") String category,
        @NotBlank @Size(max = 40) String unit,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal reorderLevel
) {}
