package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightRecordRequest(
        @NotNull LocalDate recordDate,
        @NotNull @Positive Integer sampleQuantity,
        @NotNull @Positive BigDecimal totalWeightKg,
        String notes
) {}
