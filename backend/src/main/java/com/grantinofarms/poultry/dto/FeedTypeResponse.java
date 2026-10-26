package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;

public record FeedTypeResponse(
        String id,
        String name,
        String unit,
        String applicableType,
        String status,
        BigDecimal kgPerUnit
) {}
