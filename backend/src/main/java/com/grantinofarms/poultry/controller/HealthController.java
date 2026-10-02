package com.grantinofarms.poultry.controller;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/health") public class HealthController{
 private final JdbcTemplate jdbcTemplate;
 public HealthController(JdbcTemplate jdbcTemplate){this.jdbcTemplate=jdbcTemplate;}
 @GetMapping public Map<String,Object> health(){jdbcTemplate.queryForObject("SELECT 1",Integer.class);return Map.of("ok",true,"status","UP","database","UP");}
}