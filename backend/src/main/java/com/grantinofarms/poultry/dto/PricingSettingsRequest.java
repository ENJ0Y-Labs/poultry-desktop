package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record PricingSettingsRequest(
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "99.999999")
        BigDecimal targetMarginPercent
) {}
