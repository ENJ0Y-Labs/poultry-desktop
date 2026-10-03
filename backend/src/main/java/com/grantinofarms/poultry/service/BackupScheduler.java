package com.grantinofarms.poultry.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupScheduler {
    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private final BackupService backups;
    private final boolean enabled;
    private final String directory;

    public BackupScheduler(
            BackupService backups,
            @Value("${poultry.backup.enabled:false}") boolean enabled,
            @Value("${poultry.backup.directory:}") String directory) {
        this.backups = backups;
        this.enabled = enabled;
        this.directory = directory;
    }

    @Scheduled(fixedDelayString = "${poultry.backup.interval-ms:86400000}")
    public void run() {
        if (!enabled) return;
        try {
            backups.create(directory);
        } catch (RuntimeException e) {
            log.error("scheduled_backup_failed", e);
        }
    }
}
