package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.FeedService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {
 private final FeedService service;
 public FeedController(FeedService service){this.service=service;}
 @GetMapping("/types") public Map<String,Object> types(){return ok(service.types());}
 @PostMapping("/types") public Map<String,Object> createType(@Valid @RequestBody FeedTypeCreateRequest r){return ok(service.createType(r));}
 @PostMapping("/types/{id}/archive") public Map<String,Object> archiveType(@PathVariable String id){return ok(service.archiveType(id));}
 @PostMapping("/purchases") public Map<String,Object> purchase(@Valid @RequestBody FeedPurchaseRequest r){return ok(service.purchase(r));}
 @GetMapping("/inventory") public Map<String,Object> inventory(@RequestParam(required=false) LocalDate asOf){return ok(service.inventory(asOf));}
 @PostMapping("/batches/{batchId}/usage") public Map<String,Object> use(@PathVariable String batchId,@Valid @RequestBody FeedUsageRequest r){return ok(service.use(batchId,r));}
 @GetMapping("/batches/{batchId}/cost") public Map<String,Object> batchCost(@PathVariable String batchId,@RequestParam(required=false) LocalDate asOf){return ok(service.batchCost(batchId,asOf));}
 private Map<String,Object> ok(Object data){return Map.of("ok",true,"data",data);}
}