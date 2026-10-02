package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BatchCreateRequest(
        @NotNull String type,
        @NotNull LocalDate placementDate,
        @NotBlank String houseId,
        @NotNull @Positive Integer initialBirdCount,
        String supplierId,
        @Positive Long purchaseCostMinor
) {}
