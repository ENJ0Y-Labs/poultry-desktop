package com.grantinofarms.poultry.dto;

import java.time.LocalDate;

public record BirdSaleResponse(
        String id,
        String batchId,
        LocalDate recordDate,
        int quantity,
        long pricePerBirdMinor,
        long totalAmountMinor,
        String customer
) {}
