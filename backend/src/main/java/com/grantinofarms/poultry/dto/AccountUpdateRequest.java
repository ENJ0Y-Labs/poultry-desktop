package com.grantinofarms.poultry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record AccountUpdateRequest(
    @NotBlank @Size(min=3,max=32) String username,
    String email,
    @NotBlank @Size(max=120) String fullName,
    @Pattern(regexp="^$|.{10,200}") String currentPassword,
    @Pattern(regexp="^$|.{10,200}") String newPassword
) {
    public AccountUpdateRequest(String email, String fullName, String currentPassword, String newPassword) {
        this(null, email, fullName, currentPassword, newPassword);
    }
}
