package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WaterContainerEntryRequest(
        @NotBlank String containerSizeId,
        @NotNull @Positive Integer containerCount
) {}
