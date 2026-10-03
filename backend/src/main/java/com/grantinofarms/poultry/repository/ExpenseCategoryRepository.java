package com.grantinofarms.poultry.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public class ExpenseCategoryRepository {
    private final JdbcTemplate jdbc;
    public ExpenseCategoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Row> findActive(String farmId) {
        return jdbc.query("SELECT id,name,status FROM expense_categories WHERE farm_id=? AND status='ACTIVE' ORDER BY name",
                (rs,n)->new Row(rs.getString("id"),rs.getString("name"),rs.getString("status")),farmId);
    }
    public Row findByName(String farmId,String name) {
        return jdbc.query("SELECT id,name,status FROM expense_categories WHERE farm_id=? AND name=?",
                (rs,n)->new Row(rs.getString("id"),rs.getString("name"),rs.getString("status")),farmId,name).stream().findFirst().orElse(null);
    }
    public Row findById(String farmId,String id) {
        return jdbc.query("SELECT id,name,status FROM expense_categories WHERE farm_id=? AND id=?",
                (rs,n)->new Row(rs.getString("id"),rs.getString("name"),rs.getString("status")),farmId,id).stream().findFirst().orElse(null);
    }
    public void insert(String id,String farmId,String name,String now) {
        jdbc.update("INSERT INTO expense_categories(id,farm_id,name,status,created_at,updated_at) VALUES(?,?,?,'ACTIVE',?,?)",id,farmId,name,now,now);
    }
    public void archive(String farmId,String id,String now) {
        jdbc.update("UPDATE expense_categories SET status='ARCHIVED',updated_at=? WHERE farm_id=? AND id=?",now,farmId,id);
    }
    public record Row(String id,String name,String status) {}
}
