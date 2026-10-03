package com.grantinofarms.poultry.dto;

public record CustomerResponse(
        String id,
        String farmId,
        String name,
        String phone,
        String notes,
        String createdAt,
        String updatedAt
) {}
