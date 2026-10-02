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

    public void upsertActive(String farmId, int capacity, String now) {
        jdbc.update("""
                INSERT INTO water_container_sizes
                    (id, farm_id, name, capacity_units, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (farm_id, capacity_units)
                DO UPDATE SET status = 'ACTIVE', name = excluded.name, updated_at = excluded.updated_at
                """, UUID.randomUUID().toString(), farmId, capacity + " units", capacity, now, now);
    }

    public List<Integer> findActiveSizes(String farmId) {
        return jdbc.query("""
                SELECT capacity_units FROM water_container_sizes
                WHERE farm_id = ? AND status = 'ACTIVE'
                ORDER BY capacity_units
                """, (rs, rowNum) -> rs.getInt("capacity_units"), farmId);
    }

    public void archiveAllExcept(String farmId, List<Integer> activeSizes, String now) {
        if (activeSizes.isEmpty()) {
            jdbc.update("""
                    UPDATE water_container_sizes
                    SET status = 'ARCHIVED', updated_at = ?
                    WHERE farm_id = ? AND status = 'ACTIVE'
                    """, now, farmId);
            return;
        }

        String placeholders = String.join(",", activeSizes.stream().map(v -> "?").toList());
        Object[] args = new Object[activeSizes.size() + 2];
        args[0] = now;
        args[1] = farmId;
        for (int i = 0; i < activeSizes.size(); i++) args[i + 2] = activeSizes.get(i);

        String sql = """
                UPDATE water_container_sizes
                SET status = 'ARCHIVED', updated_at = ?
                WHERE farm_id = ? AND status = 'ACTIVE'
                  AND capacity_units NOT IN (%s)
                """.formatted(placeholders);
        jdbc.update(sql, args);
    }
}
