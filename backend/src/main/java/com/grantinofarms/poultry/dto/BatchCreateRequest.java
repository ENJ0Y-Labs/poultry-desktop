package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BatchCreateRequest(
        @NotNull String type,
        @NotNull LocalDate placementDate,
        @NotBlank String houseId,
        @NotNull @Positive Integer initialBirdCount,
        String supplierId,
        Boolean purchaseCostRecorded,
        @PositiveOrZero Long purchaseCostMinor
) {
    public BatchCreateRequest(String type, LocalDate placementDate, String houseId,
                              Integer initialBirdCount, String supplierId, Long purchaseCostMinor) {
        this(type, placementDate, houseId, initialBirdCount, supplierId, true, purchaseCostMinor);
    }
}
