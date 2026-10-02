package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.dto.HouseResponse;
import com.grantinofarms.poultry.dto.HouseUpdateRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.HouseRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HouseService {
    private final FarmRepository farmRepository;
    private final HouseRepository houseRepository;
    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public HouseService(FarmRepository farmRepository, HouseRepository houseRepository,
                        AuditRepository auditRepository, ObjectMapper objectMapper) {
        this.farmRepository = farmRepository;
        this.houseRepository = houseRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    public List<HouseResponse> list() {
        String farmId = requireFarmId();
        return houseRepository.findByFarm(farmId);
    }

    @Transactional
    public HouseResponse create(HouseCreateRequest request) {
        String farmId = requireFarmId();
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        try {
            houseRepository.insert(id, farmId, request.name().trim(), request.code().trim().toUpperCase(),
                    clean(request.notes()), now);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "HOUSE_DUPLICATE",
                    "A house/pen with that name or code already exists in this farm.");
        }

        HouseResponse created = houseRepository.findById(id);
        auditRepository.append(farmId, "CREATE", "HOUSE", id, null, null, json(created), now);
        return created;
    }

    @Transactional
    public HouseResponse update(String id, HouseUpdateRequest request) {
        String farmId = requireFarmId();
        HouseResponse current = houseRepository.findById(id);
        if (current == null || !current.farmId().equals(farmId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "HOUSE_NOT_FOUND", "House/pen not found.");
        }

        String now = Instant.now().toString();
        try {
            houseRepository.update(id, farmId, request.name().trim(), request.code().trim().toUpperCase(),
                    clean(request.notes()), request.status(), now);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "HOUSE_DUPLICATE",
                    "A house/pen with that name or code already exists in this farm.");
        }

        HouseResponse updated = houseRepository.findById(id);
        auditRepository.append(farmId, "UPDATE", "HOUSE", id, null, json(current), json(updated), now);
        return updated;
    }

    private String requireFarmId() {
        var farm = farmRepository.findActive();
        if (farm == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND",
                    "Create the farm before managing houses/pens.");
        }
        return farm.id();
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
