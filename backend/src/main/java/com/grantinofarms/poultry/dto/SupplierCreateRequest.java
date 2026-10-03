package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierCreateRequest(
        @NotBlank @Size(max=160) String name,
        @Size(max=40) String phone,
        @Size(max=200) String email,
        @Size(max=250) String address,
        @Size(max=500) String notes
) {}
