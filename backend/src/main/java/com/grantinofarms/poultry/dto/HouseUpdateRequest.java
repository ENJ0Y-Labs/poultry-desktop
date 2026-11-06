package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HouseUpdateRequest(
        @NotBlank String name,
        @NotBlank String code,
        String notes,
        @NotBlank @Pattern(regexp = "ACTIVE|ARCHIVED") String status,
        @NotBlank @Pattern(regexp = "PERMANENT|TEMPORARY") String houseType
) {
    public HouseUpdateRequest(String name, String code, String notes, String status) {
        this(name, code, notes, status, "PERMANENT");
    }
}
