package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.BackupSettingsRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
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
    private final BackupSettingsRepository backupSettingsRepository;

    public AttentionService(JdbcTemplate jdbc, FarmRepository farms, BackupSettingsRepository backupSettingsRepository) {
        this.jdbc = jdbc;
        this.farms = farms;
        this.backupSettingsRepository = backupSettingsRepository;
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
        addRecentMortalityIncreaseAlerts(result, farmId, date);
        addUnusualFeedConsumptionAlerts(result, farmId, date);
        addDecliningWeightAlerts(result, farmId, date);
        addFinancialTrendAlerts(result, farmId, date);
        addMissingDailyRecords(result, farmId, date);
        addVaccinationAlerts(result, farmId, date);
        addProductionDropAlerts(result, farmId, date);
        addSaleAlerts(result, farmId, date);
        addBackupAlert(result);
        decorateInsights(result, farmId, date);

        result.sort(Comparator
                .comparingInt((Map<String, Object> item) -> severityRank((String) item.get("severity")))
                .thenComparing(item -> String.valueOf(item.getOrDefault("batchCode", "")))
                .thenComparing(item -> String.valueOf(item.getOrDefault("item", "")))
                .thenComparing(item -> String.valueOf(item.get("type"))));

        return result;
    }

    private void addInventoryAlerts(List<Map<String, Object>> result, String farmId) {
        jdbc.query("""
                SELECT i.id, i.name, i.unit, i.reorder_level,
                       COALESCE(SUM(
                           CASE
                               WHEN m.movement_type IN ('RECEIVE','ADJUST_IN') THEN m.quantity
                               WHEN m.movement_type IN ('ISSUE','ADJUST_OUT','WASTE') THEN -m.quantity
                               ELSE 0
                           END
                       ), 0) AS stock,
                       COUNT(m.id) AS movement_count
                FROM inventory_items i
                LEFT JOIN inventory_movements m ON m.inventory_item_id = i.id
                WHERE i.farm_id = ? AND i.status = 'ACTIVE'
                GROUP BY i.id, i.name, i.unit, i.reorder_level
                HAVING i.reorder_level > 0
                   AND COALESCE(SUM(
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
                                    "itemId", rs.getString("id"),
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
                                 WHERE p.feed_type_id = f.id AND p.purchase_date <= ?), 0) AS purchased,
                       COALESCE((SELECT SUM(u.quantity_milli)
                                 FROM feed_usage u
                                 WHERE u.feed_type_id = f.id AND u.usage_date <= ?), 0) AS used,
                       COALESCE((SELECT SUM(u.quantity_milli)
                                 FROM feed_usage u
                                 WHERE u.feed_type_id = f.id
                                   AND u.usage_date > date(?, '-7 day')
                                   AND u.usage_date <= ?), 0) AS used_last_7_days
                FROM feed_types f
                WHERE f.farm_id = ? AND f.status = 'ACTIVE'
                """,
                rs -> {
                    long purchased = rs.getLong("purchased");
                    long used = rs.getLong("used");
                    long remaining = purchased - used;
                    long usedLast7 = rs.getLong("used_last_7_days");
                    // A feed type with no purchase or usage history is configured, not stocked.
                    // Do not create an empty-stock alert until records show it is expected to be used.
                    if (purchased == 0 && used == 0) return;
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
                                feedDetails(rs.getString("id"), rs.getString("name"), remaining, rs.getString("unit"), daysCover)
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
                  -- A day with recorded operational activity is not "missing" just because
                  -- its optional daily-sheet header has not yet been created.
                  AND NOT EXISTS (
                      SELECT 1 FROM bird_population_events e
                      WHERE e.batch_id = b.id AND e.event_date = ?
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM feed_usage u
                      WHERE u.batch_id = b.id AND u.usage_date = ?
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM egg_collections ec
                      WHERE ec.batch_id = b.id AND ec.record_date = ?
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM weight_records wr
                      WHERE wr.batch_id = b.id AND wr.record_date = ?
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM health_records h
                      WHERE h.batch_id = b.id AND h.record_date = ?
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM drug_records dr
                      WHERE dr.batch_id = b.id AND dr.record_date = ?
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM vaccination_records vr
                      WHERE vr.batch_id = b.id AND vr.record_date = ?
                  )
                """,
                (RowCallbackHandler) rs -> {
                    result.add(alert(
                        "MISSING_DAILY_RECORD",
                        "WARNING",
                        "Missing daily record",
                        "No daily record has been entered for " + rs.getString("code") + " for " + date + ".",
                        Map.of(
                                "batchId", rs.getString("id"),
                                "batchCode", rs.getString("code"),
                                "date", date
                        )
                    ));
                }, farmId, date.toString(), date.toString(), date.toString(), date.toString(), date.toString(), date.toString(), date.toString(), date.toString());
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
                (RowCallbackHandler) rs -> result.add(alert(
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
                           SELECT SUM(e.good_eggs + e.cracked_eggs)
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

    private void addRecentMortalityIncreaseAlerts(List<Map<String, Object>> result, String farmId, LocalDate date) {
        jdbc.query("""
                SELECT b.id, b.code, b.initial_bird_count,
                       COALESCE(SUM(CASE WHEN e.event_type = 'MORTALITY'
                           AND e.event_date > date(?, '-7 day') AND e.event_date <= ?
                           THEN e.quantity ELSE 0 END), 0) AS recent_mortality
                FROM batches b
                LEFT JOIN bird_population_events e ON e.batch_id = b.id
                WHERE b.farm_id = ? AND b.status = 'ACTIVE' AND b.placement_date <= ?
                GROUP BY b.id, b.code, b.initial_bird_count
                """,
                rs -> {
                    int initial = rs.getInt("initial_bird_count");
                    long recent = rs.getLong("recent_mortality");
                    double percent = initial <= 0 ? 0 : recent * 100.0 / initial;
                    if (percent >= 2.0) {
                        result.add(alert(
                                "MORTALITY_INCREASE", percent >= 5.0 ? "CRITICAL" : "WARNING",
                                "Recent mortality increase",
                                rs.getString("code") + " recorded " + recent + " deaths in the last 7 days (" + formatPercent(percent) + " of its starting population).",
                                Map.of("batchId", rs.getString("id"), "batchCode", rs.getString("code"),
                                        "mortalityLast7Days", recent, "mortalityPercent", percent)
                        ));
                    }
                }, date.toString(), date.toString(), farmId, date.toString());
    }

    private void addUnusualFeedConsumptionAlerts(List<Map<String, Object>> result, String farmId, LocalDate date) {
        jdbc.query("""
                SELECT b.id AS batch_id, b.code, f.id AS feed_type_id, f.name, f.unit,
                       COALESCE(SUM(CASE WHEN u.usage_date > date(?, '-7 day') AND u.usage_date <= ?
                           THEN u.quantity_milli ELSE 0 END), 0) AS recent,
                       COALESCE(SUM(CASE WHEN u.usage_date > date(?, '-14 day') AND u.usage_date <= date(?, '-7 day')
                           THEN u.quantity_milli ELSE 0 END), 0) AS previous
                FROM batches b
                JOIN feed_usage u ON u.batch_id = b.id
                JOIN feed_types f ON f.id = u.feed_type_id
                WHERE b.farm_id = ? AND b.status = 'ACTIVE' AND b.placement_date <= ?
                GROUP BY b.id, b.code, f.id, f.name, f.unit
                HAVING recent > 0 AND previous > 0
                """,
                rs -> {
                    long recent = rs.getLong("recent");
                    long previous = rs.getLong("previous");
                    double increase = (recent - previous) * 100.0 / previous;
                    if (increase >= 25.0) {
                        result.add(alert(
                                "UNUSUAL_FEED_CONSUMPTION", increase >= 50.0 ? "CRITICAL" : "WARNING",
                                "Unusual feed consumption",
                                rs.getString("code") + " used " + formatPercent(increase) + " more " + rs.getString("name")
                                        + " in the latest 7 days than in the preceding 7 days.",
                                Map.of("batchId", rs.getString("batch_id"), "batchCode", rs.getString("code"),
                                        "feedTypeId", rs.getString("feed_type_id"), "item", rs.getString("name"),
                                        "unit", rs.getString("unit"), "recentQuantityMilli", recent,
                                        "previousQuantityMilli", previous, "increasePercent", increase)
                        ));
                    }
                }, date.toString(), date.toString(), date.toString(), date.toString(), farmId, date.toString());
    }

    private void addDecliningWeightAlerts(List<Map<String, Object>> result, String farmId, LocalDate date) {
        jdbc.query("""
                SELECT b.id AS batch_id, b.code, wr.record_date,
                       SUM(wr.total_weight_grams) * 1.0 / SUM(wr.sample_quantity) AS average_grams,
                       SUM(wr.sample_quantity) AS sampled_birds
                FROM batches b
                JOIN weight_records wr ON wr.batch_id = b.id
                WHERE b.farm_id = ? AND b.status = 'ACTIVE' AND b.batch_type IN ('BROILER','TURKEY')
                  AND wr.record_date <= ?
                GROUP BY b.id, b.code, wr.record_date
                ORDER BY b.id, wr.record_date DESC
                """,
                rs -> {
                    // Collecting the latest two measurements is handled below to avoid treating
                    // an absent measurement as a zero-weight observation.
                }, farmId, date.toString());

        Map<String, List<Map<String, Object>>> measurements = new LinkedHashMap<>();
        jdbc.query("""
                SELECT b.id AS batch_id, b.code, wr.record_date,
                       SUM(wr.total_weight_grams) * 1.0 / SUM(wr.sample_quantity) AS average_grams,
                       SUM(wr.sample_quantity) AS sampled_birds
                FROM batches b JOIN weight_records wr ON wr.batch_id = b.id
                WHERE b.farm_id = ? AND b.status = 'ACTIVE' AND b.batch_type IN ('BROILER','TURKEY')
                  AND wr.record_date <= ?
                GROUP BY b.id, b.code, wr.record_date
                ORDER BY b.id, wr.record_date DESC
                """, rs -> {
            String batchId = rs.getString("batch_id");
            List<Map<String, Object>> rows = measurements.computeIfAbsent(batchId, ignored -> new ArrayList<>());
            if (rows.size() < 2) {
                rows.add(Map.of("code", rs.getString("code"), "date", rs.getString("record_date"),
                        "averageGrams", rs.getDouble("average_grams")));
            }
        }, farmId, date.toString());

        measurements.forEach((batchId, rows) -> {
            if (rows.size() < 2) return;
            double latest = (double) rows.get(0).get("averageGrams");
            double previous = (double) rows.get(1).get("averageGrams");
            if (previous <= 0) return;
            double decline = (previous - latest) * 100.0 / previous;
            if (decline >= 10.0) {
                result.add(alert(
                        "DECLINING_WEIGHT", decline >= 20.0 ? "CRITICAL" : "WARNING",
                        "Declining average weight",
                        rows.get(0).get("code") + " has a latest recorded average weight " + formatPercent(decline)
                                + " below its preceding recorded measurement.",
                        Map.of("batchId", batchId, "batchCode", rows.get(0).get("code"),
                                "latestWeightGrams", latest, "previousWeightGrams", previous,
                                "latestWeightDate", rows.get(0).get("date"),
                                "previousWeightDate", rows.get(1).get("date"), "declinePercent", decline)
                ));
            }
        });
    }

    private void addFinancialTrendAlerts(List<Map<String, Object>> result, String farmId, LocalDate date) {
        Map<String, Long> expenses = jdbc.queryForMap("""
                SELECT
                  COALESCE(SUM(CASE WHEN occurred_date > date(?, '-7 day') AND occurred_date <= ? THEN amount_minor ELSE 0 END), 0) AS recent,
                  COALESCE(SUM(CASE WHEN occurred_date > date(?, '-14 day') AND occurred_date <= date(?, '-7 day') THEN amount_minor ELSE 0 END), 0) AS previous
                FROM expenses WHERE farm_id = ? AND occurred_date <= ?
                """, date.toString(), date.toString(), date.toString(), date.toString(), farmId, date.toString())
                .entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> ((Number) e.getValue()).longValue()));
        long recentExpenses = expenses.getOrDefault("recent", 0L);
        long previousExpenses = expenses.getOrDefault("previous", 0L);
        if (previousExpenses > 0) {
            double increase = (recentExpenses - previousExpenses) * 100.0 / previousExpenses;
            if (increase >= 25.0) {
                result.add(alert("RISING_EXPENSES", increase >= 50.0 ? "CRITICAL" : "WARNING",
                        "Farm expenses are rising",
                        "Recorded expenses are " + formatPercent(increase) + " higher in the latest 7 days than in the preceding 7 days.",
                        Map.of("recentAmountMinor", recentExpenses, "previousAmountMinor", previousExpenses, "increasePercent", increase)));
            }
        }

        Map<String, Object> revenue = jdbc.queryForMap("""
                SELECT
                  COALESCE(SUM(CASE WHEN sale_date > date(?, '-7 day') AND sale_date <= ? THEN total_amount_minor ELSE 0 END), 0) AS recent,
                  COALESCE(SUM(CASE WHEN sale_date > date(?, '-14 day') AND sale_date <= date(?, '-7 day') THEN total_amount_minor ELSE 0 END), 0) AS previous
                FROM sales WHERE farm_id = ? AND sale_date <= ?
                """, date.toString(), date.toString(), date.toString(), date.toString(), farmId, date.toString());
        long recentRevenue = ((Number) revenue.get("recent")).longValue();
        long previousRevenue = ((Number) revenue.get("previous")).longValue();
        if (previousRevenue > 0) {
            double decrease = (previousRevenue - recentRevenue) * 100.0 / previousRevenue;
            if (decrease >= 25.0) {
                result.add(alert("FALLING_REVENUE", decrease >= 50.0 ? "CRITICAL" : "WARNING",
                        "Farm revenue is falling",
                        "Recorded sales revenue is down " + formatPercent(decrease) + " in the latest 7 days compared with the preceding 7 days.",
                        Map.of("recentAmountMinor", recentRevenue, "previousAmountMinor", previousRevenue, "decreasePercent", decrease)));
            }
        }
    }

    private void decorateInsights(List<Map<String, Object>> result, String farmId, LocalDate date) {
        Map<String, String[]> guidance = Map.ofEntries(
                Map.entry("LOW_FEED_STOCK", new String[]{"Feed stock cover is 3 days or less, or the balance is depleted.", "Review feed stock and arrange a purchase if required."}),
                Map.entry("LOW_INVENTORY", new String[]{"Active stock is at or below its configured reorder level.", "Review the item balance and replenish it if needed."}),
                Map.entry("INSUFFICIENT_INVENTORY", new String[]{"Active stock is zero or below.", "Review stock movements and record a receipt if stock is available."}),
                Map.entry("HIGH_MORTALITY", new String[]{"Cumulative mortality is at least 5% of the initial population.", "Inspect the batch records and consult the farm's qualified animal-health professional."}),
                Map.entry("MORTALITY_INCREASE", new String[]{"Mortality in the last 7 days is at least 2% of initial birds.", "Review recent mortality records and escalate unusual losses to the farm's qualified animal-health professional."}),
                Map.entry("MISSING_DAILY_RECORD", new String[]{"No daily record or other supported daily activity exists for the selected date.", "Complete the missing daily record if the work was performed."}),
                Map.entry("VACCINATION_DUE", new String[]{"No vaccination is recorded in the previous 28 days for a batch at least 14 days old.", "Review the vaccination schedule and consult the farm's qualified animal-health professional."}),
                Map.entry("UNUSUAL_PRODUCTION_DROP", new String[]{"Good-egg production is at least 20% below the previous 7-day period.", "Review collection records, flock condition and recent operational changes."}),
                Map.entry("UNUSUAL_FEED_CONSUMPTION", new String[]{"Feed usage is at least 25% above the preceding 7-day period for the same batch and feed type.", "Check the feed entries, bird count, spillage and storage conditions."}),
                Map.entry("DECLINING_WEIGHT", new String[]{"The latest recorded average weight is at least 10% below the preceding measurement.", "Verify sample size and weighing procedure, then review feed and health records."}),
                Map.entry("RISING_EXPENSES", new String[]{"Recorded farm expenses are at least 25% above the preceding 7-day period.", "Review recent expenses and confirm unusual or duplicate entries."}),
                Map.entry("FALLING_REVENUE", new String[]{"Recorded farm sales revenue is at least 25% below the preceding 7-day period.", "Review sales activity, collections, pricing and whether all sales were recorded."}),
                Map.entry("BATCH_NEARING_SALE", new String[]{"Active broiler batch has reached the 35-day sale-planning threshold.", "Review current weight, market readiness and the farm's sale plan."}),
                Map.entry("BACKUP_OVERDUE", new String[]{"Automatic backup is enabled but no recent backup is available within 7 days.", "Create a fresh backup and verify the configured backup destination."})
        );
        for (Map<String, Object> item : result) {
            String type = String.valueOf(item.get("type"));
            String[] copy = guidance.getOrDefault(type, new String[]{"The current backend rule threshold has been reached.", "Review the related farm record."});
            item.put("detectedAt", date);
            item.put("affectedMetric", metricFor(type));
            item.put("ruleId", type);
            item.put("ruleDescription", copy[0]);
            item.put("thresholdContext", thresholdFor(type));
            item.put("suggestedAction", copy[1]);
            Object batchId = item.get("batchId");
            if (batchId != null) {
                jdbc.query("""
                        SELECT b.house_id, h.name AS house_name, h.code AS house_code
                        FROM batches b JOIN houses h ON h.id = b.house_id
                        WHERE b.id = ? AND b.farm_id = ?
                        """, rs -> {
                    if (rs.next()) {
                        item.put("houseId", rs.getString("house_id"));
                        item.put("houseName", rs.getString("house_name"));
                        item.put("houseCode", rs.getString("house_code"));
                    }
                }, String.valueOf(batchId), farmId);
            }
        }
    }

    private String metricFor(String type) {
        return switch (type) {
            case "LOW_FEED_STOCK", "UNUSUAL_FEED_CONSUMPTION" -> "Feed";
            case "LOW_INVENTORY", "INSUFFICIENT_INVENTORY" -> "Inventory";
            case "HIGH_MORTALITY", "MORTALITY_INCREASE" -> "Mortality";
            case "UNUSUAL_PRODUCTION_DROP" -> "Egg production";
            case "DECLINING_WEIGHT" -> "Average live weight";
            case "RISING_EXPENSES" -> "Expenses";
            case "FALLING_REVENUE" -> "Sales revenue";
            case "VACCINATION_DUE" -> "Vaccination";
            case "MISSING_DAILY_RECORD" -> "Daily records";
            case "BATCH_NEARING_SALE" -> "Batch age";
            default -> "Farm operations";
        };
    }

    private String thresholdFor(String type) {
        return switch (type) {
            case "LOW_FEED_STOCK" -> "Critical at zero stock; warning at 3 days of cover or less.";
            case "LOW_INVENTORY" -> "Stock at or below the configured positive reorder level.";
            case "INSUFFICIENT_INVENTORY" -> "Calculated active stock is zero or negative.";
            case "HIGH_MORTALITY" -> "Warning at 5%; critical at 10% of initial birds.";
            case "MORTALITY_INCREASE" -> "7-day mortality >= 2% of initial birds; critical at 5%.";
            case "UNUSUAL_FEED_CONSUMPTION" -> "Latest 7 days >= 25% above preceding 7 days; critical at 50%.";
            case "UNUSUAL_PRODUCTION_DROP" -> "Latest 7 days >= 20% below preceding 7 days; critical at 30%.";
            case "DECLINING_WEIGHT" -> "Latest average weight >= 10% below preceding recorded measurement; critical at 20%.";
            case "RISING_EXPENSES" -> "Latest 7 days >= 25% above preceding 7 days; critical at 50%.";
            case "FALLING_REVENUE" -> "Latest 7 days >= 25% below preceding 7 days; critical at 50%.";
            case "VACCINATION_DUE" -> "Batch age >= 14 days and no vaccination in the previous 28 days.";
            case "MISSING_DAILY_RECORD" -> "No daily record or supported operational activity for the as-of date.";
            case "BATCH_NEARING_SALE" -> "Warning at 35 days; target 42 days; critical at 49 days.";
            case "BACKUP_OVERDUE" -> "Automatic backup is at least 7 days overdue.";
            default -> "See the backend rule definition.";
        };
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
        var farm = farms.findActive();
        if (farm == null) return;
        var config = backupSettingsRepository.find(farm.id());
        if (config == null || !config.enabled()) return;

        String configuredDirectory = config.directory();
        Path directory;
        try {
            directory = configuredDirectory == null || configuredDirectory.isBlank()
                    ? null : Path.of(configuredDirectory).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            addBackupOverdue(result, "The automatic backup directory is invalid.");
            return;
        }

        if (directory == null || !Files.isDirectory(directory)) {
            addBackupOverdue(result, "The automatic backup directory is unavailable.");
            return;
        }

        try (var stream = Files.list(directory)) {
            Optional<Path> latest = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> BACKUP_NAME.matcher(path.getFileName().toString()).matches())
                    .max(Comparator.comparing(path -> {
                        try { return Files.getLastModifiedTime(path).toInstant(); }
                        catch (Exception e) { return java.time.Instant.EPOCH; }
                    }));
            if (latest.isEmpty()) {
                addBackupOverdue(result, "No automatic database backup has been created yet.");
                return;
            }
            long ageDays = java.time.Duration.between(
                    Files.getLastModifiedTime(latest.get()).toInstant(), java.time.Instant.now()).toDays();
            if (ageDays >= BACKUP_OVERDUE_DAYS) {
                addBackupOverdue(result, "The latest automatic backup is " + ageDays + " days old.");
            }
        } catch (Exception e) {
            addBackupOverdue(result, "The automatic backup directory could not be checked.");
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

    private Map<String, Object> feedDetails(String feedTypeId, String item, long stock, String unit, double daysCover) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("feedTypeId", feedTypeId);
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
