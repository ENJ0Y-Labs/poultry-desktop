package com.grantinofarms.poultry.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record ApplicationSettingsRequest(
    @NotBlank @Pattern(regexp="dashboard|operations|reports|settings") String startPage,
    @NotBlank @Pattern(regexp="YYYY-MM-DD|DD/MM/YYYY|DD-MM-YYYY") String dateFormat
) {}
