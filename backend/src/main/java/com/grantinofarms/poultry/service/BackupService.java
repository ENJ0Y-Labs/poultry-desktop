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
                log.warn("backup_unavailable reason=in_memory_database");
                throw new ApiException(HttpStatus.BAD_REQUEST,"BACKUP_UNAVAILABLE",
                        "Database backups are unavailable for an in-memory database.");
            }
            Path dir=directory==null||directory.isBlank()?database.getParent():Path.of(directory).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            LocalDateTime now = LocalDateTime.now();
            String base="poultry-"+now.format(DATE_NAME);
            Path target=dir.resolve(base+".db");
            if (Files.exists(target)) {
                String timestampedBase = "poultry-" + now.format(TIMESTAMP_NAME);
                target = dir.resolve(timestampedBase + ".db");
                int suffix = 2;
                while (Files.exists(target)) {
                    target = dir.resolve(timestampedBase + "-" + suffix++ + ".db");
                }
            }
            String escaped=target.toString().replace("'","''");
            log.info("backup_creation_started filename={}", target.getFileName());
            jdbc.execute("VACUUM INTO '"+escaped+"'");
            prune(dir);
            log.info("backup_created filename={} retentionLimit=30", target.getFileName());
            return Map.of("path",target.toString(),"filename",target.getFileName().toString());
        } catch (ApiException e) {
            throw e;
        } catch (AccessDeniedException e) {
            log.warn("backup_creation_failed reason=access_denied");
            throw new ApiException(HttpStatus.FORBIDDEN, "STORAGE_ACCESS_DENIED",
                    "The application does not have permission to write the backup file or folder.");
        } catch (FileSystemException e) {
            if (isDiskFull(e)) {
                log.warn("backup_creation_failed reason=disk_full");
                throw new ApiException(HttpStatus.INSUFFICIENT_STORAGE, "STORAGE_FULL",
                        "There is not enough disk space to create the backup.");
            }
            log.error("backup_creation_failed", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BACKUP_FAILED",
                    "The database backup could not be created.");
        } catch (Exception e) {
            log.error("backup_creation_failed", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"BACKUP_FAILED","The database backup could not be created.");
        }
    }

    private boolean isDiskFull(FileSystemException e) {
        String reason = e.getReason() == null ? "" : e.getReason().toLowerCase();
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return reason.contains("space") || reason.contains("disk full")
                || message.contains("not enough space") || message.contains("disk full");
    }

    public Map<String,Object> validate(String file){
        if (file == null || file.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BACKUP_FILE_REQUIRED",
                    "Backup file is required.");
        }
        Path path = Path.of(file).toAbsolutePath().normalize();
        try{
            if(!Files.isRegularFile(path))throw new ApiException(HttpStatus.NOT_FOUND,"BACKUP_NOT_FOUND","Backup file was not found.");
            log.info("backup_validation_started filename={}", path.getFileName());
            try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+path)){
                String integrity=connection.createStatement().executeQuery("PRAGMA integrity_check").getString(1);
                if(!"ok".equalsIgnoreCase(integrity))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_BACKUP","SQLite integrity check failed.");
                boolean historyExists = tableExists(connection, "flyway_schema_history");
                boolean metadataExists = tableExists(connection, "app_metadata");
                if (!historyExists || !metadataExists) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BACKUP",
                            "The selected database is not a Poultry Farm Manager database.");
                }

                String version = "";
                try (var rs = connection.createStatement().executeQuery(
                        "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1")) {
                    if (rs.next()) version = rs.getString(1);
                }
                if (version.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BACKUP",
                            "The backup does not contain a valid schema version.");
                }

                try (var rs = connection.createStatement().executeQuery("PRAGMA foreign_key_check")) {
                    if (rs.next()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BACKUP",
                                "SQLite foreign-key integrity check failed.");
                    }
                }

                log.info("backup_validated filename={} schemaVersion={}", path.getFileName(), version);
                return Map.of("valid", true, "path", path.toString(), "schemaVersion", version);
            }
        }catch(ApiException e){
            log.warn("backup_validation_failed filename={} code={}", path.getFileName(), e.getCode());
            throw e;
        }catch(Exception e){
            log.warn("backup_validation_failed filename={} reason=unexpected_error", path.getFileName(), e);
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_BACKUP","The selected file is not a valid Poultry Farm Manager database.");
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws java.sql.SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (var rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
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
