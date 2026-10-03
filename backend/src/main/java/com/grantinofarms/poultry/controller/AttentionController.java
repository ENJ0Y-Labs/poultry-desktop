package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.AttentionService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attention")
public class AttentionController {
    private final AttentionService service;
    public AttentionController(AttentionService service){this.service=service;}
    @GetMapping public Map<String,Object> list(@RequestParam(required=false) LocalDate asOf){return Map.of("ok",true,"data",service.list(asOf));}
}
