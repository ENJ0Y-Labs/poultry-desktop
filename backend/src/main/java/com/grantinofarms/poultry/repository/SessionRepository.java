package com.grantinofarms.poultry.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class SessionRepository {
    private final JdbcTemplate jdbc;

    public SessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(String id, String userId, String tokenHash, String createdAt, String expiresAt) {
        jdbc.update("""
                INSERT INTO user_sessions
                    (id, user_id, token_hash, created_at, expires_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, userId, tokenHash, createdAt, expiresAt, createdAt);
    }

    public Map<String, Object> findUserByTokenHash(String tokenHash, String now) {
        return jdbc.query("""
                SELECT u.id, u.email, u.full_name, u.role, u.status
                FROM user_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.expires_at > ? AND u.status = 'ACTIVE'
                """, (rs, n) -> Map.<String, Object>of(
                "id", rs.getString("id"),
                "email", rs.getString("email"),
                "fullName", rs.getString("full_name"),
                "role", rs.getString("role"),
                "status", rs.getString("status")
        ), tokenHash, now).stream().findFirst().orElse(null);
    }

    public void touch(String tokenHash, String now) {
        jdbc.update("UPDATE user_sessions SET last_seen_at = ? WHERE token_hash = ?", now, tokenHash);
    }

    public void delete(String tokenHash) {
        jdbc.update("DELETE FROM user_sessions WHERE token_hash = ?", tokenHash);
    }

    public void purgeExpired(String now) {
        jdbc.update("DELETE FROM user_sessions WHERE expires_at <= ?", now);
    }
}
