package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.ExpenseRequest;
import com.grantinofarms.poultry.dto.ExpenseResponse;
import com.grantinofarms.poultry.dto.FarmResponse;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.ExpenseRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final FarmRepository farmRepository;
    private final BatchRepository batchRepository;
    private final AuditRepository auditRepository;

    public ExpenseService(ExpenseRepository expenseRepository,
                          FarmRepository farmRepository,
                          BatchRepository batchRepository,
                          AuditRepository auditRepository) {
        this.expenseRepository = expenseRepository;
        this.farmRepository = farmRepository;
        this.batchRepository = batchRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        FarmResponse farm = farmRepository.findActive();
        if (farm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "No active farm has been created.");
        }

        String batchId = cleanNullable(request.batchId());
        if (batchId != null) {
            var batch = batchRepository.findById(batchId);
            if (batch == null || !farm.id().equals(batch.farmId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND",
                        "Batch not found for the active farm.");
            }
        }

        String description = request.description().trim();
        String category = request.category().trim().toUpperCase();
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        expenseRepository.insert(
                id, farm.id(), batchId, request.occurredDate(), description,
                request.amountMinor(), category, now
        );

        auditRepository.append(
                farm.id(), "CREATE", "EXPENSE", id, null, null,
                String.format("{\"expenseId\":\"%s\",\"category\":\"%s\"}", id, category), now
        );

        return new ExpenseResponse(
                id, farm.id(), batchId, request.occurredDate(), description,
                request.amountMinor(), category, null, null, now
        );
    }

    public List<ExpenseResponse> list(String batchId) {
        return list(batchId, null);
    }

    public List<ExpenseResponse> list(String batchId, LocalDate asOf) {
        FarmResponse farm = farmRepository.findActive();
        if (farm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "No active farm has been created.");
        }

        String normalizedBatchId = cleanNullable(batchId);
        if (normalizedBatchId != null) {
            var batch = batchRepository.findById(normalizedBatchId);
            if (batch == null || !farm.id().equals(batch.farmId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND",
                        "Batch not found for the active farm.");
            }
        }

        return expenseRepository.findByFarm(farm.id(), normalizedBatchId, asOf);
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
