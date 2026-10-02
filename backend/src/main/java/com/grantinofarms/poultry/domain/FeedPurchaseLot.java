package com.grantinofarms.poultry.domain;

import java.time.Instant;
import java.time.LocalDate;

public record FeedPurchaseLot(
        String id,
        String feedTypeId,
        LocalDate purchaseDate,
        long quantityMilli,
        long totalCostMinor,
        Instant createdAt
) {}
