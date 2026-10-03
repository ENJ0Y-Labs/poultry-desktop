package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.SaleResponse;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.SalesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class SalesService {
    private final SalesRepository repository;
    private final FarmRepository farmRepository;
    private final BatchRepository batchRepository;
    private final CustomerRepository customerRepository;

    public SalesService(SalesRepository repository,
                        FarmRepository farmRepository,
                        BatchRepository batchRepository,
                        CustomerRepository customerRepository) {
        this.repository = repository;
        this.farmRepository = farmRepository;
        this.batchRepository = batchRepository;
        this.customerRepository = customerRepository;
    }

    public List<SaleResponse> list(String batchId, String customerId, String saleType, LocalDate asOf) {
        String farmId = requireFarm();
        String batch = cleanNullable(batchId);
        String customer = customerId == null || customerId.isBlank() ? null : customerId.trim();
        String type = saleType == null || saleType.isBlank() ? null : saleType.trim().toUpperCase();

        if (batch != null && !batchRepository.belongsToFarm(batch, farmId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
        if (customer != null && customerRepository.findById(farmId, customer) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found.");
        }
        if (type != null && !"EGG".equals(type) && !"BROILER".equals(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SALE_TYPE",
                    "Sale type must be EGG or BROILER.");
        }

        return repository.findByFarm(farmId, batch, customer, type,
                asOf == null ? LocalDate.now() : asOf);
    }

    private String requireFarm() {
        var farm = farmRepository.findActive();
        if (farm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "No active farm exists.");
        }
        return farm.id();
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
