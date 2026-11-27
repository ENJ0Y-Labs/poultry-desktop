package com.grantinofarms.poultry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.ReportSubmissionRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.ExpenseCategoryRepository;
import com.grantinofarms.poultry.repository.UserRepository;
import com.grantinofarms.poultry.service.AdminUserService;
import com.grantinofarms.poultry.service.AuthService;
import com.grantinofarms.poultry.service.CurrentUserContext;
import com.grantinofarms.poultry.service.PasswordHasher;
import com.grantinofarms.poultry.service.ReportSubmissionService;
import com.grantinofarms.poultry.service.ReportReviewService;
import com.grantinofarms.poultry.service.ReportMergeService;
import com.grantinofarms.poultry.service.FarmIncidentService;
import com.grantinofarms.poultry.service.ReportAttachmentService;
import com.grantinofarms.poultry.service.ReportingConfigurationService;
import com.grantinofarms.poultry.service.MissingReportNotificationScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:report-submission-phase1-test?mode=memory&cache=shared"
})
class ReportSubmissionIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired FarmRepository farms;
    @Autowired ExpenseCategoryRepository expenseCategoryRepository;
    @Autowired UserRepository users;
    @Autowired ReportSubmissionService submissions;
    @Autowired ReportReviewService reviews;
    @Autowired ReportMergeService merges;
    @Autowired FarmIncidentService farmIncidents;
    @Autowired ReportAttachmentService attachments;
    @Autowired ReportingConfigurationService reportingConfiguration;
    @Autowired MissingReportNotificationScheduler missingReportScheduler;
    @Autowired AdminUserService adminUsers;
    @Autowired AuthService auth;
    @Autowired PasswordHasher passwordHasher;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    private String farmId;
    private String workerId;

    @BeforeEach
    void setUp() {
        String now = Instant.now().toString();
        farmId = UUID.randomUUID().toString();
        workerId = UUID.randomUUID().toString();
        farms.insert(farmId, "Submission Test Farm", "Port Harcourt", "Africa/Lagos", "NGN", now);
        expenseCategoryRepository.insert(UUID.randomUUID().toString(), farmId, "FEED", now);
        expenseCategoryRepository.insert(UUID.randomUUID().toString(), farmId, "DRUGS", now);
        expenseCategoryRepository.insert(UUID.randomUUID().toString(), farmId, "OTHER", now);
        users.insertWithRole(workerId, "worker-" + workerId.substring(0, 8),
                null, "test-password-hash", "Test Worker", "WORKER", now, null);
        CurrentUserContext.set(workerId, "WORKER");
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void savesAppendOnlyVersionsAndSubmitsTheExactCurrentVersion() throws Exception {
        java.time.ZoneId zone = java.time.ZoneId.of("Africa/Lagos");
        String eventDate = java.time.LocalDate.now(zone).toString();
        String eventTime = java.time.LocalTime.now(zone).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        var first = new ReportSubmissionRequest("EXPENSE", "Morning report",
                mapper.readTree("{\"occurredDate\":\"" + eventDate + "\",\"eventDate\":\"" + eventDate + "\",\"eventTime\":\"" + eventTime + "\",\"description\":\"Initial farm expense\",\"amountMinor\":1200,\"category\":\"OTHER\"}"));
        Map<String, Object> created = submissions.createDraft(first);
        String id = String.valueOf(created.get("id"));
        assertThat(created.get("status")).isEqualTo("DRAFT");
        assertThat(created.get("currentVersion")).isEqualTo(1);

        var second = new ReportSubmissionRequest("EXPENSE", "Morning report revised",
                mapper.readTree("{\"occurredDate\":\"" + eventDate + "\",\"eventDate\":\"" + eventDate + "\",\"eventTime\":\"" + eventTime + "\",\"description\":\"Revised farm expense\",\"amountMinor\":2400,\"category\":\"OTHER\"}"));
        Map<String, Object> saved = submissions.saveDraft(id, second);
        assertThat(saved.get("currentVersion")).isEqualTo(2);
        assertThat((java.util.List<?>) saved.get("versions")).hasSize(2);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> versions =
                (java.util.List<Map<String, Object>>) saved.get("versions");
        assertThat(versions.get(0)).containsEntry("title", "Morning report")
                .containsEntry("reportType", "EXPENSE");
        assertThat(versions.get(1)).containsEntry("title", "Morning report revised");

        Map<String, Object> submitted = submissions.submit(id);
        assertThat(submitted.get("status")).isEqualTo("PENDING_REVIEW");
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("PENDING_REVIEW");
        assertThat(submitted.get("submittedVersion")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_submission_events WHERE submission_id = ?",
                Integer.class, id)).isEqualTo(5);

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE report_submission_versions SET payload_json = '{}' 
                WHERE submission_id = ? AND version_number = 1
                """, id)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> submissions.saveDraft(id, second))
                .isInstanceOf(ApiException.class);
    }



    @Test
    void reportCreationIsIdempotentOnlyForTheSameRequestContent() throws Exception {
        var original = new ReportSubmissionRequest("MORTALITY", "Morning mortality",
                mapper.readTree("{\"batchId\":\"batch-1\",\"quantity\":1}"));
        Map<String, Object> first = submissions.createDraft(original, "retry-key-0001");
        Map<String, Object> retry = submissions.createDraft(original, "retry-key-0001");
        assertThat(retry.get("id")).isEqualTo(first.get("id"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_submissions WHERE idempotency_key = ?",
                Integer.class, "retry-key-0001")).isEqualTo(1);

        var changed = new ReportSubmissionRequest("MORTALITY", "Morning mortality",
                mapper.readTree("{\"batchId\":\"batch-1\",\"quantity\":2}"));
        assertThatThrownBy(() -> submissions.createDraft(changed, "retry-key-0001"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different report content");
    }


    @Test
    void workerCanCorrectABlockedSubmissionAsANewVersion() throws Exception {
        Map<String, Object> created = submissions.createDraft(new ReportSubmissionRequest(
                "MORTALITY", "Incomplete mortality report", mapper.readTree("{\"quantity\":1}")));
        String id = String.valueOf(created.get("id"));
        Map<String, Object> blocked = submissions.submit(id);
        assertThat(blocked.get("lifecycleStatus")).isEqualTo("BLOCKED");

        Map<String, Object> revised = submissions.saveDraft(id, new ReportSubmissionRequest(
                "MORTALITY", "Corrected mortality report",
                mapper.readTree("{\"eventDate\":\"2026-11-26\",\"eventTime\":\"08:00\",\"batchId\":\"batch-1\",\"quantity\":1}")));
        assertThat(revised.get("lifecycleStatus")).isEqualTo("DRAFT");
        assertThat(revised.get("currentVersion")).isEqualTo(2);
        assertThat((java.util.List<?>) revised.get("versions")).hasSize(2);
    }

    @Test
    void managerCanAssignCommentAndRequestChangesThenAuthorResubmitsNewVersion() throws Exception {
        Map<String, Object> created = submissions.createDraft(new ReportSubmissionRequest(
                "MORTALITY", "Mortality report", mapper.readTree("{\"quantity\":1,\"eventDate\":\"2026-11-26\"}")));
        String id = String.valueOf(created.get("id"));
        submissions.submit(id);

        String managerId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(managerId, "review-manager-" + managerId.substring(0, 6),
                null, "test-password-hash", "Review Manager", "MANAGER", now, null);
        CurrentUserContext.set(managerId, "MANAGER");

        assertThat(reviews.queue(null, null, null))
                .extracting(row -> row.get("id"))
                .contains(id);
        Map<String, Object> assigned = reviews.assign(id, managerId);
        assertThat(assigned.get("assignedManagerId")).isEqualTo(managerId);
        reviews.comment(id, "Please confirm the affected batch and observation time.");
        Map<String, Object> returned = reviews.decide(id, "CHANGES_REQUESTED", "The batch reference is missing.");
        assertThat(returned.get("lifecycleStatus")).isEqualTo("CHANGES_REQUESTED");
        assertThat((java.util.List<?>) returned.get("comments")).hasSize(1);
        assertThat((java.util.List<?>) returned.get("decisions")).hasSize(1);

        CurrentUserContext.set(workerId, "WORKER");
        Map<String, Object> revised = submissions.saveDraft(id, new ReportSubmissionRequest(
                "MORTALITY", "Mortality report", mapper.readTree("{\"quantity\":1,\"batchId\":\"batch-1\",\"eventDate\":\"2026-11-26\"}")));
        assertThat(revised.get("currentVersion")).isEqualTo(2);
        Map<String, Object> resubmitted = submissions.submit(id);
        assertThat(resubmitted.get("lifecycleStatus")).isEqualTo("BLOCKED");
        assertThat((java.util.List<?>) resubmitted.get("versions")).hasSize(2);
    }


    @Test
    void lateReportRequiresReasonAndSeparateManagerAuthorizationBeforeApproval() throws Exception {
        java.time.LocalDate lateDate = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Lagos")).minusDays(2);
        var request = new ReportSubmissionRequest("EXPENSE", "Late expense report",
                mapper.readTree("{\"eventDate\":\"" + lateDate + "\",\"eventTime\":\"09:00\","
                        + "\"lateReason\":\"Power outage prevented timely entry\","
                        + "\"description\":\"Feed transport\",\"amountMinor\":50000,\"category\":\"OTHER\"}"));
        Map<String, Object> created = submissions.createDraft(request);
        String id = String.valueOf(created.get("id"));
        Map<String, Object> submitted = submissions.submit(id);
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("FLAGGED");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> checks =
                (java.util.List<Map<String, Object>>) submitted.get("checks");
        Map<String, Object> lateCheck = checks.stream()
                .filter(check -> "LATE_REPORT_REQUIRES_MANAGER_AUTHORIZATION".equals(check.get("code")))
                .findFirst().orElseThrow();
        assertThat(lateCheck.get("overrideable")).isEqualTo(true);

        String managerId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(managerId, "late-review-" + managerId.substring(0, 6),
                null, "test-password-hash", "Late Review Manager", "MANAGER", now, null);
        CurrentUserContext.set(managerId, "MANAGER");
        Map<String, Object> authorized = reviews.overrideWarning(id, String.valueOf(lateCheck.get("id")),
                "Verified the original event record and approved the late entry.");
        assertThat((java.util.List<?>) authorized.get("history")).anySatisfy(event ->
                assertThat(((Map<?, ?>) event).get("event")).isEqualTo("WARNING_OVERRIDDEN"));

        Map<String, Object> approved = reviews.decide(id, "APPROVED", null);
        assertThat(approved.get("lifecycleStatus")).isEqualTo("APPROVED");
    }


    @Test
    void approvedExpenseMergesOnceIntoTheExistingLedgerAndKeepsProvenance() throws Exception {
        java.time.ZoneId zone = java.time.ZoneId.of("Africa/Lagos");
        String eventDate = java.time.LocalDate.now(zone).toString();
        String eventTime = java.time.LocalTime.now(zone).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        var request = new ReportSubmissionRequest("EXPENSE", "Feed transport expense",
                mapper.readTree("{\"eventDate\":\"" + eventDate + "\",\"eventTime\":\"" + eventTime + "\","
                        + "\"description\":\"Feed transport\",\"amountMinor\":120000,\"category\":\"OTHER\"}"));
        Map<String, Object> created = submissions.createDraft(request);
        String id = String.valueOf(created.get("id"));
        Map<String, Object> submitted = submissions.submit(id);
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("PENDING_REVIEW");

        String managerId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(managerId, "merge-manager-" + managerId.substring(0, 6),
                null, "test-password-hash", "Merge Manager", "MANAGER", now, null);
        CurrentUserContext.set(managerId, "MANAGER");
        reviews.decide(id, "APPROVED", null);

        Map<String, Object> merged = merges.merge(id, "expense-merge-key-001");
        assertThat(merged.get("lifecycleStatus")).isEqualTo("MERGED");
        assertThat((java.util.List<?>) merged.get("links")).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE description = ?",
                Integer.class, "Feed transport")).isEqualTo(1);

        Map<String, Object> retry = merges.merge(id, "expense-merge-key-001");
        assertThat(retry.get("lifecycleStatus")).isEqualTo("MERGED");
        assertThat((java.util.List<?>) retry.get("links")).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE description = ?",
                Integer.class, "Feed transport")).isEqualTo(1);
    }


    @Test
    void emergencyIncidentNotifiesManagersAndKeepsAnAppendOnlyResponseHistory() {
        String managerId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(managerId, "incident-manager-" + managerId.substring(0, 6),
                null, "test-password-hash", "Incident Manager", "MANAGER", now, null);
        String eventAt = java.time.LocalDateTime.now(java.time.ZoneId.of("Africa/Lagos")).toString();
        Map<String, Object> incident = farmIncidents.create(new FarmIncidentService.IncidentRequest(
                "WATER_FAILURE", "CRITICAL", "Water supply stopped",
                "The drinker line has stopped supplying water to the active flock.", null, null, eventAt));
        String id = String.valueOf(incident.get("id"));
        assertThat(incident.get("status")).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM manager_notifications WHERE entity_id = ?",
                Integer.class, id)).isGreaterThanOrEqualTo(1);

        CurrentUserContext.set(managerId, "MANAGER");
        assertThat(farmIncidents.notifications()).hasSize(1);
        assertThat(farmIncidents.acknowledge(id).get("status")).isEqualTo("ACKNOWLEDGED");
        assertThat(farmIncidents.escalate(id, "The repair is delayed and the flock remains at risk.").get("status"))
                .isEqualTo("ACKNOWLEDGED");
        assertThat(farmIncidents.escalate(id, "The replacement pump has not arrived; keep the flock under observation.").get("status"))
                .isEqualTo("ACKNOWLEDGED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM manager_notifications WHERE recipient_user_id = ? AND entity_id = ?",
                Integer.class, managerId, id)).isEqualTo(2);
        assertThat(farmIncidents.resolve(id, "Water pump restored and the line pressure verified.").get("status"))
                .isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM farm_incident_events WHERE incident_id = ?",
                Integer.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE description = ?",
                Integer.class, "The drinker line has stopped supplying water to the active flock.")).isZero();

        CurrentUserContext.set(workerId, "WORKER");
        var reporterNotifications = farmIncidents.notifications();
        assertThat(reporterNotifications).extracting(row -> row.get("type"))
                .contains("INCIDENT_ACKNOWLEDGED", "INCIDENT_ESCALATED", "INCIDENT_RESOLVED");
    }



    @Test
    void reportSubmissionAndReviewDecisionsNotifyTheRelevantUsers() throws Exception {
        String managerId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(managerId, "workflow-manager-" + managerId.substring(0, 6),
                null, "test-password-hash", "Workflow Manager", "MANAGER", now, null);
        java.time.ZoneId zone = java.time.ZoneId.of("Africa/Lagos");
        String eventDate = java.time.LocalDate.now(zone).toString();
        String eventTime = java.time.LocalTime.now(zone).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        String payload = """
                {"occurredDate":"%s","eventDate":"%s","eventTime":"%s","description":"Notification test expense","amountMinor":1200,"category":"OTHER"}
                """.formatted(eventDate, eventDate, eventTime);
        Map<String, Object> created = submissions.createDraft(new ReportSubmissionRequest(
                "EXPENSE", "Notification test expense", mapper.readTree(payload)));
        String submissionId = String.valueOf(created.get("id"));
        Map<String, Object> submitted = submissions.submit(submissionId);
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM manager_notifications WHERE recipient_user_id = ? AND entity_id = ? AND notification_type = 'REPORT_REVIEW_NEEDED'",
                Integer.class, managerId, submissionId)).isEqualTo(1);

        CurrentUserContext.set(managerId, "MANAGER");
        reviews.decide(submissionId, "APPROVED", null);
        CurrentUserContext.set(workerId, "WORKER");
        var workerNotifications = farmIncidents.notifications();
        assertThat(workerNotifications).extracting(notification -> notification.get("type"))
                .contains("REPORT_SUBMITTED", "REPORT_APPROVED");
        Map<String, Object> approval = workerNotifications.stream()
                .filter(notification -> "REPORT_APPROVED".equals(notification.get("type")))
                .findFirst().orElseThrow();
        farmIncidents.markNotificationRead(String.valueOf(approval.get("id")));
        assertThat(jdbc.queryForObject(
                "SELECT read_at FROM manager_notifications WHERE id = ?",
                String.class, approval.get("id"))).isNotBlank();

        String password = "worker-notification-password";
        users.updatePassword(workerId, passwordHasher.hash(password), Instant.now().toString());
        var session = auth.login(new com.grantinofarms.poultry.dto.LoginRequest(
                "worker-" + workerId.substring(0, 8), password, false));
        mockMvc.perform(get("/api/v1/review/notifications")
                        .cookie(new Cookie(AuthService.COOKIE, session.token())))
                .andExpect(status().isOk());
    }

    @Test
    void evidenceIsVersionBoundImmutableAndIncludedInTheDatabaseBackup() throws Exception {
        Map<String, Object> created = submissions.createDraft(new ReportSubmissionRequest(
                "DAILY_OPERATIONS", "Report with evidence", mapper.readTree("{\"eventDate\":\"2026-11-26\"}")));
        String id = String.valueOf(created.get("id"));
        byte[] content = "Supplier delivery note 4821".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "../../delivery-note.txt", "text/plain", content);
        Map<String, Object> metadata = attachments.upload(id, file);
        assertThat(metadata.get("fileName")).isEqualTo("delivery-note.txt");
        assertThat(metadata.get("mimeType")).isEqualTo("text/plain");
        assertThat(metadata.get("fileSize")).isEqualTo((long) content.length);
        assertThat(jdbc.queryForObject(
                "SELECT length(file_content) FROM report_submission_attachments WHERE id = ?",
                Integer.class, metadata.get("id"))).isEqualTo(content.length);

        Map<String, Object> downloaded = attachments.download(id, String.valueOf(metadata.get("id")));
        assertThat((byte[]) downloaded.get("content")).containsExactly(content);
        assertThatThrownBy(() -> attachments.upload(id, new org.springframework.mock.web.MockMultipartFile(
                "file", "fake.png", "image/png", content)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("valid JPEG, PNG, PDF, or UTF-8 text");
    }


    @Test
    void reportingSchedulesAndMortalityThresholdsAreManagerConfiguredAndStartEmpty() {
        String managerId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(managerId, "report-config-" + managerId.substring(0, 6),
                null, "test-password-hash", "Reporting Manager", "MANAGER", now, null);
        CurrentUserContext.set(managerId, "MANAGER");

        assertThat(reportingConfiguration.schedules()).isEmpty();
        assertThat(reportingConfiguration.thresholds()).isEmpty();

        Map<String, Object> schedule = reportingConfiguration.createSchedule(
                new ReportingConfigurationService.ScheduleRequest(
                        "EGG_SALE", "FARM", null, null, "DAILY", "00:00", null, 0));
        Map<String, Object> threshold = reportingConfiguration.createThreshold(
                new ReportingConfigurationService.ThresholdRequest(
                        "UNUSUAL_MORTALITY_COUNT", "FARM", null, null, 5, "BIRDS"));

        assertThat(schedule.get("enabled")).isEqualTo(true);
        assertThat(threshold.get("value")).isEqualTo(5.0);
        assertThat(reportingConfiguration.missingReports())
                .extracting(row -> row.get("id"))
                .doesNotContain(schedule.get("id"));

        String priorScheduleTime = Instant.now().minusSeconds(172800).toString();
        jdbc.update("UPDATE reporting_schedules SET created_at = ?, updated_at = ? WHERE id = ?",
                priorScheduleTime, priorScheduleTime, schedule.get("id"));
        assertThat(reportingConfiguration.missingReports())
                .extracting(row -> row.get("id"))
                .contains(schedule.get("id"));
        missingReportScheduler.notifyOverdueSchedules();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM manager_notifications WHERE notification_type = 'MISSING_REPORT' AND entity_id LIKE ?",
                Integer.class, schedule.get("id") + ":%")).isGreaterThan(0);

        reportingConfiguration.setScheduleEnabled(String.valueOf(schedule.get("id")), false);
        assertThat(reportingConfiguration.missingReports()).isEmpty();
        reportingConfiguration.setScheduleEnabled(String.valueOf(schedule.get("id")), true);
        assertThat(reportingConfiguration.missingReports()).isEmpty();
    }

    @Test
    void managerCannotApproveOwnSubmissionAndWorkerCannotOpenReviewQueue() throws Exception {
        String managerId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(managerId, "self-manager-" + managerId.substring(0, 6),
                null, "test-password-hash", "Self Review Manager", "MANAGER", now, null);
        CurrentUserContext.set(managerId, "MANAGER");
        Map<String, Object> created = submissions.createDraft(new ReportSubmissionRequest(
                "DAILY_OPERATIONS", "Manager's own report", mapper.readTree("{\"notes\":\"test\"}")));
        String id = String.valueOf(created.get("id"));
        submissions.submit(id);
        assertThatThrownBy(() -> reviews.decide(id, "APPROVED", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own submission");

        CurrentUserContext.set(workerId, "WORKER");
        assertThatThrownBy(() -> reviews.queue(null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
    }

    @Test
    void authorCannotReadAnotherWorkersSubmission() throws Exception {
        Map<String, Object> created = submissions.createDraft(new ReportSubmissionRequest(
                "DAILY_OPERATIONS", "Private report", mapper.readTree("{\"mortality\":0}")));
        String otherWorkerId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(otherWorkerId, "worker-" + otherWorkerId.substring(0, 8),
                null, "test-password-hash", "Other Worker", "WORKER", now, null);
        CurrentUserContext.set(otherWorkerId, "WORKER");

        assertThatThrownBy(() -> submissions.get(String.valueOf(created.get("id"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
        assertThat(submissions.mine()).isEmpty();
    }

    @Test
    void workerCannotBypassRoleRestrictionsByCallingAnAdminApiDirectly() throws Exception {
        String password = "worker-login-password";
        String userId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(userId, "api-worker-" + userId.substring(0, 6), null,
                passwordHasher.hash(password), "API Worker", "WORKER", now, null);
        var session = auth.login(new com.grantinofarms.poultry.dto.LoginRequest(
                "api-worker-" + userId.substring(0, 6), password, false));

        mockMvc.perform(get("/api/v1/admin/users")
                        .cookie(new Cookie(AuthService.COOKIE, session.token())))
                .andExpect(status().isForbidden());
    }


    @Test
    void administratorCannotCallManagerReviewApiDirectly() throws Exception {
        String password = "administrator-login-password";
        String adminId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(adminId, "api-admin-" + adminId.substring(0, 6), null,
                passwordHasher.hash(password), "API Administrator", "ADMINISTRATOR", now, null);
        var session = auth.login(new com.grantinofarms.poultry.dto.LoginRequest(
                "api-admin-" + adminId.substring(0, 6), password, false));

        mockMvc.perform(get("/api/v1/review/queue")
                        .cookie(new Cookie(AuthService.COOKIE, session.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void workerCanCommentOnOwnSubmissionThroughTheScopedReviewCommentEndpoint() throws Exception {
        Map<String, Object> created = submissions.createDraft(new ReportSubmissionRequest(
                "DAILY_OPERATIONS", "Worker report", mapper.readTree("{\"notes\":\"test\"}")));
        String id = String.valueOf(created.get("id"));
        String password = "worker-comment-password";
        String now = Instant.now().toString();
        users.updatePassword(workerId, passwordHasher.hash(password), now);
        var session = auth.login(new com.grantinofarms.poultry.dto.LoginRequest(
                "worker-" + workerId.substring(0, 8), password, false));

        mockMvc.perform(post("/api/v1/review/submissions/" + id + "/comments")
                        .cookie(new Cookie(AuthService.COOKIE, session.token()))
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content("{\"body\":\"I have added the missing details.\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_submission_comments WHERE submission_id = ?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void administratorCanCreateManageAndRevokeUserAccounts() {
        String adminId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(adminId, "admin-" + adminId.substring(0, 8),
                null, "test-password-hash", "Test Administrator", "ADMINISTRATOR", now, null);
        CurrentUserContext.set(adminId, "ADMINISTRATOR");

        Map<String, Object> created = adminUsers.create(new com.grantinofarms.poultry.dto.AdminUserCreateRequest(
                "new-worker-" + adminId.substring(0, 6), null, "New Worker",
                "a-secure-password", "WORKER"));
        String newUserId = String.valueOf(created.get("id"));
        assertThat(created.get("role")).isEqualTo("WORKER");
        assertThat(created.get("status")).isEqualTo("ACTIVE");

        assertThat(adminUsers.changeStatus(newUserId, "SUSPENDED").get("status")).isEqualTo("SUSPENDED");
        assertThat(adminUsers.changeStatus(newUserId, "ACTIVE").get("status")).isEqualTo("ACTIVE");
        assertThat(adminUsers.changeRole(newUserId, "MANAGER").get("role")).isEqualTo("MANAGER");
        assertThat(adminUsers.resetPassword(newUserId, "another-secure-password").get("id")).isEqualTo(newUserId);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type='USER_ACCOUNT' AND entity_id=?",
                Integer.class, newUserId)).isEqualTo(5);
    }

    @Test
    void workerCannotUseAdministratorGuardOrManageAccounts() {
        assertThatThrownBy(com.grantinofarms.poultry.service.RoleAuthorization::requireAdministrator)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
        assertThatThrownBy(adminUsers::list)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
    }
}
