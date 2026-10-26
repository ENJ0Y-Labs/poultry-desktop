package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 254) String loginIdentifier,
        @NotBlank String password,
        Boolean rememberMe
) {
    public LoginRequest(String loginIdentifier, String password) {
        this(loginIdentifier, password, false);
    }

    public boolean persistentSession() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
