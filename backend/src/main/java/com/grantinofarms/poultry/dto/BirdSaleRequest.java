package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record BirdSaleRequest(
        @NotNull LocalDate recordDate,
        @NotNull @Positive Integer quantity,
        @NotNull @Positive Long pricePerBirdMinor,
        @NotBlank String customer,
        Boolean confirmedBelowTarget,
        String customerId
) {
    public BirdSaleRequest(LocalDate recordDate, Integer quantity, Long pricePerBirdMinor, String customer) {
        this(recordDate, quantity, pricePerBirdMinor, customer, false, null);
    }

    public BirdSaleRequest(LocalDate recordDate, Integer quantity, Long pricePerBirdMinor,
                           String customer, Boolean confirmedBelowTarget) {
        this(recordDate, quantity, pricePerBirdMinor, customer, confirmedBelowTarget, null);
    }
}
