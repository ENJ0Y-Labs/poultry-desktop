package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EggSaleRequest(
        @NotNull LocalDate recordDate,
        @NotBlank String customer,
        @NotNull @Positive @DecimalMin(value = "0.001") BigDecimal crates,
        @NotNull @Positive Long pricePerCrateMinor
) {}
