package com.grantinofarms.poultry.dto;

import java.util.List;

public record FarmSettingsResponse(
        Integer defaultCrateSize,
        Integer defaultWaterContainerSize,
        List<Integer> waterContainerSizes
) {}
