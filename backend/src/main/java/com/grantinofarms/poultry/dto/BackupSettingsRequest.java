package com.grantinofarms.poultry.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
public record BackupSettingsRequest(
    @NotNull Boolean enabled,
    String directory,
    @Min(3600000) long intervalMs
) {}
