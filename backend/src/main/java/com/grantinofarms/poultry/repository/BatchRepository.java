package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.BatchResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class BatchRepository {
    private final JdbcTemplate jdbc;

    public BatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BatchResponse findById(String id) {
        return jdbc.query("""
                SELECT id, farm_id, house_id, supplier_id, code, batch_type,
                       placement_date, initial_bird_count, original_purchase_cost_minor,
                       status, created_at, updated_at
                FROM batches
                WHERE id = ?
                """, (rs, rowNum) -> map(rs), id).stream().findFirst().orElse(null);
    }

    public List<BatchResponse> findByFarm(String farmId) {
        return jdbc.query("""
                SELECT id, farm_id, house_id, supplier_id, code, batch_type,
                       placement_date, initial_bird_count, original_purchase_cost_minor,
                       status, created_at, updated_at
                FROM batches
                WHERE farm_id = ?
                ORDER BY placement_date DESC, code DESC
                """, (rs, rowNum) -> map(rs), farmId);
    }

    public boolean houseBelongsToFarm(String houseId, String farmId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM houses WHERE id = ? AND farm_id = ? AND status = 'ACTIVE'",
                Integer.class, houseId, farmId
        );
        return count != null && count == 1;
    }

    public boolean supplierBelongsToFarm(String supplierId, String farmId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM suppliers WHERE id = ? AND farm_id = ? AND status = 'ACTIVE'",
                Integer.class, supplierId, farmId
        );
        return count != null && count == 1;
    }

    public boolean belongsToFarm(String batchId, String farmId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM batches WHERE id = ? AND farm_id = ?",
                Integer.class, batchId, farmId
        );
        return count != null && count == 1;
    }

    public void insert(String id, String farmId, String houseId, String supplierId,
                       String code, String type, LocalDate placementDate,
                       int initialBirdCount, Long purchaseCostMinor, String now) {
        jdbc.update("""
                INSERT INTO batches
                    (id, farm_id, house_id, supplier_id, code, batch_type,
                     placement_date, initial_bird_count, original_purchase_cost_minor,
                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                id, farmId, houseId, supplierId, code, type, placementDate.toString(),
                initialBirdCount, purchaseCostMinor, now, now);
    }

    public void updateStatus(String id, String status, String now) {
        jdbc.update("""
                UPDATE batches
                SET status = ?, updated_at = ?
                WHERE id = ?
                """, status, now, id);
    }

    public void ensureSequence(String type, int year) {
        jdbc.update("""
                INSERT OR IGNORE INTO batch_code_sequences
                    (batch_type, sequence_year, next_number)
                VALUES (?, ?, 1)
                """, type, year);
    }

    public int allocateSequenceNumber(String type, int year) {
        int updated = jdbc.update("""
                UPDATE batch_code_sequences
                SET next_number = next_number + 1
                WHERE batch_type = ? AND sequence_year = ?
                """, type, year);
        if (updated != 1) {
            throw new IllegalStateException("Unable to allocate batch code sequence.");
        }

        Integer allocated = jdbc.queryForObject("""
                SELECT next_number - 1
                FROM batch_code_sequences
                WHERE batch_type = ? AND sequence_year = ?
                """, Integer.class, type, year);
        if (allocated == null) {
            throw new IllegalStateException("Batch code sequence disappeared during allocation.");
        }
        return allocated;
    }

    private BatchResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        String purchaseCost = rs.getString("original_purchase_cost_minor");
        return new BatchResponse(
                rs.getString("id"),
                rs.getString("farm_id"),
                rs.getString("house_id"),
                rs.getString("supplier_id"),
                rs.getString("code"),
                rs.getString("batch_type"),
                LocalDate.parse(rs.getString("placement_date")),
                rs.getInt("initial_bird_count"),
                purchaseCost == null ? null : rs.getLong("original_purchase_cost_minor"),
                rs.getString("status"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }
}
