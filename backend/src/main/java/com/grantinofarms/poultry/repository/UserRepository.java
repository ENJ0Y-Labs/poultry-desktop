package com.grantinofarms.poultry.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return count == null ? 0 : count;
    }

    public void insert(String id, String email, String passwordHash, String fullName, String now) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'OWNER', 'ACTIVE', ?, ?)
                """, id, email, passwordHash, fullName, now, now);
    }

    public Map<String, Object> findByEmail(String email) {
        try {
            return jdbc.queryForMap("""
                    SELECT id, email, password_hash, full_name, role, status
                    FROM users WHERE email = ? COLLATE NOCASE
                    """, email);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> findById(String id) {
        try {
            return jdbc.queryForMap("""
                    SELECT id, email, password_hash, full_name, role, status
                    FROM users WHERE id = ?
                    """, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void updateProfile(String id, String email, String fullName, String now) {
        jdbc.update("UPDATE users SET email = ?, full_name = ?, updated_at = ? WHERE id = ?",
                email, fullName, now, id);
    }

    public void updatePassword(String id, String passwordHash, String now) {
        jdbc.update("UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?",
                passwordHash, now, id);
    }
}
