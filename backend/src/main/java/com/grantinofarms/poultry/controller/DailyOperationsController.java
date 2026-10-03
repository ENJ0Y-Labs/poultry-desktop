package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.DailyRecordRequest;
import com.grantinofarms.poultry.service.DailyOperationsService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/batches/{batchId}/daily-records")
public class DailyOperationsController {
 private final DailyOperationsService service;
 public DailyOperationsController(DailyOperationsService service){this.service=service;}
 @PostMapping public Map<String,Object> create(@PathVariable String batchId,@Valid @RequestBody DailyRecordRequest r){return ok(service.create(batchId,r));}
 @GetMapping("/{date}") public Map<String,Object> get(@PathVariable String batchId,@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return ok(service.get(batchId,date));}
 @GetMapping public Map<String,Object> list(@PathVariable String batchId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return ok(service.list(batchId,from,to));}
 private Map<String,Object> ok(Object data){return Map.of("ok",true,"data",data);}
}