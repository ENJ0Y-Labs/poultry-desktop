package com.grantinofarms.poultry.domain;

import java.time.Instant;
import java.time.LocalDate;

public record FeedUsageEvent(
        String id,
        String batchId,
        String feedTypeId,
        LocalDate usageDate,
        long quantityMilli,
        Instant createdAt
) {}
