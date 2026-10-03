package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FeedUsageRequest(
        @NotBlank String feedTypeId,
        @NotNull LocalDate usageDate,
        @NotNull @Positive BigDecimal quantity,
        @NotBlank String unit,
        String reason
) {
    public FeedUsageRequest(String feedTypeId, LocalDate usageDate, BigDecimal quantity, String unit) {
        this(feedTypeId, usageDate, quantity, unit, null);
    }
}
