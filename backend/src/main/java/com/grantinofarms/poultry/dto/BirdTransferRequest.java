package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BirdTransferRequest(
        @NotBlank String targetBatchId,
        @NotNull LocalDate eventDate,
        @NotNull @Positive Integer quantity,
        String reason,
        String notes
) {
    public BirdTransferRequest(String targetBatchId, LocalDate eventDate, Integer quantity, String reason) {
        this(targetBatchId, eventDate, quantity, reason, null);
    }
}
