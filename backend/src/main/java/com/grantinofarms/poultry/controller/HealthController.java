package com.grantinofarms.poultry.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            boolean flywayHistoryExists = tableExists("flyway_schema_history");
            boolean applicationMetadataExists = tableExists("app_metadata");
            boolean schemaValid = flywayHistoryExists && applicationMetadataExists;
            String schemaVersion = flywayHistoryExists ? jdbcTemplate.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1", String.class) : "";

            if (!schemaValid) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "ok", false,
                        "status", "DEGRADED",
                        "database", "UP",
                        "schema", "INVALID",
                        "schemaVersion", schemaVersion
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "status", "UP",
                    "database", "UP",
                    "schema", "VALID",
                    "schemaVersion", schemaVersion
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "ok", false,
                    "status", "DOWN",
                    "database", "DOWN",
                    "schema", "UNKNOWN"
            ));
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
