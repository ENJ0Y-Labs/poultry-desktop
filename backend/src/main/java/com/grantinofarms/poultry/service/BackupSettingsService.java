package com.grantinofarms.poultry.service;
import com.grantinofarms.poultry.dto.BackupSettingsRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service
public class BackupSettingsService {
    private final BackupSettingsRepository repository;
    private final FarmRepository farmRepository;
    private final AuditRepository auditRepository;
    public BackupSettingsService(BackupSettingsRepository repository,FarmRepository farmRepository,AuditRepository auditRepository){
        this.repository=repository;this.farmRepository=farmRepository;this.auditRepository=auditRepository;
    }
    public Map<String,Object> get(){var r=require();return Map.of("enabled",r.enabled(),"directory",r.directory()==null?"":r.directory(),"intervalMs",r.intervalMs());}
    @Transactional public Map<String,Object> update(BackupSettingsRequest request){
        String farmId=farmId();var old=require();String directory=request.directory()==null||request.directory().isBlank()?null:request.directory().trim();
        if(Boolean.TRUE.equals(request.enabled()) && (directory==null||directory.isBlank())) throw new ApiException(HttpStatus.BAD_REQUEST,"BACKUP_DIRECTORY_REQUIRED","A backup directory is required when automatic backups are enabled.");
        String now=Instant.now().toString();repository.update(farmId,Boolean.TRUE.equals(request.enabled()),directory,request.intervalMs(),now);
        auditRepository.append(farmId,"UPDATE","BACKUP_SETTINGS",farmId,"Backup settings changed",
            String.format("{\"enabled\":%s,\"directory\":\"%s\",\"intervalMs\":%d}",old.enabled(),safe(old.directory()),old.intervalMs()),
            String.format("{\"enabled\":%s,\"directory\":\"%s\",\"intervalMs\":%d}",Boolean.TRUE.equals(request.enabled()),safe(directory),request.intervalMs()),now);
        return get();
    }
    private BackupSettingsRepository.Row require(){var r=repository.find(farmId());if(r==null)throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"BACKUP_SETTINGS_MISSING","Backup settings are missing.");return r;}
    private String farmId(){var farm=farmRepository.findActive();if(farm==null)throw new ApiException(HttpStatus.NOT_FOUND,"FARM_NOT_FOUND","No active farm has been created.");return farm.id();}
    private String safe(String s){return s==null?"":s.replace("\"","\\\"");}
}
