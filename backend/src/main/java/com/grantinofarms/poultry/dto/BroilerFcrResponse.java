package com.grantinofarms.poultry.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BroilerFcrResponse(
        String batchId,
        LocalDate asOf,
        BigDecimal feedConsumedKg,
        BigDecimal liveWeightGainKg,
        BigDecimal fcr
) {}
