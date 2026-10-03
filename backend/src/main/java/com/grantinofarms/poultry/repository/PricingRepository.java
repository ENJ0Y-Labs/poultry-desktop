package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.PricingSettingsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public class PricingRepository {
    private final JdbcTemplate jdbc;

    public PricingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PricingSettingsResponse settings(String farmId) {
        return jdbc.query("""
                SELECT target_margin_percent, working_margin_percent
                FROM farm_settings WHERE farm_id = ?
                """,
                (rs, rowNum) -> new PricingSettingsResponse(
                        rs.getBigDecimal("target_margin_percent"),
                        rs.getBigDecimal("working_margin_percent")
                ), farmId).stream().findFirst()
                .orElse(new PricingSettingsResponse(null, null));
    }

    public void updateTarget(String farmId, BigDecimal target, String now) {
        jdbc.update("""
                UPDATE farm_settings
                SET target_margin_percent = ?, updated_at = ?
                WHERE farm_id = ?
                """, target, now, farmId);
    }

    public void updateWorking(String farmId, BigDecimal working, String now) {
        jdbc.update("""
                UPDATE farm_settings
                SET working_margin_percent = ?, updated_at = ?
                WHERE farm_id = ?
                """, working, now, farmId);
    }

    public void insertHistory(String id, String farmId, String now, String changeType,
                              BigDecimal oldTarget, BigDecimal newTarget,
                              BigDecimal oldWorking, BigDecimal newWorking,
                              String reason, String referenceType, String referenceId) {
        jdbc.update("""
                INSERT INTO pricing_margin_history
                    (id, farm_id, changed_at, change_type,
                     old_target_margin_percent, new_target_margin_percent,
                     old_working_margin_percent, new_working_margin_percent,
                     reason, reference_type, reference_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, farmId, now, changeType,
                oldTarget, newTarget, oldWorking, newWorking,
                reason, referenceType, referenceId);
    }

    public void insertAuditHistory(String id, String farmId, String now, String changeType,
                                   BigDecimal oldTarget, BigDecimal newTarget,
                                   BigDecimal oldWorking, BigDecimal newWorking,
                                   String reason, String referenceType, String referenceId) {
        insertHistory(id, farmId, now, changeType, oldTarget, newTarget,
                oldWorking, newWorking, reason, referenceType, referenceId);
    }
}
