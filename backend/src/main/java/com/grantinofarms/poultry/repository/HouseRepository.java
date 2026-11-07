package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.HouseResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HouseRepository {
    private final JdbcTemplate jdbc;

    public HouseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(String id, String farmId, String name, String code, String notes, String houseType, String now) {
        jdbc.update("""
                INSERT INTO houses (id, farm_id, name, code, notes, house_type, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, id, farmId, name, code, notes, houseType, now, now);
    }

    public List<HouseResponse> findByFarm(String farmId) {
        return jdbc.query("""
                SELECT id, farm_id, name, code, notes, status, created_at, updated_at, house_type
                FROM houses WHERE farm_id = ? ORDER BY name
                """, (rs, rowNum) -> map(rs), farmId);
    }

    public HouseResponse findById(String id) {
        return jdbc.query("""
                SELECT id, farm_id, name, code, notes, status, created_at, updated_at, house_type
                FROM houses WHERE id = ?
                """, (rs, rowNum) -> map(rs), id).stream().findFirst().orElse(null);
    }

    public boolean hasActiveBatches(String houseId, String farmId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM batches WHERE house_id = ? AND farm_id = ? AND status = 'ACTIVE'",
                Integer.class, houseId, farmId);
        return count != null && count > 0;
    }

    public void update(String id, String farmId, String name, String code, String notes, String status, String houseType, String now) {
        jdbc.update("""
                UPDATE houses SET name = ?, code = ?, notes = ?, status = ?, house_type = ?, updated_at = ?
                WHERE id = ? AND farm_id = ?
                """, name, code, notes, status, houseType, now, id, farmId);
    }

    private HouseResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new HouseResponse(rs.getString("id"), rs.getString("farm_id"), rs.getString("name"),
                rs.getString("code"), rs.getString("notes"), rs.getString("status"),
                rs.getString("created_at"), rs.getString("updated_at"), rs.getString("house_type"));
    }
}
