package com.grantinofarms.poultry.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record AccountUpdateRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(max=120) String fullName,
    @Size(min=10,max=200) String currentPassword,
    @Size(min=10,max=200) String newPassword
) {}
