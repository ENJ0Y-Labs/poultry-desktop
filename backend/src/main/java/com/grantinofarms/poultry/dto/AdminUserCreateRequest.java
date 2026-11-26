package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminUserCreateRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]{2,31}") String username,
        @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(min = 10, max = 200) String password,
        @NotBlank @Pattern(regexp = "WORKER|ADMINISTRATOR|MANAGER") String role
) {}
