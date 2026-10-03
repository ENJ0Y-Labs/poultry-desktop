package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.CustomerResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CustomerRepository {
    private final JdbcTemplate jdbc;

    public CustomerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String id, String farmId, String name, String phone, String notes, String now) {
        jdbc.update("""
                INSERT INTO customers (id, farm_id, name, phone, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, farmId, name, phone, notes, now, now);
    }

    public CustomerResponse findById(String farmId, String id) {
        List<CustomerResponse> rows = jdbc.query("""
                SELECT id, farm_id, name, phone, notes, created_at, updated_at
                FROM customers
                WHERE farm_id = ? AND id = ?
                """, (rs, n) -> map(rs), farmId, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public CustomerResponse findByName(String farmId, String name) {
        List<CustomerResponse> rows = jdbc.query("""
                SELECT id, farm_id, name, phone, notes, created_at, updated_at
                FROM customers
                WHERE farm_id = ? AND name = ?
                ORDER BY created_at
                LIMIT 1
                """, (rs, n) -> map(rs), farmId, name);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<CustomerResponse> findAll(String farmId) {
        return jdbc.query("""
                SELECT id, farm_id, name, phone, notes, created_at, updated_at
                FROM customers
                WHERE farm_id = ?
                ORDER BY name COLLATE NOCASE, created_at
                """, (rs, n) -> map(rs), farmId);
    }

    public void update(String farmId, String id, String name, String phone, String notes, String now) {
        jdbc.update("""
                UPDATE customers
                SET name = ?, phone = ?, notes = ?, updated_at = ?
                WHERE farm_id = ? AND id = ?
                """, name, phone, notes, now, farmId, id);
    }

    private CustomerResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CustomerResponse(
                rs.getString("id"),
                rs.getString("farm_id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("notes"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }
}
