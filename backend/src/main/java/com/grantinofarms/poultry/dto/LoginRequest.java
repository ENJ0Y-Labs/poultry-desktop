package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        String identifier,
        String email,
        @NotBlank @Size(max = 200) String password
) {
    public LoginRequest(String email, String password) {
        this(email, email, password);
    }

    public String loginIdentifier() {
        if (identifier != null && !identifier.isBlank()) return identifier.trim();
        return email == null ? "" : email.trim();
    }
}
