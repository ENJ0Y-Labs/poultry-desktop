package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryMovementRequest(
        @NotNull LocalDate movementDate,
        @NotBlank String movementType,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String reason,
        @NotBlank String source,
        String batchId
) {}
