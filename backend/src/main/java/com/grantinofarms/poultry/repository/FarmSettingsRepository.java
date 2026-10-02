package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.FarmSettingsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FarmSettingsRepository {
    private final JdbcTemplate jdbc;

    public FarmSettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String farmId, int crateSize, Integer defaultWaterSize, String now) {
        jdbc.update("""
                INSERT INTO farm_settings
                    (farm_id, default_crate_size, default_water_container_size, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, farmId, crateSize, defaultWaterSize, now, now);
    }

    public void update(String farmId, int crateSize, Integer defaultWaterSize, String now) {
        jdbc.update("""
                UPDATE farm_settings
                SET default_crate_size = ?, default_water_container_size = ?, updated_at = ?
                WHERE farm_id = ?
                """, crateSize, defaultWaterSize, now, farmId);
    }

    public FarmSettingsResponse find(String farmId) {
        return jdbc.query("""
                SELECT default_crate_size, default_water_container_size
                FROM farm_settings WHERE farm_id = ?
                """, (rs, rowNum) -> new FarmSettingsResponse(
                rs.getInt("default_crate_size"),
                (Integer) rs.getObject("default_water_container_size"),
                List.of()
        ), farmId).stream().findFirst().orElse(null);
    }
}
