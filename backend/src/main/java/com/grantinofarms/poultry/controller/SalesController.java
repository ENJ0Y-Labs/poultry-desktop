package com.grantinofarms.poultry.controller;
import com.grantinofarms.poultry.dto.SaleResponse;
import com.grantinofarms.poultry.service.SalesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {
 private final SalesService service;
 public SalesController(SalesService service){this.service=service;}
 @GetMapping public Map<String,Object> list(@RequestParam(required=false) String batchId,@RequestParam(required=false) String customerId,@RequestParam(required=false) String saleType,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf){return Map.of("ok",true,"data",service.list(batchId,customerId,saleType,asOf));}
}