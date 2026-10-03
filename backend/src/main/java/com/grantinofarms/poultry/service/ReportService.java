package com.grantinofarms.poultry.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

@Service
public class ReportService {
    private final DashboardService dashboard;
    private final JdbcTemplate jdbc;
    public ReportService(DashboardService dashboard,JdbcTemplate jdbc){this.dashboard=dashboard;this.jdbc=jdbc;}

    public Map<String,Object> farm(LocalDate asOf){
        LocalDate date=asOf==null?LocalDate.now():asOf;
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("reportType","FARM_SUMMARY");report.put("asOf",date);report.put("dashboard",dashboard.farm(date));
        report.put("sales",jdbc.query("SELECT sale_date,sale_type,quantity,unit,total_amount_minor FROM sales WHERE sale_date<=? ORDER BY sale_date DESC LIMIT 1000",
                (rs,n)->Map.of("date",rs.getString("sale_date"),"type",rs.getString("sale_type"),"quantity",rs.getBigDecimal("quantity"),"unit",rs.getString("unit"),"totalMinor",rs.getLong("total_amount_minor")),date.toString()));
        report.put("expenses",jdbc.query("SELECT occurred_date,category,description,amount_minor,batch_id FROM expenses WHERE occurred_date<=? ORDER BY occurred_date DESC LIMIT 1000",
                (rs,n)->{
                    Map<String,Object> expense = new LinkedHashMap<>();
                    expense.put("date",rs.getString("occurred_date"));
                    expense.put("category",rs.getString("category"));
                    expense.put("description",rs.getString("description"));
                    expense.put("amountMinor",rs.getLong("amount_minor"));
                    expense.put("batchId",rs.getString("batch_id"));
                    return expense;
                },date.toString()));
        return report;
    }

    public Map<String,Object> batch(String id,LocalDate asOf){
        LocalDate date=asOf==null?LocalDate.now():asOf;
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("reportType","BATCH_SUMMARY");report.put("asOf",date);report.put("dashboard",dashboard.batch(id,date));
        report.put("sales",jdbc.query("SELECT sale_date,sale_type,quantity,unit,total_amount_minor FROM sales WHERE batch_id=? AND sale_date<=? ORDER BY sale_date DESC",
                (rs,n)->Map.of("date",rs.getString("sale_date"),"type",rs.getString("sale_type"),"quantity",rs.getBigDecimal("quantity"),"unit",rs.getString("unit"),"totalMinor",rs.getLong("total_amount_minor")),id,date.toString()));
        report.put("expenses",jdbc.query("SELECT occurred_date,category,description,amount_minor FROM expenses WHERE batch_id=? AND occurred_date<=? ORDER BY occurred_date DESC",
                (rs,n)->Map.of("date",rs.getString("occurred_date"),"category",rs.getString("category"),"description",rs.getString("description"),"amountMinor",rs.getLong("amount_minor")),id,date.toString()));
        return report;
    }
}
