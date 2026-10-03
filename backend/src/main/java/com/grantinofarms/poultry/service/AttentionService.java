package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.repository.FarmRepository;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AttentionService {
    private static final double HIGH_MORTALITY_PERCENT = 5.0;
    private static final double PRODUCTION_DROP_PERCENT = 20.0;
    private static final double CRITICAL_PRODUCTION_DROP_PERCENT = 30.0;
    private static final int FEED_DAYS_OF_COVER = 3;
    private static final int VACCINATION_INTERVAL_DAYS = 28;
    private static final int VACCINATION_MINIMUM_AGE_DAYS = 14;
    private static final int BROILER_SALE_TARGET_DAYS = 42;
    private static final int BROILER_SALE_WARNING_DAYS = 35;
    private static final int BROILER_SALE_CRITICAL_DAYS = 49;
    private static final int BACKUP_OVERDUE_DAYS = 7;
    private static final Pattern BACKUP_NAME =
            Pattern.compile("poultry-\\d{4}-\\d{2}-\\d{2}(?:-\\d{4}(?:-\\d+)?)?\\.db");

    private final JdbcTemplate jdbc;
    private final FarmRepository farms;
    private final Environment environment;

    public AttentionService(JdbcTemplate jdbc, FarmRepository farms, Environment environment) {
        this.jdbc = jdbc;
        this.farms = farms;
        this.environment = environment;
    }

    public List<Map<String, Object>> list(LocalDate asOf) {
        var farm = farms.findActive();
        if (farm == null) return List.of();

        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        String farmId = farm.id();
        List<Map<String, Object>> result = new ArrayList<>();

        addInventoryAlerts(result, farmId);
        addFeedAlerts(result, farmId, date);
        addMortalityAlerts(result, farmId, date);
        addMissingDailyRecords(result, farmId, date);
        addVaccinationAlerts(result, farmId, date);
        addProductionDropAlerts(result, farmId, date);
        addSaleAlerts(result, farmId, date);
        addBackupAlert(result);

        result.sort(Comparator
                .comparingInt((Map<String, Object> item) -> severityRank((String) item.get("severity")))
                .thenComparing(item -> String.valueOf(item.getOrDefault("batchCode", "")))
                .thenComparing(item -> String.valueOf(item.getOrDefault("item", "")))
                .thenComparing(item -> String.valueOf(item.get("type"))));

        return result;
    }

    private void addInventoryAlerts(List<Map<String, Object>> result, String farmId) {
        jdbc.query("""
                SELECT i.name, i.unit, i.reorder_level,
                       COALESCE(SUM(
                           CASE
                               WHEN m.movement_type IN ('RECEIVE','ADJUST_IN') THEN m.quantity
                               WHEN m.movement_type IN ('ISSUE','ADJUST_OUT','WASTE') THEN -m.quantity
                               ELSE 0
                           END
                       ), 0) AS stock
                FROM inventory_items i
                LEFT JOIN inventory_movements m ON m.inventory_item_id = i.id
                WHERE i.farm_id = ? AND i.status = 'ACTIVE'
                GROUP BY i.id, i.name, i.unit, i.reorder_level
                HAVING COALESCE(SUM(
                    CASE
                        WHEN m.movement_type IN ('RECEIVE','ADJUST_IN') THEN m.quantity
                        WHEN m.movement_type IN ('ISSUE','ADJUST_OUT','WASTE') THEN -m.quantity
                        ELSE 0
                    END
                ), 0) <= i.reorder_level
                """,
                rs -> {
                    BigDecimal stock = rs.getBigDecimal("stock");
                    boolean insufficient = stock.signum() <= 0;
                    result.add(alert(
                            insufficient ? "INSUFFICIENT_INVENTORY" : "LOW_INVENTORY",
                            insufficient ? "CRITICAL" : "WARNING",
                            insufficient ? "Insufficient inventory" : "Low inventory",
                            rs.getString("name") + (insufficient
                                    ? " is out of stock."
                                    : " is at or below its reorder level."),
                            Map.of(
                                    "item", rs.getString("name"),
                                    "stock", stock,
                                    "reorderLevel", rs.getBigDecimal("reorder_level"),
                                    "unit", rs.getString("unit")
                            )
                    ));
                }, farmId);
    }

    private void addFeedAlerts(List<Map<String, Object>> result, String farmId, LocalDate date) {
        jdbc.query("""
                SELECT f.id, f.name, f.unit,
                       COALESCE((SELECT SUM(p.quantity_milli)
                                 FROM feed_purchases p
                                 WHERE p.feed_type_id = f.id AND p.purchase_date <= ?), 0)
                       - COALESCE((SELECT SUM(u.quantity_milli)
                                   FROM feed_usage u
                                   WHERE u.feed_type_id = f.id AND u.usage_date <= ?), 0) AS remaining,
                       COALESCE((SELECT SUM(u.quantity_milli)
                                 FROM feed_usage u
                                 WHERE u.feed_type_id = f.id
                                   AND u.usage_date > date(?, '-7 day')
                                   AND u.usage_date <= ?), 0) AS used_last_7_days
                FROM feed_types f
                WHERE f.farm_id = ? AND f.status = 'ACTIVE'
                """,
                rs -> {
                    long remaining = rs.getLong("remaining");
                    long usedLast7 = rs.getLong("used_last_7_days");
                    double averageDaily = usedLast7 / 7.0;
                    double daysCover = averageDaily <= 0 ? (remaining <= 0 ? 0 : Double.POSITIVE_INFINITY)
                            : remaining / averageDaily;

                    if (remaining <= 0 || daysCover <= FEED_DAYS_OF_COVER) {
                        String message = remaining <= 0
                                ? rs.getString("name") + " has no feed remaining."
                                : rs.getString("name") + " has about " + formatDays(daysCover)
                                    + " days of stock at the recent usage rate.";
                        result.add(alert(
                                "LOW_FEED_STOCK",
                                remaining <= 0 ? "CRITICAL" : "WARNING",
                                "Low feed stock",
                                message,
                                feedDetails(rs.getString("name"), remaining, rs.getString("unit"), daysCover)
                        ));
                    }
                }, date.toString(), date.toString(), date.toString(), date.toString(), farmId);
    }

    private void addMortalityAlerts(List<Map<String, Object>> result, String farmId, LocalDate date) {
        jdbc.query("""
                SELECT b.id, b.code, b.initial_bird_count,
                       COALESCE(SUM(CASE WHEN e.event_type = 'MORTALITY' THEN e.quantity ELSE 0 END), 0) AS mortality
                FROM batches b
                LEFT JOIN bird_population_events e
                    ON e.batch_id = b.id AND e.event_date <= ?
                WHERE b.farm_id = ? AND b.status = 'ACTIVE'
                GROUP BY b.id, b.code, b.initial_bird_count
                """,
                rs -> {
                    int initial = rs.getInt("initial_bird_count");
                    long mortality = rs.getLong("mortality");
                    double percent = initial == 0 ? 0 : mortality * 100.0 / initial;
                    if (percent >= HIGH_MORTALITY_PERCENT) {
                        result.add(alert(
                                "HIGH_MORTALITY",
                                percent >= 10.0 ? "CRITICAL" : "WARNING",
                                "High mortality",
                                rs.getString("code") + " has " + formatPercent(percent)
                                        + " mortality (" + mortality + " birds).",
                                Map.of(
                                        "batchId", rs.getString("id"),
                                        "batchCode", rs.getString("code"),
                                        "mortality", mortality,
                                        "mortalityPercent", percent
                                )
                        ));
                    }
                }, date.toString(), farmId);
    }

    private void addMissingDailyRecords(List<Map<String, Object>> result, String farmId, LocalDate date) {
        jdbc.query("""
                SELECT b.id, b.code
                FROM batches b
                WHERE b.farm_id = ? AND b.status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1 FROM daily_records d
                      WHERE d.batch_id = b.id AND d.record_date = ?
                  )
                """,
                rs -> result.add(alert(
                        "MISSING_DAILY_RECORD",
                        "WARNING",
                        "Missing daily record",
                        "No daily record has been entered for " + rs.getString("code") + " for " + date + ".",
                        Map.of(
                                "batchId", rs.getString("id"),
                                "batchCode", rs.getString("code"),
                                "date", date
                        )
                )), farmId, date.toString());
    }

    private void addVaccinationAlerts(List<Map<String, Object>> result, String farmId, LocalDate date) {
        jdbc.query("""
                SELECT b.id, b.code, b.placement_date
                FROM batches b
                WHERE b.farm_id = ? AND b.status = 'ACTIVE'
                  AND julianday(?) - julianday(b.placement_date) >= ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM vaccination_records v
                      WHERE v.batch_id = b.id
                        AND v.record_date <= ?
                        AND v.record_date > date(?, '-' || ? || ' day')
                  )
                """,
                rs -> result.add(alert(
                        "VACCINATION_DUE",
                        "WARNING",
                        "Vaccination due",
                        rs.getString("code") + " has no vaccination recorded in the last "
                                + VACCINATION_INTERVAL_DAYS + " days.",
                        Map.of(
                                "batchId", rs.getString("id"),
                                "batchCode", rs.getString("code"),
                                "intervalDays", VACCINATION_INTERVAL_DAYS
                        )
                )), farmId, date.toString(), VACCINATION_MINIMUM_AGE_DAYS,
                date.toString(), date.toString(), VACCINATION_INTERVAL_DAYS);
    }

    private void addProductionDropAlerts(List<Map<String, Object>> result, String farmId, LocalDate date) {
        jdbc.query("""
                SELECT b.id, b.code,
                       COALESCE((
                           SELECT SUM(e.good_eggs)
                           FROM egg_collections e
                           WHERE e.batch_id = b.id
                             AND e.record_date > date(?, '-7 day')
                             AND e.record_date <= ?
                       ), 0) AS recent_eggs,
                       COALESCE((
                           SELECT SUM(e.good_eggs)
                           FROM egg_collections e
                           WHERE e.batch_id = b.id
                             AND e.record_date > date(?, '-14 day')
                             AND e.record_date <= date(?, '-7 day')
                       ), 0) AS previous_eggs
                FROM batches b
                WHERE b.farm_id = ? AND b.status = 'ACTIVE' AND b.batch_type = 'LAYER'
                """,
                rs -> {
                    long recent = rs.getLong("recent_eggs");
                    long previous = rs.getLong("previous_eggs");
                    if (previous <= 0) return;
                    double drop = (previous - recent) * 100.0 / previous;
                    if (drop >= PRODUCTION_DROP_PERCENT) {
                        result.add(alert(
                                "UNUSUAL_PRODUCTION_DROP",
                                drop >= CRITICAL_PRODUCTION_DROP_PERCENT ? "CRITICAL" : "WARNING",
                                "Unusual production drop",
                                rs.getString("code") + " egg production is down "
                                        + formatPercent(drop) + " compared with the previous 7 days.",
                                Map.of(
                                        "batchId", rs.getString("id"),
                                        "batchCode", rs.getString("code"),
                                        "recentEggs", recent,
                                        "previousEggs", previous,
                                        "dropPercent", drop
                                )
                        ));
                    }
                }, date.toString(), date.toString(), date.toString(), date.toString(), farmId);
    }

    private void addSaleAlerts(List<Map<String, Object>> result, String farmId, LocalDate date) {
        jdbc.query("""
                SELECT id, code, placement_date
                FROM batches
                WHERE farm_id = ? AND status = 'ACTIVE' AND batch_type = 'BROILER'
                """,
                rs -> {
                    LocalDate placement = LocalDate.parse(rs.getString("placement_date"));
                    long ageDays = java.time.temporal.ChronoUnit.DAYS.between(placement, date);
                    if (ageDays >= BROILER_SALE_WARNING_DAYS) {
                        String severity = ageDays >= BROILER_SALE_CRITICAL_DAYS ? "CRITICAL" : "WARNING";
                        result.add(alert(
                                "BATCH_NEARING_SALE",
                                severity,
                                "Batch nearing sale",
                                rs.getString("code") + " is " + ageDays
                                        + " days old. Target sale age is " + BROILER_SALE_TARGET_DAYS + " days.",
                                Map.of(
                                        "batchId", rs.getString("id"),
                                        "batchCode", rs.getString("code"),
                                        "ageDays", ageDays,
                                        "targetSaleAgeDays", BROILER_SALE_TARGET_DAYS
                                )
                        ));
                    }
                }, farmId);
    }

    private void addBackupAlert(List<Map<String, Object>> result) {
        String configured = environment.getProperty("poultry.database-path", "./data/poultry.db");
        if (configured == null || configured.isBlank() || ":memory:".equalsIgnoreCase(configured)) return;

        Path database = Path.of(configured).toAbsolutePath().normalize();
        Path directory = database.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            addBackupOverdue(result, "No backup has been created in the default database directory.");
            return;
        }

        try (var stream = Files.list(directory)) {
            Optional<Path> latest = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> BACKUP_NAME.matcher(path.getFileName().toString()).matches())
                    .max(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant();
                        } catch (Exception e) {
                            return java.time.Instant.EPOCH;
                        }
                    }));

            if (latest.isEmpty()) {
                addBackupOverdue(result, "No database backup has been created yet.");
                return;
            }

            long ageDays = java.time.Duration.between(
                    Files.getLastModifiedTime(latest.get()).toInstant(),
                    java.time.Instant.now()
            ).toDays();

            if (ageDays >= BACKUP_OVERDUE_DAYS) {
                addBackupOverdue(result, "The latest default backup is " + ageDays + " days old.");
            }
        } catch (Exception e) {
            addBackupOverdue(result, "The default backup directory could not be checked.");
        }
    }

    private void addBackupOverdue(List<Map<String, Object>> result, String message) {
        result.add(alert(
                "BACKUP_OVERDUE",
                "WARNING",
                "Backup overdue",
                message + " Create a fresh backup.",
                Map.of("overdueAfterDays", BACKUP_OVERDUE_DAYS)
        ));
    }

    private Map<String, Object> feedDetails(String item, long stock, String unit, double daysCover) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("item", item);
        details.put("stockQuantityMilli", stock);
        details.put("unit", unit);
        details.put("daysOfCover", daysCover == Double.POSITIVE_INFINITY ? null : daysCover);
        return details;
    }

    private Map<String, Object> alert(String type, String severity, String title, String message,
                                      Map<String, Object> details) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("severity", severity);
        item.put("title", title);
        item.put("message", message);
        item.putAll(details);
        return item;
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String formatDays(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
