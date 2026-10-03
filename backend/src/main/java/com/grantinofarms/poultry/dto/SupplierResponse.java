package com.grantinofarms.poultry.dto;

public record SupplierResponse(
        String id, String farmId, String name, String supplierType,
        String phone, String email, String address, String notes, String status
) {}
