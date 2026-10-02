package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record HealthRecordRequest(
        @NotNull LocalDate recordDate,
        @NotBlank String conditionProblem,
        @NotBlank String description,
        @NotBlank String action
) {}
