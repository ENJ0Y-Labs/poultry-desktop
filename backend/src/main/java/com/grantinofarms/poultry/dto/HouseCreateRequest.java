package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HouseCreateRequest(
        @NotBlank String name,
        @NotBlank String code,
        String notes,
        @NotBlank @Pattern(regexp = "PERMANENT|TEMPORARY") String houseType
) {
    public HouseCreateRequest(String name, String code, String notes) {
        this(name, code, notes, "PERMANENT");
    }
}
