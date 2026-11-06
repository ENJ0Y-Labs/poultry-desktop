package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record PopulationAdjustmentResponse(
        String id, String batchId, LocalDate effectiveDate,
        int previousCount, int newCount, int difference,
        String reason, String userId, String createdAt
) {}
