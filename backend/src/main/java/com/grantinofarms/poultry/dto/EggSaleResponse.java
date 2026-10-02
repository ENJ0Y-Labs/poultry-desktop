package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EggSaleResponse(
        String id,
        String batchId,
        LocalDate recordDate,
        String customer,
        BigDecimal crates,
        int soldEggs,
        int crateSize,
        long pricePerCrateMinor,
        long totalAmountMinor
) {}
