package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.FarmResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class FarmRepository {
    private final JdbcTemplate jdbc;

    public FarmRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM farms", Long.class);
        return count == null ? 0 : count;
    }

    public void insert(String id, String name, String location, String timezone, String currency, String now) {
        jdbc.update("""
                INSERT INTO farms (id, name, location, timezone, currency, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, id, name, location, timezone, currency, now, now);
    }

    public FarmResponse findActive() {
        try {
            return jdbc.queryForObject("""
                    SELECT id, name, location, timezone, currency, status, created_at, updated_at
                    FROM farms WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1
                    """, (rs, rowNum) -> new FarmResponse(
                    rs.getString("id"), rs.getString("name"), rs.getString("location"),
                    rs.getString("timezone"), rs.getString("currency"), rs.getString("status"),
                    rs.getString("created_at"), rs.getString("updated_at")
            ));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void update(String id, String name, String location, String timezone, String currency, String now) {
        jdbc.update("""
                UPDATE farms
                SET name = ?, location = ?, timezone = ?, currency = ?, updated_at = ?
                WHERE id = ? AND status = 'ACTIVE'
                """, name, location, timezone, currency, now, id);
    }

    public boolean exists(String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM farms WHERE id = ?", Integer.class, id);
        return count != null && count == 1;
    }
}
