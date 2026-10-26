package com.grantinofarms.poultry.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record ApplicationSettingsRequest(
    @NotBlank @Pattern(regexp="dashboard|flocks|daily-work|feed-stock|health|production|sales-money|reports|settings") String startPage,
    @NotBlank @Pattern(regexp="YYYY-MM-DD|DD/MM/YYYY|DD-MM-YYYY") String dateFormat
) {}
