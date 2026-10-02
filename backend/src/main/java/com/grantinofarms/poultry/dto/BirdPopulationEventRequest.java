package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BirdPopulationEventRequest(
        @NotNull LocalDate eventDate,
        @NotNull @Positive Integer quantity,
        String reason
) {}
