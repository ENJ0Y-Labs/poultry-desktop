package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthSetupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 10, max = 200) String password,
        @NotBlank @Size(max = 120) String fullName
) {}
