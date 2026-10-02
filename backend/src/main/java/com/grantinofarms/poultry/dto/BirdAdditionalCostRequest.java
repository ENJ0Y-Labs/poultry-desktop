package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BirdAdditionalCostRequest(
        @NotNull LocalDate eventDate,
        @NotNull @Positive Long amountMinor,
        @Size(max = 500) String reason
) {}
