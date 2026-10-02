package com.grantinofarms.poultry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record DailyRecordRequest(
        @NotNull LocalDate recordDate,
        String notes,
        @Valid List<WaterContainerEntryRequest> waterContainers
) {}
