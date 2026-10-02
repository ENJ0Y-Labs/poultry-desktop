package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record BatchResponse(
        String id,
        String farmId,
        String houseId,
        String supplierId,
        String code,
        String type,
        LocalDate placementDate,
        int initialBirdCount,
        Long purchaseCostMinor,
        String status,
        String createdAt,
        String updatedAt
) {}
