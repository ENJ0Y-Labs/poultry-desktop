package com.grantinofarms.poultry.dto;

public record FarmResponse(
        String id,
        String name,
        String location,
        String timezone,
        String currency,
        String status,
        String createdAt,
        String updatedAt
) {}
