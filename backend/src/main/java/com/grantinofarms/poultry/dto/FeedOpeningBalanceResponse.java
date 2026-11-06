package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record FeedOpeningBalanceResponse(
        String id, String feedTypeId, String feedTypeName, LocalDate effectiveDate,
        long quantityMilli, String unit, String reason, String createdAt
) {}
