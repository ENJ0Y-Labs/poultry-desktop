package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record EggCollectionRequest(
        @NotNull LocalDate recordDate,
        @NotNull @PositiveOrZero Integer good,
        @NotNull @PositiveOrZero Integer cracked,
        String notes
) {}
