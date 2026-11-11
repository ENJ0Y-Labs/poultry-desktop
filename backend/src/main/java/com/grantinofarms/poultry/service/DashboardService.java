package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.BatchFeedCostResponse;
import com.grantinofarms.poultry.dto.BroilerGrowthResponse;
import com.grantinofarms.poultry.dto.EggInventoryResponse;
import com.grantinofarms.poultry.dto.FarmResponse;
import com.grantinofarms.poultry.domain.ReportDateRange;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.FarmRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final FarmRepository farms;
    private final BirdPopulationService population;
    private final FeedService feed;
    private final EggManagementService eggs;
    private final BroilerProductionService broilers;
    private final AttentionService attention;

    public DashboardService(JdbcTemplate jdbc, FarmRepository farms, BirdPopulationService population,
                            FeedService feed, EggManagementService eggs, BroilerProductionService broilers,
                            AttentionService attention) {
        this.jdbc = jdbc; this.farms = farms; this.population = population;
        this.feed = feed; this.eggs = eggs; this.broilers = broilers; this.attention = attention;
    }

    public Map<String, Object> farm(LocalDate asOf) {
        FarmResponse farm = requireFarm();
        LocalDate date = asOf == null ? LocalDate.now(ZoneId.of(farm.timezone())) : asOf;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOf", date);
        out.put("farm", farm);
        out.put("activeBatches", scalar("SELECT COUNT(*) FROM batches WHERE farm_id = ? AND status = 'ACTIVE'", farm.id()));
        out.put("totalHouses", scalar("SELECT COUNT(*) FROM houses WHERE farm_id=? AND status='ACTIVE'", farm.id()));
        out.put("permanentHouses", scalar("SELECT COUNT(*) FROM houses WHERE farm_id=? AND status='ACTIVE' AND house_type='PERMANENT'", farm.id()));
        out.put("temporaryHouses", scalar("SELECT COUNT(*) FROM houses WHERE farm_id=? AND status='ACTIVE' AND house_type='TEMPORARY'", farm.id()));
        out.put("occupiedHouses", scalar("SELECT COUNT(*) FROM houses h WHERE h.farm_id=? AND h.status='ACTIVE' AND EXISTS (SELECT 1 FROM batches b WHERE b.house_id=h.id AND b.status='ACTIVE')", farm.id()));
        out.put("emptyHouses", scalar("SELECT COUNT(*) FROM houses h WHERE h.farm_id=? AND h.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM batches b WHERE b.house_id=h.id AND b.status='ACTIVE')", farm.id()));
        out.put("estimatedBatches", scalar("SELECT COUNT(*) FROM batches WHERE farm_id=? AND status='ACTIVE' AND population_count_status='ESTIMATED' AND placement_date<=?", farm.id(), date.toString()));
        out.put("verifiedBatches", scalar("SELECT COUNT(*) FROM batches WHERE farm_id=? AND status='ACTIVE' AND population_count_status='VERIFIED' AND placement_date<=?", farm.id(), date.toString()));
        out.put("estimatedBirds", populationTotalByConfidence(farm.id(), date, "ESTIMATED"));
        out.put("totalBirds", populationTotal(farm.id(), date, null));
        out.put("layerBirds", populationTotal(farm.id(), date, "LAYER"));
        out.put("broilerBirds", populationTotal(farm.id(), date, "BROILER"));
        out.put("turkeyBirds", populationTotal(farm.id(), date, "TURKEY"));
        out.put("mortality", scalar("SELECT COALESCE(SUM(e.quantity),0) FROM bird_population_events e JOIN batches b ON b.id=e.batch_id WHERE b.farm_id=? AND e.event_type='MORTALITY' AND e.event_date<=?", farm.id(), date.toString()));
        out.put("feed", feed.inventory(date));
        out.put("eggsGood", scalar("SELECT COALESCE(SUM(good_eggs),0) FROM egg_collections e JOIN batches b ON b.id=e.batch_id WHERE b.farm_id=? AND e.record_date<=?", farm.id(), date.toString()));
        out.put("revenueMinor", scalar("SELECT COALESCE(SUM(total_amount_minor),0) FROM sales WHERE farm_id=? AND sale_date<=?", farm.id(), date.toString()));
        out.put("expensesMinor", scalar("SELECT COALESCE(SUM(amount_minor),0) FROM expenses WHERE farm_id=? AND occurred_date<=?", farm.id(), date.toString()));
        long revenue = ((Number) out.get("revenueMinor")).longValue();
        long expenses = ((Number) out.get("expensesMinor")).longValue();
        out.put("profitMinor", revenue - expenses);
        out.put("inventoryAlerts", scalar("""
                SELECT COUNT(*) FROM (
                    SELECT i.id FROM inventory_items i
                    LEFT JOIN inventory_movements m ON m.inventory_item_id=i.id
                    WHERE i.farm_id=? AND i.status='ACTIVE'
                    GROUP BY i.id, i.reorder_level
                    HAVING i.reorder_level > 0
                       AND COALESCE(SUM(CASE WHEN m.movement_type IN ('RECEIVE','ADJUST_IN') THEN m.quantity
                                             WHEN m.movement_type IN ('ISSUE','ADJUST_OUT','WASTE') THEN -m.quantity ELSE 0 END),0) <= i.reorder_level
                )
                """, farm.id()));
        out.put("attention", attention.list(date).size());
        out.put("feedSummary", feedSummary(date));
        return out;
    }

    public Map<String, Object> farmRange(LocalDate from, LocalDate to) {
        ReportDateRange range = ReportDateRange.require(from, to);
        FarmResponse farm = requireFarm();
        Map<String, Object> result = farm(null);
        result.put("from", range.from());
        result.put("to", range.to());
        result.put("period", periodSummary(farm.id(), null, range));
        return result;
    }

    public Map<String, Object> batchRange(String batchId, LocalDate from, LocalDate to) {
        ReportDateRange range = ReportDateRange.require(from, to);
        FarmResponse farm = requireFarm();
        Map<String, Object> result = batch(batchId, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> batch = (Map<String, Object>) result.get("batch");
        if (batch == null || !farm.id().equals(batch.get("farmId"))) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found.");
        }
        result.put("from", range.from());
        result.put("to", range.to());
        result.put("period", periodSummary(farm.id(), batchId, range));
        return result;
    }

    private Map<String, Object> periodSummary(String farmId, String batchId, ReportDateRange range) {
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("from", range.from());
        period.put("to", range.to());
        long mortality = rangeTotal("""
                SELECT COALESCE(SUM(e.quantity), 0)
                FROM bird_population_events e JOIN batches b ON b.id=e.batch_id
                WHERE b.farm_id=? AND e.event_type='MORTALITY' AND e.event_date BETWEEN ? AND ?
                  AND (? IS NULL OR b.id=?)
                """, farmId, range, batchId);
        long eggsGood = rangeTotal("""
                SELECT COALESCE(SUM(e.good_eggs), 0)
                FROM egg_collections e JOIN batches b ON b.id=e.batch_id
                WHERE b.farm_id=? AND e.record_date BETWEEN ? AND ?
                  AND (? IS NULL OR b.id=?)
                """, farmId, range, batchId);
        long revenue = rangeTotal("""
                SELECT COALESCE(SUM(total_amount_minor), 0) FROM sales
                WHERE farm_id=? AND sale_date BETWEEN ? AND ?
                  AND (? IS NULL OR batch_id=?)
                """, farmId, range, batchId);
        long expenses = rangeTotal("""
                SELECT COALESCE(SUM(amount_minor), 0) FROM expenses
                WHERE farm_id=? AND occurred_date BETWEEN ? AND ?
                  AND (? IS NULL OR batch_id=?)
                """, farmId, range, batchId);
        period.put("mortality", mortality);
        period.put("eggsGood", eggsGood);
        period.put("revenueMinor", revenue);
        period.put("expensesMinor", expenses);
        period.put("profitMinor", revenue - expenses);
        period.put("salesCount", rangeTotal("""
                SELECT COUNT(*) FROM sales
                WHERE farm_id=? AND sale_date BETWEEN ? AND ?
                  AND (? IS NULL OR batch_id=?)
                """, farmId, range, batchId));
        period.put("expenseCount", rangeTotal("""
                SELECT COUNT(*) FROM expenses
                WHERE farm_id=? AND occurred_date BETWEEN ? AND ?
                  AND (? IS NULL OR batch_id=?)
                """, farmId, range, batchId));
        period.put("feedUsageQuantityMilli", rangeTotal("""
                SELECT COALESCE(SUM(u.quantity_milli), 0)
                FROM feed_usage u JOIN batches b ON b.id=u.batch_id
                WHERE b.farm_id=? AND u.usage_date BETWEEN ? AND ?
                  AND (? IS NULL OR b.id=?)
                """, farmId, range, batchId));
        period.put("feedUsageCount", rangeTotal("""
                SELECT COUNT(*)
                FROM feed_usage u JOIN batches b ON b.id=u.batch_id
                WHERE b.farm_id=? AND u.usage_date BETWEEN ? AND ?
                  AND (? IS NULL OR b.id=?)
                """, farmId, range, batchId));
        period.put("healthRecordCount", rangeTotal("""
                SELECT COUNT(*) FROM health_records h JOIN batches b ON b.id=h.batch_id
                WHERE b.farm_id=? AND h.record_date BETWEEN ? AND ?
                  AND (? IS NULL OR b.id=?)
                """, farmId, range, batchId));
        period.put("drugRecordCount", rangeTotal("""
                SELECT COUNT(*) FROM drug_records d JOIN batches b ON b.id=d.batch_id
                WHERE b.farm_id=? AND d.record_date BETWEEN ? AND ?
                  AND (? IS NULL OR b.id=?)
                """, farmId, range, batchId));
        period.put("vaccinationRecordCount", rangeTotal("""
                SELECT COUNT(*) FROM vaccination_records v JOIN batches b ON b.id=v.batch_id
                WHERE b.farm_id=? AND v.record_date BETWEEN ? AND ?
                  AND (? IS NULL OR b.id=?)
                """, farmId, range, batchId));
        return period;
    }

    private long rangeTotal(String sql, String farmId, ReportDateRange range, String batchId) {
        Long value = jdbc.queryForObject(sql, Long.class,
                farmId, range.from().toString(), range.to().toString(), batchId, batchId);
        return value == null ? 0L : value;
    }

    public Map<String, Object> batch(String batchId, LocalDate asOf) {
        FarmResponse farm = requireFarm();
        LocalDate date = asOf == null ? LocalDate.now(ZoneId.of(farm.timezone())) : asOf;
        Map<String,Object> batch = jdbc.query("""
                SELECT id, farm_id, code, batch_type, placement_date, initial_bird_count, status
                FROM batches WHERE id=?
                """, (rs,n) -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",rs.getString("id")); m.put("farmId",rs.getString("farm_id"));
            m.put("code",rs.getString("code")); m.put("type",rs.getString("batch_type"));
            m.put("placementDate",rs.getString("placement_date")); m.put("initialBirdCount",rs.getInt("initial_bird_count"));
            m.put("status",rs.getString("status")); return m;
        }, batchId).stream().findFirst().orElseThrow(() ->
                new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"BATCH_NOT_FOUND","Batch not found."));

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("asOf",date); out.put("batch",batch);
        out.put("population",population.get(batchId,date));
        BatchFeedCostResponse feedCost=feed.batchCost(batchId,date);
        out.put("feed",feedCost);
        out.put("expensesMinor",scalar("SELECT COALESCE(SUM(amount_minor),0) FROM expenses WHERE batch_id=? AND occurred_date<=?",batchId,date.toString()));
        out.put("revenueMinor",scalar("SELECT COALESCE(SUM(total_amount_minor),0) FROM sales WHERE batch_id=? AND sale_date<=?",batchId,date.toString()));
        long revenue=((Number)out.get("revenueMinor")).longValue(), expenses=((Number)out.get("expensesMinor")).longValue();
        out.put("profitMinor",revenue-expenses);
        if("LAYER".equals(batch.get("type"))) {
            out.put("eggInventory", eggs.inventory(batchId,date));
            out.put("eggQuality", eggQuality(batchId,date));
        } else {
            out.put("broilerGrowth", broilers.growth(batchId,date));
            out.put("broilerSales", broilers.sales(batchId,date));
            out.put("meatBirdGrowth", out.get("broilerGrowth"));
            out.put("meatBirdSales", out.get("broilerSales"));
        }
        out.put("attention",attentionForBatch(batchId,date));
        return out;
    }

    private Map<String,Object> eggQuality(String batchId, LocalDate date) {
        Map<String,Object> quality = new LinkedHashMap<>();
        Number good = jdbc.queryForObject("SELECT COALESCE(SUM(good_eggs),0) FROM egg_collections WHERE batch_id=? AND record_date<=?", Long.class, batchId, date.toString());
        Number cracked = jdbc.queryForObject("SELECT COALESCE(SUM(cracked_eggs),0) FROM egg_collections WHERE batch_id=? AND record_date<=?", Long.class, batchId, date.toString());
        long goodCount = good == null ? 0 : good.longValue();
        long crackedCount = cracked == null ? 0 : cracked.longValue();
        long total = goodCount + crackedCount;
        quality.put("good", goodCount);
        quality.put("cracked", crackedCount);
        quality.put("total", total);
        quality.put("goodRatePercent", total == 0 ? null : (goodCount * 100.0) / total);
        quality.put("crackedRatePercent", total == 0 ? null : (crackedCount * 100.0) / total);
        return quality;
    }

    private long populationTotal(String farmId, LocalDate date, String type) {
        String sql="""
                SELECT COALESCE(SUM(
                    b.initial_bird_count
                    - COALESCE((SELECT SUM(quantity) FROM bird_population_events e WHERE e.batch_id=b.id AND e.event_type='MORTALITY' AND e.event_date<=?),0)
                    - COALESCE((SELECT SUM(quantity) FROM bird_population_events e WHERE e.batch_id=b.id AND e.event_type='CULLING' AND e.event_date<=?),0)
                    - COALESCE((SELECT SUM(quantity) FROM bird_population_events e WHERE e.batch_id=b.id AND e.event_type='SOLD' AND e.event_date<=?),0)
                    + COALESCE((SELECT SUM(quantity) FROM bird_population_events e WHERE e.batch_id=b.id AND e.event_type='TRANSFER_IN' AND e.event_date<=?),0)
                    - COALESCE((SELECT SUM(quantity) FROM bird_population_events e WHERE e.batch_id=b.id AND e.event_type='TRANSFER_OUT' AND e.event_date<=?),0)
                    + COALESCE((SELECT SUM(difference) FROM bird_population_adjustments a WHERE a.batch_id=b.id AND a.effective_date<=?),0)
                ),0) FROM batches b WHERE b.farm_id=?
                """;
        if(type!=null) sql=sql.replace("b.farm_id=?","b.farm_id=? AND b.batch_type=?");
        Object[] args=type==null
                ? new Object[]{date.toString(),date.toString(),date.toString(),date.toString(),date.toString(),date.toString(),farmId}
                : new Object[]{date.toString(),date.toString(),date.toString(),date.toString(),date.toString(),date.toString(),farmId,type};
        Number value=jdbc.queryForObject(sql,Long.class,args);
        return value==null?0:value.longValue();
    }


    private long populationTotalByConfidence(String farmId, LocalDate date, String confidence) {
        Number value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(
                    b.initial_bird_count
                    - COALESCE((SELECT SUM(quantity) FROM bird_population_events e WHERE e.batch_id=b.id AND e.event_type IN ('MORTALITY','CULLING','SOLD','TRANSFER_OUT') AND e.event_date<=?),0)
                    + COALESCE((SELECT SUM(quantity) FROM bird_population_events e WHERE e.batch_id=b.id AND e.event_type='TRANSFER_IN' AND e.event_date<=?),0)
                    + COALESCE((SELECT SUM(difference) FROM bird_population_adjustments a WHERE a.batch_id=b.id AND a.effective_date<=?),0)
                ),0) FROM batches b
                WHERE b.farm_id=? AND b.population_count_status=? AND b.placement_date<=?
                """, Long.class, date.toString(), date.toString(), date.toString(), farmId, confidence, date.toString());
        return value == null ? 0 : value.longValue();
    }

    private Map<String,Object> feedSummary(LocalDate date) {
        var rows = feed.inventory(date);
        Long remainingCost = rows.stream().anyMatch(r -> r.remainingCostMinor() == null)
                ? null : rows.stream().mapToLong(r -> r.remainingCostMinor()).sum();
        Long consumedCost = rows.stream().anyMatch(r -> r.consumedCostMinor() == null)
                ? null : rows.stream().mapToLong(r -> r.consumedCostMinor()).sum();
        long remainingQuantityMilli = rows.stream().mapToLong(r -> r.remainingQuantityMilli()).sum();
        Map<String,Object> summary = new LinkedHashMap<>();
        summary.put("typeCount", rows.size());
        summary.put("remainingCostMinor", remainingCost);
        summary.put("consumedCostMinor", consumedCost);
        summary.put("remainingQuantityMilli", remainingQuantityMilli);
        return summary;
    }

    private long attentionForBatch(String batchId,LocalDate date){
        Number n=jdbc.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM daily_records WHERE batch_id=? AND record_date=?) THEN 0 ELSE 1 END",Long.class,batchId,date.toString());
        return n==null?0:n.longValue();
    }

    private Object scalar(String sql,Object...args){return jdbc.queryForObject(sql,Object.class,args);}

    private FarmResponse requireFarm(){
        FarmResponse farm=farms.findActive();
        if(farm==null) throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"FARM_NOT_FOUND","No active farm exists.");
        return farm;
    }
}
