package com.grantinofarms.poultry.service;
import com.grantinofarms.poultry.dto.ExpenseCategoryRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service
public class ExpenseCategoryService {
    private final ExpenseCategoryRepository repository;
    private final FarmRepository farmRepository;
    private final AuditRepository auditRepository;
    public ExpenseCategoryService(ExpenseCategoryRepository repository,FarmRepository farmRepository,AuditRepository auditRepository){
        this.repository=repository;this.farmRepository=farmRepository;this.auditRepository=auditRepository;
    }
    public List<Map<String,String>> list(){ return repository.findActive(farmId()).stream().map(r->Map.of("id",r.id(),"name",r.name(),"status",r.status())).toList(); }
    @Transactional public Map<String,String> create(ExpenseCategoryRequest request){
        String farmId=farmId(),name=request.name().trim(),now=Instant.now().toString(),id=UUID.randomUUID().toString();
        try { repository.insert(id,farmId,name,now); }
        catch(DataIntegrityViolationException e){ throw new ApiException(HttpStatus.CONFLICT,"DUPLICATE_EXPENSE_CATEGORY","An expense category with this name already exists."); }
        auditRepository.append(farmId,"CREATE","EXPENSE_CATEGORY",id,"Expense category created",null,
                String.format("{"id":"%s","name":"%s","status":"ACTIVE"}",id,name.replace(""","\\"")),now);
        return Map.of("id",id,"name",name,"status","ACTIVE");
    }
    @Transactional public Map<String,String> archive(String id){
        String farmId=farmId(); var row=repository.findById(farmId,id);
        if(row==null) throw new ApiException(HttpStatus.NOT_FOUND,"EXPENSE_CATEGORY_NOT_FOUND","Expense category not found.");
        if("ARCHIVED".equals(row.status())) return Map.of("id",row.id(),"name",row.name(),"status",row.status());
        String now=Instant.now().toString(); repository.archive(farmId,id,now);
        auditRepository.append(farmId,"ARCHIVE","EXPENSE_CATEGORY",id,"Expense category archived",
                String.format("{"id":"%s","name":"%s","status":"ACTIVE"}",id,row.name().replace(""","\\"")),
                String.format("{"id":"%s","name":"%s","status":"ARCHIVED"}",id,row.name().replace(""","\\"")),now);
        return Map.of("id",row.id(),"name",row.name(),"status","ARCHIVED");
    }
    public boolean isActive(String farmId,String name){ var row=repository.findByName(farmId,name); return row!=null&&"ACTIVE".equals(row.status()); }
    private String farmId(){ var farm=farmRepository.findActive(); if(farm==null) throw new ApiException(HttpStatus.NOT_FOUND,"FARM_NOT_FOUND","No active farm has been created."); return farm.id(); }
}
