package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricingResponse(
        String batchId,
        LocalDate asOf,
        int quantity,
        long actualCostMinor,
        BigDecimal targetMarginPercent,
        BigDecimal workingMarginPercent,
        Long targetPricePerBirdMinor,
        Long workingPricePerBirdMinor
) {}
