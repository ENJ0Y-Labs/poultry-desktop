package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthSetupRequest(
        @Size(min = 3, max = 32) String username,
        String email,
        @NotBlank @Size(min = 10, max = 200) String password,
        @NotBlank @Size(max = 120) String fullName
) {
    public AuthSetupRequest(String email, String password, String fullName) {
        this(null, email, password, fullName);
    }
}
