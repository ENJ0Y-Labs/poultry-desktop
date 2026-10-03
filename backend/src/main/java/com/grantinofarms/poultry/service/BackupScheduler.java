package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.repository.BackupSettingsRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupScheduler {
    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private final BackupService backups;
    private final BackupSettingsRepository settings;
    private final FarmRepository farms;

    public BackupScheduler(BackupService backups, BackupSettingsRepository settings, FarmRepository farms) {
        this.backups = backups; this.settings = settings; this.farms = farms;
    }

    @Scheduled(fixedDelay = 3600000)
    public void run() {
        var farm = farms.findActive();
        if (farm == null) return;
        var config = settings.find(farm.id());
        if (config == null || !config.enabled()) return;
        try {
            java.nio.file.Path directory = java.nio.file.Path.of(config.directory());
            java.nio.file.Files.createDirectories(directory);
            java.util.Optional<java.nio.file.Path> latest;
            try (var stream = java.nio.file.Files.list(directory)) {
                latest = stream
                        .filter(p -> p.getFileName().toString().startsWith("poultry-") && p.getFileName().toString().endsWith(".db"))
                        .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()));
            }
            long last = latest.map(p -> p.toFile().lastModified()).orElse(0L);
            if (last > 0 && System.currentTimeMillis() - last < config.intervalMs()) return;
            backups.create(config.directory());
        }
        catch (RuntimeException e) { log.error("scheduled_backup_failed", e); }
    }
}