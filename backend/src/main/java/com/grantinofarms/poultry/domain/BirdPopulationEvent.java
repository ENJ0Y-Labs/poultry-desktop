package com.grantinofarms.poultry.domain;

import java.time.LocalDate;

public record BirdPopulationEvent(
        LocalDate eventDate,
        String eventType,
        int quantity
) {}
