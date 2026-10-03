package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.InventoryItemResponse;
import com.grantinofarms.poultry.dto.InventoryMovementResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class InventoryRepository {
    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String farmId() {
        return jdbc.queryForObject(
                "SELECT id FROM farms WHERE status = 'ACTIVE' ORDER BY created_at LIMIT 1",
                String.class
        );
    }

    public void insertItem(String id, String farmId, String name, String category,
                           String unit, BigDecimal reorderLevel, String now) {
        jdbc.update("""
                INSERT INTO inventory_items
                    (id, farm_id, name, category, unit, reorder_level, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, id, farmId, name, category, unit, reorderLevel, now, now);
    }

    public InventoryItemResponse findItem(String id, String farmId) {
        return jdbc.query("""
                SELECT i.id, i.farm_id, i.name, i.category, i.unit, i.reorder_level, i.status,
                       COALESCE(SUM(
                           CASE
                               WHEN m.movement_type IN ('RECEIVE', 'ADJUST_IN') THEN m.quantity
                               WHEN m.movement_type IN ('ISSUE', 'ADJUST_OUT', 'WASTE') THEN -m.quantity
                               ELSE 0
                           END
                       ), 0) AS quantity_on_hand
                FROM inventory_items i
                LEFT JOIN inventory_movements m ON m.inventory_item_id = i.id
                WHERE i.id = ? AND i.farm_id = ?
                GROUP BY i.id
                """, (rs, n) -> mapItem(rs), id, farmId).stream().findFirst().orElse(null);
    }

    public List<InventoryItemResponse> findItems(String farmId) {
        return jdbc.query("""
                SELECT i.id, i.farm_id, i.name, i.category, i.unit, i.reorder_level, i.status,
                       COALESCE(SUM(
                           CASE
                               WHEN m.movement_type IN ('RECEIVE', 'ADJUST_IN') THEN m.quantity
                               WHEN m.movement_type IN ('ISSUE', 'ADJUST_OUT', 'WASTE') THEN -m.quantity
                               ELSE 0
                           END
                       ), 0) AS quantity_on_hand
                FROM inventory_items i
                LEFT JOIN inventory_movements m ON m.inventory_item_id = i.id
                WHERE i.farm_id = ?
                GROUP BY i.id
                ORDER BY i.category, i.name
                """, (rs, n) -> mapItem(rs), farmId);
    }

    public InventoryItemResponse findActiveItem(String id, String farmId) {
        return jdbc.query("""
                SELECT i.id, i.farm_id, i.name, i.category, i.unit, i.reorder_level, i.status,
                       COALESCE(SUM(
                           CASE
                               WHEN m.movement_type IN ('RECEIVE', 'ADJUST_IN') THEN m.quantity
                               WHEN m.movement_type IN ('ISSUE', 'ADJUST_OUT', 'WASTE') THEN -m.quantity
                               ELSE 0
                           END
                       ), 0) AS quantity_on_hand
                FROM inventory_items i
                LEFT JOIN inventory_movements m ON m.inventory_item_id = i.id
                WHERE i.id = ? AND i.farm_id = ? AND i.status = 'ACTIVE'
                GROUP BY i.id
                """, (rs, n) -> mapItem(rs), id, farmId).stream().findFirst().orElse(null);
    }

    public void archiveItem(String id, String farmId, String now) {
        jdbc.update("""
                UPDATE inventory_items
                SET status = 'ARCHIVED', updated_at = ?
                WHERE id = ? AND farm_id = ? AND status = 'ACTIVE'
                """, now, id, farmId);
    }

    public void insertMovement(String id, String farmId, String itemId, LocalDate date,
                               String type, BigDecimal quantity, String reason, String source,
                               String batchId, String now) {
        jdbc.update("""
                INSERT INTO inventory_movements
                    (id, farm_id, inventory_item_id, movement_date, movement_type,
                     quantity, reason, source, batch_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, farmId, itemId, date.toString(), type, quantity, reason, source, batchId, now);
    }

    public List<InventoryMovementResponse> findMovements(String farmId, String itemId, LocalDate asOf) {
        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.inventory_item_id, i.name, i.category, m.movement_date,
                       m.movement_type, m.quantity, m.reason, m.source, m.batch_id, m.created_at
                FROM inventory_movements m
                JOIN inventory_items i ON i.id = m.inventory_item_id
                WHERE m.farm_id = ?
                """);
        if (itemId != null) sql.append(" AND m.inventory_item_id = ?");
        if (asOf != null) sql.append(" AND m.movement_date <= ?");
        sql.append(" ORDER BY m.movement_date DESC, m.created_at DESC");

        if (itemId != null && asOf != null) {
            return queryMovements(sql.toString(), farmId, itemId, asOf.toString());
        }
        if (itemId != null) {
            return queryMovements(sql.toString(), farmId, itemId);
        }
        if (asOf != null) {
            return queryMovements(sql.toString(), farmId, asOf.toString());
        }
        return queryMovements(sql.toString(), farmId);
    }

    private List<InventoryMovementResponse> queryMovements(String sql, Object... args) {
        return jdbc.query(sql, (rs, n) -> new InventoryMovementResponse(
                rs.getString("id"),
                rs.getString("inventory_item_id"),
                rs.getString("name"),
                rs.getString("category"),
                LocalDate.parse(rs.getString("movement_date")),
                rs.getString("movement_type"),
                rs.getBigDecimal("quantity"),
                rs.getString("reason"),
                rs.getString("source"),
                rs.getString("batch_id"),
                rs.getString("created_at")
        ), args);
    }

    public BigDecimal quantityOnHand(String itemId, String farmId) {
        BigDecimal quantity = jdbc.queryForObject("""
                SELECT COALESCE(SUM(
                    CASE
                        WHEN movement_type IN ('RECEIVE', 'ADJUST_IN') THEN quantity
                        WHEN movement_type IN ('ISSUE', 'ADJUST_OUT', 'WASTE') THEN -quantity
                        ELSE 0
                    END
                ), 0)
                FROM inventory_movements
                WHERE inventory_item_id = ? AND farm_id = ?
                """, BigDecimal.class, itemId, farmId);
        return quantity == null ? BigDecimal.ZERO : quantity;
    }

    private InventoryItemResponse mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new InventoryItemResponse(
                rs.getString("id"),
                rs.getString("farm_id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("unit"),
                rs.getBigDecimal("reorder_level"),
                rs.getString("status"),
                rs.getBigDecimal("quantity_on_hand")
        );
    }
}
