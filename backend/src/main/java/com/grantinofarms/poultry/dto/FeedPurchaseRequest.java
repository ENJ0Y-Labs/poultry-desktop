package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FeedPurchaseRequest(
        @NotBlank String feedTypeId,
        String supplierId,
        @NotNull LocalDate purchaseDate,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String unit,
        @Positive long totalCostMinor
) {}
