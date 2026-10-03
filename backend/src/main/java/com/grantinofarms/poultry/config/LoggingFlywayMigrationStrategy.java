package com.grantinofarms.poultry.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.stereotype.Component;

@Component
public class LoggingFlywayMigrationStrategy implements FlywayMigrationStrategy {
    private static final Logger log = LoggerFactory.getLogger(LoggingFlywayMigrationStrategy.class);

    @Override
    public void migrate(Flyway flyway) {
        log.info("database_migration_started");

        try {
            var result = flyway.migrate();
            log.info("database_migration_completed migrationsApplied={} schemaVersion={}",
                    result.migrationsExecuted,
                    result.targetSchemaVersion == null ? "none" : result.targetSchemaVersion);
        } catch (RuntimeException e) {
            log.error("database_migration_failed", e);
            throw e;
        }
    }
}
