package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        Boolean rememberMe
) {
    public boolean persistentSession() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
