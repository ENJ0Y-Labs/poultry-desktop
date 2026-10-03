package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record ExpenseRequest(
        @NotNull LocalDate occurredDate,
        @NotBlank String description,
        @Positive long amountMinor,
        @NotBlank @Pattern(regexp = "FEED|DRUGS|OTHER") String category,
        String batchId
) {}
