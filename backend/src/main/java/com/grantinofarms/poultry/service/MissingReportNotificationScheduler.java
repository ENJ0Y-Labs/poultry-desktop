package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.ReportingConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

@Component
public class MissingReportNotificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(MissingReportNotificationScheduler.class);
    private final FarmRepository farms;
    private final ReportingConfigurationRepository reporting;

    public MissingReportNotificationScheduler(FarmRepository farms, ReportingConfigurationRepository reporting) {
        this.farms = farms;
        this.reporting = reporting;
    }

    @Scheduled(fixedDelay = 60000)
    public void notifyOverdueSchedules() {
        try {
            var farm = farms.findActive();
            if (farm == null) return;
            ZoneId zone = ZoneId.of(farm.timezone());
            LocalDateTime now = LocalDateTime.now(zone);
            for (Map<String, Object> schedule : reporting.schedules(farm.id())) {
                if (!Boolean.TRUE.equals(schedule.get("enabled"))) continue;
                boolean weekly = "WEEKLY".equals(schedule.get("frequency"));
                LocalDate latestDate = now.toLocalDate();
                LocalTime dueTime = LocalTime.parse(String.valueOf(schedule.get("dueTime")));
                if (weekly) {
                    int day = ((Number) schedule.get("dayOfWeek")).intValue();
                    latestDate = latestDate.minusDays(Math.floorMod(now.getDayOfWeek().getValue() - day, 7));
                }
                long grace = ((Number) schedule.get("graceMinutes")).longValue();
                LocalDateTime latestDue = LocalDateTime.of(latestDate, dueTime);
                if (!now.isAfter(latestDue.plusMinutes(grace))) {
                    latestDate = latestDate.minusDays(weekly ? 7 : 1);
                }
                String scopeType = String.valueOf(schedule.get("scopeType"));
                String scopeId = (String) schedule.get("scopeId");
                String batchType = (String) schedule.get("batchType");
                String scheduleId = String.valueOf(schedule.get("id"));
                int periods = weekly ? 8 : 30;
                for (int period = 0; period < periods; period++) {
                    LocalDate dueDate = latestDate.minusDays((long) period * (weekly ? 7 : 1));
                    LocalDateTime dueAt = LocalDateTime.of(dueDate, dueTime);
                    LocalDateTime graceUntil = dueAt.plusMinutes(grace);
                    if (!now.isAfter(graceUntil)) continue;
                    LocalDateTime windowEnd = dueAt.plusDays(weekly ? 7 : 1);
                    if (reporting.hasSubmissionInWindow(farm.id(), String.valueOf(schedule.get("reportType")),
                            scopeType, scopeId, batchType, dueAt.atZone(zone).toInstant().toString(),
                            windowEnd.atZone(zone).toInstant().toString())) continue;
                    String notificationId = scheduleId + ":" + dueAt;
                    reporting.notifyMissingReportManagers(farm.id(), notificationId,
                            "Missing " + schedule.get("reportType") + " report",
                            "The configured reporting window is overdue. No report was submitted; this is not a zero-activity record.",
                            Instant.now().toString());
                }
            }
        } catch (Exception exception) {
            log.warn("Could not evaluate overdue report schedules: {}", exception.getMessage());
        }
    }
}
