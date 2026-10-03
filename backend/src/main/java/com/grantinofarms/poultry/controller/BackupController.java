package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.BackupService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/backup")
public class BackupController {
    private final BackupService service;
    public BackupController(BackupService service){this.service=service;}
    @PostMapping public Map<String,Object> backup(@RequestBody(required=false) Map<String,String> body){return Map.of("ok",true,"data",service.create(body==null?null:body.get("directory")));}
    @PostMapping("/validate") public Map<String,Object> validate(@RequestBody Map<String,String> body){
        String file=body==null?null:body.get("file");
        if(file==null||file.isBlank())throw new com.grantinofarms.poultry.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"BACKUP_FILE_REQUIRED","Backup file is required.");
        return Map.of("ok",true,"data",service.validate(file));
    }
}
