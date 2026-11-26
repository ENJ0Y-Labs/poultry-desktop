package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserPasswordResetRequest(
        @NotBlank @Size(min = 10, max = 200) String password
) {}
