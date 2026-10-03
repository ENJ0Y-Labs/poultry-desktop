package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter DATE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");
    private static final java.util.regex.Pattern BACKUP_NAME = java.util.regex.Pattern.compile("poultry-\\d{4}-\\d{2}-\\d{2}(?:-\\d{4}(?:-\\d+)?)?\\.db");
    private final JdbcTemplate jdbc;
    private final Path database;

    public BackupService(JdbcTemplate jdbc,Environment env){
        this.jdbc=jdbc;
        String configured=env.getProperty("poultry.database-path","./data/poultry.db");
        this.database = isInMemoryDatabase(configured)
                ? null
                : Path.of(configured).toAbsolutePath().normalize();
    }

    private boolean isInMemoryDatabase(String configured) {
        return configured == null
                || configured.isBlank()
                || ":memory:".equalsIgnoreCase(configured)
                || "memory".equalsIgnoreCase(configured);
    }

    public synchronized Map<String,Object> create(String directory){
        try{
            if (database == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST,"BACKUP_UNAVAILABLE",
                        "Database backups are unavailable for an in-memory database.");
            }
            Path dir=directory==null||directory.isBlank()?database.getParent():Path.of(directory).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String base="poultry-"+LocalDateTime.now().format(NAME);
            Path target=dir.resolve(base+".db");int suffix=2;
            while(Files.exists(target))target=dir.resolve(base+"-"+suffix+++".db");
            String escaped=target.toString().replace("'","''");
            jdbc.execute("VACUUM INTO '"+escaped+"'");
            prune(dir);
            log.info("backup_created filename={} directory={}", target.getFileName(), dir);
            return Map.of("path",target.toString(),"filename",target.getFileName().toString());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"BACKUP_FAILED","The database backup could not be created.");
        }
    }

    public Map<String,Object> validate(String file){
        try{
            Path path=Path.of(file).toAbsolutePath().normalize();
            if(!Files.isRegularFile(path))throw new ApiException(HttpStatus.NOT_FOUND,"BACKUP_NOT_FOUND","Backup file was not found.");
            try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+path)){
                String integrity=connection.createStatement().executeQuery("PRAGMA integrity_check").getString(1);
                if(!"ok".equalsIgnoreCase(integrity))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_BACKUP","SQLite integrity check failed.");
                String version="";
                try(var rs=connection.createStatement().executeQuery("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")){if(rs.next())version=rs.getString(1);}
                log.info("backup_validated filename={} schemaVersion={}", path.getFileName(), version);
                return Map.of("valid",true,"path",path.toString(),"schemaVersion",version);
            }
        }catch(ApiException e){throw e;}catch(Exception e){
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_BACKUP","The selected file is not a valid Poultry Farm Manager database.");
        }
    }

    private void prune(Path dir)throws Exception{
        try(Stream<Path>s=Files.list(dir)){
            List<Path> backups=s.filter(p->BACKUP_NAME.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(Path::toString).reversed()).toList();
            for(int i=30;i<backups.size();i++)Files.deleteIfExists(backups.get(i));
        }
    }
}
