package com.grantinofarms.poultry.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class WaterContainerSizeRepository {
    private final JdbcTemplate jdbc;

    public WaterContainerSizeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String farmId, int capacity, String now) {
        jdbc.update("""
                INSERT INTO water_container_sizes
                    (id, farm_id, name, capacity_units, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, UUID.randomUUID().toString(), farmId, capacity + " units", capacity, now, now);
    }

    public List<Integer> findActiveSizes(String farmId) {
        return jdbc.query("""
                SELECT capacity_units FROM water_container_sizes
                WHERE farm_id = ? AND status = 'ACTIVE'
                ORDER BY capacity_units
                """, (rs, rowNum) -> rs.getInt("capacity_units"), farmId);
    }

    public void archiveAll(String farmId, String now) {
        jdbc.update("""
                UPDATE water_container_sizes
                SET status = 'ARCHIVED', updated_at = ?
                WHERE farm_id = ? AND status = 'ACTIVE'
                """, now, farmId);
    }
}
