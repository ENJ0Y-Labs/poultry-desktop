package com.grantinofarms.poultry.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Map;
@Repository
public class ApplicationSettingsRepository {
    private final JdbcTemplate jdbc;
    public ApplicationSettingsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void insert(String farmId,String now){jdbc.update("INSERT INTO application_settings(farm_id,created_at,updated_at) VALUES(?,?,?)",farmId,now,now);}
    public Row find(String farmId){return jdbc.query("SELECT start_page,date_format FROM application_settings WHERE farm_id=?",
        (rs,n)->new Row(rs.getString("start_page"),rs.getString("date_format")),farmId).stream().findFirst().orElse(null);}
    public void update(String farmId,String startPage,String dateFormat,String now){jdbc.update("UPDATE application_settings SET start_page=?,date_format=?,updated_at=? WHERE farm_id=?",startPage,dateFormat,now,farmId);}
    public record Row(String startPage,String dateFormat){}
}
