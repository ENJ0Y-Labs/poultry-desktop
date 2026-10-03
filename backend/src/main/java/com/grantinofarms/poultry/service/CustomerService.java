package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.CustomerCreateRequest;
import com.grantinofarms.poultry.dto.CustomerResponse;
import com.grantinofarms.poultry.dto.CustomerUpdateRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.CustomerRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerRepository repository;
    private final FarmRepository farmRepository;
    private final AuditRepository auditRepository;

    public CustomerService(CustomerRepository repository,
                           FarmRepository farmRepository,
                           AuditRepository auditRepository) {
        this.repository = repository;
        this.farmRepository = farmRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public CustomerResponse create(CustomerCreateRequest request) {
        String farmId = requireFarm();
        String name = cleanRequired(request.name());
        String phone = cleanNullable(request.phone());
        String notes = cleanNullable(request.notes());
        String now = Instant.now().toString();
        String id = UUID.randomUUID().toString();

        repository.insert(id, farmId, name, phone, notes, now);
        auditRepository.append(farmId, "CREATE", "CUSTOMER", id, null, null,
                String.format("{\"name\":\"%s\",\"phone\":\"%s\"}", name, phone == null ? "" : phone), now);
        return repository.findById(farmId, id);
    }

    public List<CustomerResponse> list() {
        return repository.findAll(requireFarm());
    }

    @Transactional
    public CustomerResponse update(String id, CustomerUpdateRequest request) {
        String farmId = requireFarm();
        CustomerResponse current = repository.findById(farmId, id);
        if (current == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found.");
        }

        String name = cleanRequired(request.name());
        String phone = cleanNullable(request.phone());
        String notes = cleanNullable(request.notes());
        String now = Instant.now().toString();
        repository.update(farmId, id, name, phone, notes, now);
        auditRepository.append(farmId, "UPDATE", "CUSTOMER", id, null,
                current.name(), String.format("{\"name\":\"%s\",\"phone\":\"%s\",\"notes\":\"%s\"}",
                        name, phone == null ? "" : phone, notes == null ? "" : notes), now);
        return repository.findById(farmId, id);
    }

    public CustomerResponse requireForSale(String customerId, String customerName) {
        String farmId = requireFarm();
        if (customerId != null && !customerId.isBlank()) {
            CustomerResponse customer = repository.findById(farmId, customerId.trim());
            if (customer == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found.");
            }
            return customer;
        }

        String name = cleanRequired(customerName);
        CustomerResponse existing = repository.findByName(farmId, name);
        if (existing != null) {
            return existing;
        }

        String now = Instant.now().toString();
        String id = UUID.randomUUID().toString();
        repository.insert(id, farmId, name, null, null, now);
        auditRepository.append(farmId, "CREATE", "CUSTOMER", id, null, null,
                String.format("{\"name\":\"%s\",\"source\":\"SALE\"}", name), now);
        return repository.findById(farmId, id);
    }

    private String requireFarm() {
        var farm = farmRepository.findActive();
        if (farm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "No active farm exists.");
        }
        return farm.id();
    }

    private String cleanRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_NAME_REQUIRED", "Customer name is required.");
        }
        return value.trim();
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
