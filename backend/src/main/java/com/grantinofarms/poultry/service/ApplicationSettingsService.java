package com.grantinofarms.poultry.service;
import com.grantinofarms.poultry.dto.ApplicationSettingsRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service
public class ApplicationSettingsService {
    private final ApplicationSettingsRepository repository;
    private final FarmRepository farmRepository;
    private final AuditRepository auditRepository;
    public ApplicationSettingsService(ApplicationSettingsRepository repository,FarmRepository farmRepository,AuditRepository auditRepository){
        this.repository=repository;this.farmRepository=farmRepository;this.auditRepository=auditRepository;
    }
    public Map<String,String> get(){var r=require();return Map.of("startPage",r.startPage(),"dateFormat",r.dateFormat());}
    @Transactional public Map<String,String> update(ApplicationSettingsRequest request){
        String farmId=farmId();var old=require();String now=Instant.now().toString();
        repository.update(farmId,request.startPage(),request.dateFormat(),now);
        auditRepository.append(farmId,"UPDATE","APPLICATION_SETTINGS",farmId,"Application settings changed",json(old.startPage(),old.dateFormat()),json(request.startPage(),request.dateFormat()),now);
        return get();
    }
    private ApplicationSettingsRepository.Row require(){var r=repository.find(farmId());if(r==null)throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"APPLICATION_SETTINGS_MISSING","Application settings are missing.");return r;}
    private String farmId(){var farm=farmRepository.findActive();if(farm==null)throw new ApiException(HttpStatus.NOT_FOUND,"FARM_NOT_FOUND","No active farm has been created.");return farm.id();}
    private String json(String page,String format){return String.format("{\"startPage\":\"%s\",\"dateFormat\":\"%s\"}",page,format);}
}
