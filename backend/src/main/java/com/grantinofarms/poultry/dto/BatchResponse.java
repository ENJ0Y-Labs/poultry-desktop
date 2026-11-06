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
        String updatedAt,
        String populationCountStatus
) {
    public BatchResponse(String id, String farmId, String houseId, String supplierId, String code,
                         String type, LocalDate placementDate, int initialBirdCount,
                         Long purchaseCostMinor, String status, String createdAt, String updatedAt) {
        this(id, farmId, houseId, supplierId, code, type, placementDate, initialBirdCount,
                purchaseCostMinor, status, createdAt, updatedAt, "VERIFIED");
    }
}
