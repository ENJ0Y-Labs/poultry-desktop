package com.grantinofarms.poultry.dto;

public record WaterUsageResponse(
        String containerSizeId,
        String containerName,
        int capacityUnits,
        int containerCount,
        int totalUnits
) {}
