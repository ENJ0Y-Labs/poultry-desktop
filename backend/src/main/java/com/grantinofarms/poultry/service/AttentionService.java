package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.repository.FarmRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

@Service
public class AttentionService {
    private final JdbcTemplate jdbc;
    private final FarmRepository farms;
    public AttentionService(JdbcTemplate jdbc,FarmRepository farms){this.jdbc=jdbc;this.farms=farms;}

    public List<Map<String,Object>> list(LocalDate asOf){
        var farm=farms.findActive(); if(farm==null)return List.of();
        String date=(asOf==null?LocalDate.now():asOf).toString();
        List<Map<String,Object>> result=new ArrayList<>();
        jdbc.query("""
                SELECT i.name,i.unit,i.reorder_level,
                       COALESCE(SUM(CASE WHEN m.movement_type IN ('RECEIVE','ADJUST_IN') THEN m.quantity
                                         WHEN m.movement_type IN ('ISSUE','ADJUST_OUT','WASTE') THEN -m.quantity ELSE 0 END),0) stock
                FROM inventory_items i LEFT JOIN inventory_movements m ON m.inventory_item_id=i.id
                WHERE i.farm_id=? AND i.status='ACTIVE'
                GROUP BY i.id,i.name,i.unit,i.reorder_level
                HAVING COALESCE(SUM(CASE WHEN m.movement_type IN ('RECEIVE','ADJUST_IN') THEN m.quantity
                                         WHEN m.movement_type IN ('ISSUE','ADJUST_OUT','WASTE') THEN -m.quantity ELSE 0 END),0) <= i.reorder_level
                """,(rs,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("type","LOW_INVENTORY");m.put("severity","WARNING");m.put("title","Low inventory");m.put("message",rs.getString("name")+" is at or below its reorder level.");m.put("item",rs.getString("name"));m.put("stock",rs.getBigDecimal("stock"));m.put("unit",rs.getString("unit"));result.add(m);return null;},farm.id());
        jdbc.query("SELECT b.id,b.code FROM batches b WHERE b.farm_id=? AND b.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM daily_records d WHERE d.batch_id=b.id AND d.record_date=?)",(rs,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("type","MISSING_DAILY_RECORD");m.put("severity","WARNING");m.put("title","Missing daily record");m.put("message","No daily record has been entered for "+rs.getString("code")+" today.");m.put("batchId",rs.getString("id"));m.put("batchCode",rs.getString("code"));result.add(m);return null;},farm.id(),date);
        return result;
    }
}
