package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record VaccinationRecordRequest(
        @NotNull LocalDate recordDate,
        @NotBlank String vaccine,
        @NotBlank String dose,
        @NotNull @Positive Integer quantity,
        String notes
) {}
