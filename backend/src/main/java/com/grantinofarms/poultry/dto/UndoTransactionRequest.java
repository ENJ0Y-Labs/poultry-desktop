package com.grantinofarms.poultry.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record UndoTransactionRequest(@NotBlank @Size(max = 500) String reason) {}
