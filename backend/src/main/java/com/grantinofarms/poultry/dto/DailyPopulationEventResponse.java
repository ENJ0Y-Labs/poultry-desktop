package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record DailyPopulationEventResponse(
        String type,
        LocalDate date,
        int quantity,
        String reason,
        String notes
) {}
