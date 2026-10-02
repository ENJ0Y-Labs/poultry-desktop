package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record DrugRecordRequest(
        @NotNull LocalDate recordDate,
        @NotBlank String drug,
        @NotNull @Positive Integer quantity,
        @Positive long costMinor,
        @NotBlank String reason
) {}
