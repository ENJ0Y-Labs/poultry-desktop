package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.HouseResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HouseRepository {
    private final JdbcTemplate jdbc;

    public HouseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String id, String farmId, String name, String code, String notes, String now) {
        jdbc.update("""
                INSERT INTO houses (id, farm_id, name, code, notes, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, id, farmId, name, code, notes, now, now);
    }

    public List<HouseResponse> findByFarm(String farmId) {
        return jdbc.query("""
                SELECT id, farm_id, name, code, notes, status, created_at, updated_at
                FROM houses WHERE farm_id = ? ORDER BY name
                """, (rs, rowNum) -> new HouseResponse(
                rs.getString("id"), rs.getString("farm_id"), rs.getString("name"),
                rs.getString("code"), rs.getString("notes"), rs.getString("status"),
                rs.getString("created_at"), rs.getString("updated_at")
        ), farmId);
    }

    public HouseResponse findById(String id) {
        return jdbc.query("""
                SELECT id, farm_id, name, code, notes, status, created_at, updated_at
                FROM houses WHERE id = ?
                """, (rs, rowNum) -> new HouseResponse(
                rs.getString("id"), rs.getString("farm_id"), rs.getString("name"),
                rs.getString("code"), rs.getString("notes"), rs.getString("status"),
                rs.getString("created_at"), rs.getString("updated_at")
        ), id).stream().findFirst().orElse(null);
    }

    public void update(String id, String farmId, String name, String code, String notes, String status, String now) {
        jdbc.update("""
                UPDATE houses
                SET name = ?, code = ?, notes = ?, status = ?, updated_at = ?
                WHERE id = ? AND farm_id = ?
                """, name, code, notes, status, now, id, farmId);
    }
}
