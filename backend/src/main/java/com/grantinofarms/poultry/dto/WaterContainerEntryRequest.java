package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WaterContainerEntryRequest(
        @NotNull @Positive Integer containerSizeUnits,
        @NotNull @Positive Integer containerCount
) {}
