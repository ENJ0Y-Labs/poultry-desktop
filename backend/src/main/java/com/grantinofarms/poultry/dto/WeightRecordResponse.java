package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightRecordResponse(
        String id,
        String batchId,
        LocalDate recordDate,
        int sampleQuantity,
        BigDecimal totalWeightKg,
        BigDecimal averageWeightKg,
        BigDecimal weightGainKg,
        String notes
) {}
