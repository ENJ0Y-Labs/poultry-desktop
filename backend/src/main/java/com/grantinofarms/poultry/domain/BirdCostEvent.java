package com.grantinofarms.poultry.domain;

import java.time.LocalDate;

public record BirdCostEvent(
        LocalDate eventDate,
        String eventType,
        long amountMinor
) {}
