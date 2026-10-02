package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class HealthManagementRepository {
    private final JdbcTemplate jdbc;

    public HealthManagementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String farmIdForBatch(String batchId) {
        List<String> ids = jdbc.query(
                "SELECT farm_id FROM batches WHERE id = ?",
                (rs, n) -> rs.getString("farm_id"),
                batchId
        );
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public String batchStatus(String batchId) {
        List<String> statuses = jdbc.query(
                "SELECT status FROM batches WHERE id = ?",
                (rs, n) -> rs.getString("status"),
                batchId
        );
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    public LocalDate placementDate(String batchId) {
        return LocalDate.parse(jdbc.queryForObject(
                "SELECT placement_date FROM batches WHERE id = ?", String.class, batchId));
    }

    public void insertHealth(String id, String batchId, LocalDate date, String conditionProblem,
                             String description, String action, String now) {
        jdbc.update("""
                INSERT INTO health_records
                    (id, batch_id, record_date, condition_problem, description, action, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, batchId, date.toString(), conditionProblem, description, action, now, now);
    }

    public List<HealthRecordResponse> findHealth(String batchId) {
        return jdbc.query("""
                SELECT id, batch_id, record_date, condition_problem, description, action
                FROM health_records
                WHERE batch_id = ?
                ORDER BY record_date DESC, created_at DESC
                """,
                (rs, n) -> new HealthRecordResponse(
                        rs.getString("id"),
                        rs.getString("batch_id"),
                        LocalDate.parse(rs.getString("record_date")),
                        rs.getString("condition_problem"),
                        rs.getString("description"),
                        rs.getString("action")
                ), batchId);
    }

    public void insertDrug(String id, String batchId, LocalDate date, String drug,
                           int quantity, long costMinor, String reason, String now) {
        jdbc.update("""
                INSERT INTO drug_records
                    (id, batch_id, record_date, drug, quantity, cost_minor, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, batchId, date.toString(), drug, quantity, costMinor, reason, now);
    }

    public List<DrugRecordResponse> findDrugs(String batchId) {
        return jdbc.query("""
                SELECT id, batch_id, record_date, drug, quantity, cost_minor, reason
                FROM drug_records
                WHERE batch_id = ?
                ORDER BY record_date DESC, created_at DESC
                """,
                (rs, n) -> new DrugRecordResponse(
                        rs.getString("id"),
                        rs.getString("batch_id"),
                        LocalDate.parse(rs.getString("record_date")),
                        rs.getString("drug"),
                        rs.getInt("quantity"),
                        rs.getLong("cost_minor"),
                        rs.getString("reason")
                ), batchId);
    }

    public void insertExpense(String id, String farmId, long amountMinor, LocalDate date,
                              String referenceId, String description, String now) {
        jdbc.update("""
                INSERT INTO expenses
                    (id, farm_id, category, amount_minor, occurred_date,
                     reference_type, reference_id, description, created_at)
                VALUES (?, ?, 'DRUGS', ?, ?, 'DRUG_RECORD', ?, ?, ?)
                """, id, farmId, amountMinor, date.toString(), referenceId, description, now);
    }

    public void insertVaccination(String id, String batchId, LocalDate date, String vaccine,
                                  String dose, int quantity, String notes, String now) {
        jdbc.update("""
                INSERT INTO vaccination_records
                    (id, batch_id, record_date, vaccine, dose, quantity, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, batchId, date.toString(), vaccine, dose, quantity, notes, now);
    }

    public List<VaccinationRecordResponse> findVaccinations(String batchId) {
        return jdbc.query("""
                SELECT id, batch_id, record_date, vaccine, dose, quantity, notes
                FROM vaccination_records
                WHERE batch_id = ?
                ORDER BY record_date DESC, created_at DESC
                """,
                (rs, n) -> new VaccinationRecordResponse(
                        rs.getString("id"),
                        rs.getString("batch_id"),
                        LocalDate.parse(rs.getString("record_date")),
                        rs.getString("vaccine"),
                        rs.getString("dose"),
                        rs.getInt("quantity"),
                        rs.getString("notes")
                ), batchId);
    }
}
