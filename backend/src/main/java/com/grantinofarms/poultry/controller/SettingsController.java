package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    private final ApplicationSettingsService application;
    private final BackupSettingsService backup;
    public SettingsController(ApplicationSettingsService application,BackupSettingsService backup){this.application=application;this.backup=backup;}
    @GetMapping("/application") Map<String,Object> application(){return Map.of("ok",true,"data",application.get());}
    @PutMapping("/application") Map<String,Object> application(@Valid @RequestBody ApplicationSettingsRequest r){return Map.of("ok",true,"data",application.update(r));}
    @GetMapping("/backup") Map<String,Object> backup(){return Map.of("ok",true,"data",backup.get());}
    @PutMapping("/backup") Map<String,Object> backup(@Valid @RequestBody BackupSettingsRequest r){return Map.of("ok",true,"data",backup.update(r));}
}
