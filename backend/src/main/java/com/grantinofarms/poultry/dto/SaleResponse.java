package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaleResponse(
        String id,
        String farmId,
        String batchId,
        String customerId,
        String customerName,
        String customerPhone,
        LocalDate saleDate,
        String saleType,
        BigDecimal quantity,
        String unit,
        long unitPriceMinor,
        long totalAmountMinor,
        String referenceType,
        String referenceId
) {}
