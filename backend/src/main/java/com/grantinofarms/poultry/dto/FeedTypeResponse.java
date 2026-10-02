package com.grantinofarms.poultry.dto;

public record FeedTypeResponse(
        String id,
        String name,
        String unit,
        String applicableType,
        String status
) {}
