package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.SupplierCreateRequest;
import com.grantinofarms.poultry.dto.SupplierResponse;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.SupplierRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SupplierService {
    private final SupplierRepository suppliers;
    private final FarmRepository farms;
    private final AuditRepository audit;
    private final ObjectMapper mapper;
    public SupplierService(SupplierRepository suppliers,FarmRepository farms,AuditRepository audit,ObjectMapper mapper){
        this.suppliers=suppliers;this.farms=farms;this.audit=audit;this.mapper=mapper;
    }
    public List<SupplierResponse> list(){return suppliers.findAll(requireFarm());}
    @Transactional public SupplierResponse create(SupplierCreateRequest request){
        String farmId=requireFarm(),id=UUID.randomUUID().toString(),now=Instant.now().toString();
        try{suppliers.insert(id,farmId,request.name().trim(),clean(request.phone()),clean(request.email()),clean(request.address()),clean(request.notes()),now);}
        catch(DataIntegrityViolationException e){throw new ApiException(HttpStatus.CONFLICT,"SUPPLIER_DUPLICATE","A supplier with that name already exists.");}
        SupplierResponse created=suppliers.find(farmId,id);audit.append(farmId,"CREATE","SUPPLIER",id,null,null,json(created),now);return created;
    }
    @Transactional public SupplierResponse archive(String id){
        String farmId=requireFarm();SupplierResponse current=require(farmId,id);
        suppliers.update(id,farmId,current.name(),current.phone(),current.email(),current.address(),current.notes(),"ARCHIVED",Instant.now().toString());
        SupplierResponse updated=suppliers.find(farmId,id);audit.append(farmId,"STATUS_CHANGE","SUPPLIER",id,"Archived",json(current),json(updated),Instant.now().toString());return updated;
    }
    private SupplierResponse require(String farmId,String id){SupplierResponse s=suppliers.find(farmId,id);if(s==null)throw new ApiException(HttpStatus.NOT_FOUND,"SUPPLIER_NOT_FOUND","Supplier not found.");return s;}
    private String requireFarm(){var f=farms.findActive();if(f==null)throw new ApiException(HttpStatus.NOT_FOUND,"FARM_NOT_FOUND","No active farm exists.");return f.id();}
    private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
}
