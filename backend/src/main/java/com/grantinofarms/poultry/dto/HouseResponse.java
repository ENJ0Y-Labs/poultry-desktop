package com.grantinofarms.poultry.dto;

public record HouseResponse(
        String id,
        String farmId,
        String name,
        String code,
        String notes,
        String status,
        String createdAt,
        String updatedAt,
        String houseType
) {
    public HouseResponse(String id, String farmId, String name, String code, String notes,
                         String status, String createdAt, String updatedAt) {
        this(id, farmId, name, code, notes, status, createdAt, updatedAt, "PERMANENT");
    }
}
