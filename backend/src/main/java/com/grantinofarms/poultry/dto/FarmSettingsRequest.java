package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record FarmSettingsRequest(
        @NotNull @Positive Integer defaultCrateSize,
        @Positive Integer defaultWaterContainerSize,
        @NotEmpty List<@Positive Integer> waterContainerSizes,
        @NotBlank @Size(max = 20) String waterUnitLabel
) {}
