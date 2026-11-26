package com.grantinofarms.poultry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.ReportSubmissionRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.UserRepository;
import com.grantinofarms.poultry.service.AdminUserService;
import com.grantinofarms.poultry.service.AuthService;
import com.grantinofarms.poultry.service.CurrentUserContext;
import com.grantinofarms.poultry.service.PasswordHasher;
import com.grantinofarms.poultry.service.ReportSubmissionService;
import com.grantinofarms.poultry.service.ReportReviewService;
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
    @Autowired UserRepository users;
    @Autowired ReportSubmissionService submissions;
    @Autowired ReportReviewService reviews;
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
        var first = new ReportSubmissionRequest("DAILY_OPERATIONS", "Morning report",
                mapper.readTree("{\"mortality\":0,\"notes\":\"No activity\"}"));
        Map<String, Object> created = submissions.createDraft(first);
        String id = String.valueOf(created.get("id"));
        assertThat(created.get("status")).isEqualTo("DRAFT");
        assertThat(created.get("currentVersion")).isEqualTo(1);

        var second = new ReportSubmissionRequest("DAILY_OPERATIONS", "Morning report revised",
                mapper.readTree("{\"mortality\":1,\"notes\":\"One bird found dead\"}"));
        Map<String, Object> saved = submissions.saveDraft(id, second);
        assertThat(saved.get("currentVersion")).isEqualTo(2);
        assertThat((java.util.List<?>) saved.get("versions")).hasSize(2);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> versions =
                (java.util.List<Map<String, Object>>) saved.get("versions");
        assertThat(versions.get(0)).containsEntry("title", "Morning report")
                .containsEntry("reportType", "DAILY_OPERATIONS");
        assertThat(versions.get(1)).containsEntry("title", "Morning report revised");

        Map<String, Object> submitted = submissions.submit(id);
        assertThat(submitted.get("status")).isEqualTo("PENDING_REVIEW");
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("BLOCKED");
        assertThat(submitted.get("submittedVersion")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_submission_events WHERE submission_id = ?",
                Integer.class, id)).isEqualTo(4);

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

        assertThat(reviews.queue(null, null, null)).hasSize(1);
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
