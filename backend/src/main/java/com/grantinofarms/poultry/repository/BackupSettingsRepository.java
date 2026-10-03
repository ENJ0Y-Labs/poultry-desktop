package com.grantinofarms.poultry.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class BackupSettingsRepository {
    private final JdbcTemplate jdbc;
    public BackupSettingsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void insert(String farmId,String now){jdbc.update("INSERT INTO backup_settings(farm_id,created_at,updated_at) VALUES(?,?,?)",farmId,now,now);}
    public Row find(String farmId){return jdbc.query("SELECT enabled,directory,interval_ms FROM backup_settings WHERE farm_id=?",
        (rs,n)->new Row(rs.getInt("enabled")!=0,rs.getString("directory"),rs.getLong("interval_ms")),farmId).stream().findFirst().orElse(null);}
    public void update(String farmId,boolean enabled,String directory,long intervalMs,String now){jdbc.update("UPDATE backup_settings SET enabled=?,directory=?,interval_ms=?,updated_at=? WHERE farm_id=?",enabled?1:0,directory,intervalMs,now,farmId);}
    public record Row(boolean enabled,String directory,long intervalMs){}
}
