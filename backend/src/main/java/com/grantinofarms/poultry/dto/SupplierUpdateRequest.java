package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 40) String phone,
        @Email @Size(max = 160) String email,
        @Size(max = 240) String address,
        @Size(max = 1000) String notes
) {}
