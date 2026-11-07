package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record FeedTypeUpdateRequest(
        @NotBlank String name,
        String brand,
        String packagingName,
        BigDecimal packageWeightKg
) {}
