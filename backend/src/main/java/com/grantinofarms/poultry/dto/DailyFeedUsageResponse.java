package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyFeedUsageResponse(
        String feedTypeId,
        String feedTypeName,
        String unit,
        LocalDate date,
        long quantityMilli,
        BigDecimal quantity,
        String brand
) {
    public DailyFeedUsageResponse(String feedTypeId, String feedTypeName, String unit, LocalDate date,
                                  long quantityMilli, BigDecimal quantity) {
        this(feedTypeId, feedTypeName, unit, date, quantityMilli, quantity, null);
    }
}
