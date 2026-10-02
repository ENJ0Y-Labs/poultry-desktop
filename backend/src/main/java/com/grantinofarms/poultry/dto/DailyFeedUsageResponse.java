package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyFeedUsageResponse(
        String feedTypeId,
        String feedTypeName,
        String unit,
        LocalDate date,
        long quantityMilli,
        BigDecimal quantity
) {}
