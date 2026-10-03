package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.BatchFeedCostResponse;
import com.grantinofarms.poultry.dto.BroilerGrowthResponse;
import com.grantinofarms.poultry.dto.EggInventoryResponse;
import com.grantinofarms.poultry.dto.FarmResponse;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.FarmRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    public DashboardService(JdbcTemplate jdbc, FarmRepository farms, BirdPopulationService population,
                            FeedService feed, EggManagementService eggs, BroilerProductionService broilers) {
        this.jdbc = jdbc; this.farms = farms; this.population = population;
        this.feed = feed; this.eggs = eggs; this.broilers = broilers;
    }

    public Map<String, Object> farm(LocalDate asOf) {
        FarmResponse farm = requireFarm();
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOf", date);
        out.put("farm", farm);
        out.put("activeBatches", scalar("SELECT COUNT(*) FROM batches WHERE farm_id = ? AND status = 'ACTIVE'", farm.id()));
        out.put("totalBirds", populationTotal(farm.id(), date, null));
        out.put("layerBirds", populationTotal(farm.id(), date, "LAYER"));
        out.put("broilerBirds", populationTotal(farm.id(), date, "BROILER"));
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
                    HAVING COALESCE(SUM(CASE WHEN m.movement_type IN ('RECEIVE','ADJUST_IN') THEN m.quantity
                                             WHEN m.movement_type IN ('ISSUE','ADJUST_OUT','WASTE') THEN -m.quantity ELSE 0 END),0) <= i.reorder_level
                )
                """, farm.id()));
        out.put("attention", attentionCount(farm.id(), date));
        out.put("feedSummary", feedSummary(date));
        return out;
    }

    public Map<String, Object> batch(String batchId, LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
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
                ),0) FROM batches b WHERE b.farm_id=?
                """;
        if(type!=null) sql=sql.replace("b.farm_id=?","b.farm_id=? AND b.batch_type=?");
        Object[] args=type==null
                ? new Object[]{date.toString(),date.toString(),date.toString(),date.toString(),date.toString(),farmId}
                : new Object[]{date.toString(),date.toString(),date.toString(),date.toString(),date.toString(),farmId,type};
        Number value=jdbc.queryForObject(sql,Long.class,args);
        return value==null?0:value.longValue();
    }

    private Map<String,Object> feedSummary(LocalDate date) {
        var rows = feed.inventory(date);
        long remainingCost = rows.stream().mapToLong(r -> r.remainingCostMinor()).sum();
        long consumedCost = rows.stream().mapToLong(r -> r.consumedCostMinor()).sum();
        long remainingQuantityMilli = rows.stream().mapToLong(r -> r.remainingQuantityMilli()).sum();
        Map<String,Object> summary = new LinkedHashMap<>();
        summary.put("typeCount", rows.size());
        summary.put("remainingCostMinor", remainingCost);
        summary.put("consumedCostMinor", consumedCost);
        summary.put("remainingQuantityMilli", remainingQuantityMilli);
        return summary;
    }

    private long attentionCount(String farmId,LocalDate date){
        Number n=jdbc.queryForObject("SELECT COUNT(*) FROM batches b WHERE b.farm_id=? AND b.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM daily_records d WHERE d.batch_id=b.id AND d.record_date=?)",Long.class,farmId,date.toString());
        return n==null?0:n.longValue();
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
