package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BroilerGrowthResponse(
        String batchId,
        LocalDate asOf,
        BigDecimal averageWeightKg,
        BigDecimal weightGainKg,
        BigDecimal fcr,
        List<WeightRecordResponse> trend
) {}
