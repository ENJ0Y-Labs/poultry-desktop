package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.FarmResponse;
import com.grantinofarms.poultry.dto.FarmSettingsRequest;
import com.grantinofarms.poultry.dto.FarmSettingsResponse;
import com.grantinofarms.poultry.dto.FarmUpdateRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.FarmSettingsRepository;
import com.grantinofarms.poultry.repository.WaterContainerSizeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FarmService {
    private final FarmRepository farmRepository;
    private final FarmSettingsRepository settingsRepository;
    private final WaterContainerSizeRepository waterRepository;
    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationSettingsRepository applicationSettingsRepository;
    private final BackupSettingsRepository backupSettingsRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;

    public FarmService(FarmRepository farmRepository,
                       FarmSettingsRepository settingsRepository,
                       WaterContainerSizeRepository waterRepository,
                       AuditRepository auditRepository,
                       ObjectMapper objectMapper,
                       ApplicationSettingsRepository applicationSettingsRepository,
                       BackupSettingsRepository backupSettingsRepository,
                       ExpenseCategoryRepository expenseCategoryRepository) {
        this.farmRepository = farmRepository;
        this.settingsRepository = settingsRepository;
        this.waterRepository = waterRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.applicationSettingsRepository = applicationSettingsRepository;
        this.backupSettingsRepository = backupSettingsRepository;
        this.expenseCategoryRepository = expenseCategoryRepository;
    }

    @Transactional
    public FarmResponse create(FarmCreateRequest request) {
        if (farmRepository.count() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "FARM_EXISTS",
                    "This local installation already has a farm.");
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        farmRepository.insert(id, request.name().trim(), clean(request.location()),
                request.timezone().trim(), request.currency().trim().toUpperCase(), now);
        settingsRepository.insert(id, 30, null, now);
        applicationSettingsRepository.insert(id, now);
        backupSettingsRepository.insert(id, now);
        expenseCategoryRepository.insert(UUID.randomUUID().toString(), id, "FEED", now);
        expenseCategoryRepository.insert(UUID.randomUUID().toString(), id, "DRUGS", now);
        expenseCategoryRepository.insert(UUID.randomUUID().toString(), id, "OTHER", now);

        auditRepository.append(id, "CREATE", "FARM", id, null, null,
                json(Map.of("name", request.name().trim())), now);

        return farmRepository.findActive();
    }

    public FarmResponse get() {
        FarmResponse farm = farmRepository.findActive();
        if (farm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND",
                    "No farm has been created yet.");
        }
        return farm;
    }

    @Transactional
    public FarmResponse update(FarmUpdateRequest request) {
        FarmResponse current = get();
        String now = Instant.now().toString();
        farmRepository.update(current.id(), request.name().trim(), clean(request.location()),
                request.timezone().trim(), request.currency().trim().toUpperCase(), now);
        auditRepository.append(current.id(), "UPDATE", "FARM", current.id(), null,
                json(current), json(request), now);
        return get();
    }

    public FarmSettingsResponse getSettings() {
        FarmResponse farm = get();
        FarmSettingsResponse base = settingsRepository.find(farm.id());
        if (base == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SETTINGS_MISSING",
                    "Farm settings are missing.");
        }
        return new FarmSettingsResponse(base.defaultCrateSize(), base.defaultWaterContainerSize(),
                waterRepository.findActiveSizes(farm.id()));
    }

    @Transactional
    public FarmSettingsResponse updateSettings(FarmSettingsRequest request) {
        FarmResponse farm = get();
        List<Integer> sizes = request.waterContainerSizes().stream().distinct().sorted().toList();
        if (request.defaultWaterContainerSize() != null &&
                !sizes.contains(request.defaultWaterContainerSize())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEFAULT_WATER_SIZE",
                    "The default water container size must be one of the configured sizes.");
        }

        String now = Instant.now().toString();
        settingsRepository.update(farm.id(), request.defaultCrateSize(),
                request.defaultWaterContainerSize(), now);
        waterRepository.archiveAllExcept(farm.id(), sizes, now);
        for (Integer size : sizes) {
            waterRepository.upsertActive(farm.id(), size, now);
        }

        auditRepository.append(farm.id(), "UPDATE", "FARM_SETTINGS", farm.id(), null,
                null, json(request), now);
        return getSettings();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize audit data", e);
        }
    }
}
