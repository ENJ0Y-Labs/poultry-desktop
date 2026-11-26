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

    public void insert(String id, String username, String email, String passwordHash, String fullName, String now) {
        jdbc.update("""
                INSERT INTO users (id, username, email, password_hash, full_name, role, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'OWNER', 'ACTIVE', ?, ?)
                """, id, username, email, passwordHash, fullName, now, now);
        jdbc.update("""
                INSERT INTO user_roles (user_id, role, created_at, updated_at, updated_by)
                VALUES (?, 'ADMINISTRATOR', ?, ?, NULL)
                """, id, now, now);
    }

    public Map<String, Object> findByIdentifier(String identifier) {
        try {
            return jdbc.queryForMap("""
                    SELECT u.id, u.username, u.email, u.password_hash, u.full_name,
                           r.role, u.status
                    FROM users u JOIN user_roles r ON r.user_id = u.id
                    WHERE u.username = ? COLLATE NOCASE OR u.email = ? COLLATE NOCASE
                    """, identifier, identifier);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> findByUsername(String username) {
        try {
            return jdbc.queryForMap("""
                    SELECT u.id, u.username, u.email, u.password_hash, u.full_name,
                           r.role, u.status
                    FROM users u JOIN user_roles r ON r.user_id = u.id
                    WHERE u.username = ? COLLATE NOCASE
                    """, username);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Map<String, Object> findById(String id) {
        try {
            return jdbc.queryForMap("""
                    SELECT u.id, u.username, u.email, u.password_hash, u.full_name,
                           r.role, u.status
                    FROM users u JOIN user_roles r ON r.user_id = u.id
                    WHERE u.id = ?
                    """, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void updateProfile(String id, String username, String email, String fullName, String now) {
        jdbc.update("UPDATE users SET username = ?, email = ?, full_name = ?, updated_at = ? WHERE id = ?",
                username, email, fullName, now, id);
    }

    public void updatePassword(String id, String passwordHash, String now) {
        jdbc.update("UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?", passwordHash, now, id);
    }
}
