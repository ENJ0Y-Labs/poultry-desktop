package com.grantinofarms.poultry.domain;

import java.time.Instant;
import java.time.LocalDate;

public record FeedOpeningStockLot(
        String id,
        String feedTypeId,
        LocalDate effectiveDate,
        long quantityMilli,
        Instant createdAt
) {}
