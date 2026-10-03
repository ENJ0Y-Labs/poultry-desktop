package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record ExpenseResponse(
        String id,
        String farmId,
        String batchId,
        LocalDate occurredDate,
        String description,
        long amountMinor,
        String category,
        String referenceType,
        String referenceId,
        String createdAt
) {}
