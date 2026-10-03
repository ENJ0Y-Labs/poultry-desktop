package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;

public record PricingSettingsResponse(
        BigDecimal targetMarginPercent,
        BigDecimal workingMarginPercent
) {}
