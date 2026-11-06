package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record PopulationAdjustmentRequest(
        @NotNull LocalDate effectiveDate,
        @NotNull @PositiveOrZero Integer newCount,
        @NotBlank String reason
) {}
