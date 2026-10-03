package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.*;
import com.grantinofarms.poultry.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
 private final InventoryService service;
 public InventoryController(InventoryService service){this.service=service;}
 @GetMapping("/items") public Map<String,Object> items(){return ok(service.listItems());}
 @PostMapping("/items") public Map<String,Object> createItem(@Valid @RequestBody InventoryItemCreateRequest r){return ok(service.createItem(r));}
 @PostMapping("/items/{id}/archive") public Map<String,Object> archiveItem(@PathVariable String id){return ok(service.archiveItem(id));}
 @GetMapping("/movements") public Map<String,Object> movements(@RequestParam(required=false) String itemId,@RequestParam(required=false) LocalDate asOf){return ok(service.movements(itemId,asOf));}
 @PostMapping("/items/{id}/movements") public Map<String,Object> movement(@PathVariable String id,@Valid @RequestBody InventoryMovementRequest r){return ok(service.recordMovement(id,r));}
 private Map<String,Object> ok(Object data){return Map.of("ok",true,"data",data);}
}