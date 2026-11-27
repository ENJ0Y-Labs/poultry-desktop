package com.grantinofarms.poultry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.ReportSubmissionRequest;
import com.grantinofarms.poultry.repository.ExpenseCategoryRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.ReportSubmissionRepository;
import com.grantinofarms.poultry.repository.UserRepository;
import com.grantinofarms.poultry.service.CurrentUserContext;
import com.grantinofarms.poultry.service.ReportMergeService;
import com.grantinofarms.poultry.service.ReportReviewService;
import com.grantinofarms.poultry.service.ReportSubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:report-review-hardening-test?mode=memory&cache=shared"
})
class ReportReviewHardeningIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired FarmRepository farms;
    @Autowired ExpenseCategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired ReportSubmissionRepository submissionRepository;
    @Autowired ReportSubmissionService submissions;
    @Autowired ReportReviewService reviews;
    @Autowired ReportMergeService merges;
    @Autowired ObjectMapper mapper;

    private String farmId;
    private String workerId;

    @BeforeEach
    void setUp() {
        String now = Instant.now().toString();
        farmId = UUID.randomUUID().toString();
        workerId = UUID.randomUUID().toString();
        // The shared in-memory database survives between test methods. Ensure this test's
        // newly-created farm is the one selected by FarmRepository.findActive().
        jdbc.update("UPDATE farms SET status = 'ARCHIVED' WHERE status = 'ACTIVE'");
        farms.insert(farmId, "Review Hardening Farm", "Port Harcourt", "Africa/Lagos", "NGN", now);
        categories.insert(UUID.randomUUID().toString(), farmId, "FEED", now);
        categories.insert(UUID.randomUUID().toString(), farmId, "DRUGS", now);
        categories.insert(UUID.randomUUID().toString(), farmId, "OTHER", now);
        users.insertWithRole(workerId, "review-worker-" + workerId.substring(0, 8),
                null, "test-password-hash", "Review Test Worker", "WORKER", now, null);
        CurrentUserContext.set(workerId, "WORKER");
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_submission_provenance");
    }

    @Test
    void exactExistingExpenseIsFlaggedAndTheCheckUsesTheExpenseBusinessDate() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Lagos"));
        String description = "Generator fuel delivery";
        jdbc.update("""
                INSERT INTO expenses
                    (id, farm_id, batch_id, category, amount_minor, occurred_date,
                     reference_type, reference_id, description, created_at)
                VALUES (?, ?, NULL, 'OTHER', 325000, ?, NULL, NULL, ?, ?)
                """, UUID.randomUUID().toString(), farmId, date.toString(), description, Instant.now().toString());

        Map<String, Object> submitted = submitExpense(date, description, 325000);
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("FLAGGED");

        @SuppressWarnings("unchecked")
        var checks = (java.util.List<Map<String, Object>>) submitted.get("checks");
        Map<String, Object> duplicate = checks.stream()
                .filter(check -> "OFFICIAL_EXPENSE_DUPLICATE".equals(check.get("code")))
                .findFirst().orElseThrow();

        assertThat(duplicate.get("outcome")).isEqualTo("WARNING");
        assertThat(duplicate.get("overrideable")).isEqualTo(true);
        assertThat(duplicate.get("details").toString()).contains(description);
        assertThat(checks).noneMatch(check -> "EVENT_DATE_REQUIRED".equals(check.get("code")));
    }

    @Test
    void failureAfterOfficialExpenseInsertRollsBackTheLedgerAndMergeState() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Lagos"));
        String description = "Rollback verification expense";
        Map<String, Object> submitted = submitExpense(date, description, 91200);
        String submissionId = String.valueOf(submitted.get("id"));
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("PENDING_REVIEW");

        String managerId = createManager();
        CurrentUserContext.set(managerId, "MANAGER");
        reviews.decide(submissionId, "APPROVED", null);

        jdbc.execute("""
                CREATE TRIGGER test_fail_submission_provenance
                BEFORE INSERT ON report_submission_links
                BEGIN
                    SELECT RAISE(ABORT, 'simulated provenance write failure');
                END
                """);

        assertThatThrownBy(() -> merges.merge(submissionId, "rollback-merge-key-001"))
                .isInstanceOf(Exception.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE farm_id = ? AND description = ?",
                Integer.class, farmId, description)).isZero();

        Map<String, Object> afterFailure = reviews.get(submissionId);
        assertThat(afterFailure.get("lifecycleStatus")).isEqualTo("APPROVED");
        assertThat((java.util.List<?>) afterFailure.get("links")).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_submissions WHERE id = ? AND merge_operation_key IS NULL",
                Integer.class, submissionId)).isEqualTo(1);
    }

    @Test
    void concurrentReviewTransitionsAllowOnlyOneDecisionForTheSubmittedVersion() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Lagos"));
        Map<String, Object> submitted = submitExpense(date, "Concurrent decision expense", 41000);
        String submissionId = String.valueOf(submitted.get("id"));
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("PENDING_REVIEW");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            String now = Instant.now().toString();
            Future<Integer> approval = pool.submit(() -> {
                start.await();
                return submissionRepository.transition(submissionId, 1, "PENDING_REVIEW",
                        "APPROVED", "manager-a", now);
            });
            Future<Integer> rejection = pool.submit(() -> {
                start.await();
                return submissionRepository.transition(submissionId, 1, "PENDING_REVIEW",
                        "REJECTED", "manager-b", now);
            });
            start.countDown();

            int changedRows = approval.get() + rejection.get();
            assertThat(changedRows).isEqualTo(1);
            String finalStatus = jdbc.queryForObject(
                    "SELECT lifecycle_status FROM report_submissions WHERE id = ?",
                    String.class, submissionId);
            assertThat(finalStatus).isIn("APPROVED", "REJECTED");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invalidExpensePayloadIsBlockedByIntegrityValidation() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Lagos"));
        Map<String, Object> submitted = submitExpense(date, "Invalid negative expense", -500);
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("BLOCKED");
        @SuppressWarnings("unchecked")
        var checks = (java.util.List<Map<String, Object>>) submitted.get("checks");
        assertThat(checks).anyMatch(check ->
                "REPORT_PERMISSION_VALID".equals(check.get("code"))
                        && "PASSED".equals(check.get("outcome")));
        assertThat(checks).anyMatch(check ->
                "REPORT_VERSION_INTEGRITY".equals(check.get("code"))
                        && "PASSED".equals(check.get("outcome")));
        assertThat(checks).anyMatch(check ->
                "REPORT_DATA_VALIDATION".equals(check.get("code"))
                        && "BLOCKED".equals(check.get("outcome")));
    }

    @Test
    void workerCannotAccessManagerReviewActions() {
        CurrentUserContext.set(workerId, "WORKER");
        assertThatThrownBy(() -> reviews.queue(null, null, null))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> reviews.decide("not-a-submission", "APPROVED", null))
                .isInstanceOf(Exception.class);
    }

    @Test
    void changedOfficialDataIsRecheckedAndBlocksAnOutdatedApprovalFromMerging() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Africa/Lagos"));
        String description = "Expense inserted after approval";
        Map<String, Object> submitted = submitExpense(date, description, 77500);
        String submissionId = String.valueOf(submitted.get("id"));
        assertThat(submitted.get("lifecycleStatus")).isEqualTo("PENDING_REVIEW");

        String managerId = createManager();
        CurrentUserContext.set(managerId, "MANAGER");
        reviews.decide(submissionId, "APPROVED", null);
        assertThat(reviews.get(submissionId).get("lifecycleStatus")).isEqualTo("APPROVED");

        jdbc.update("""
                INSERT INTO expenses
                    (id, farm_id, batch_id, category, amount_minor, occurred_date,
                     reference_type, reference_id, description, created_at)
                VALUES (?, ?, NULL, 'OTHER', 77500, ?, NULL, NULL, ?, ?)
                """, UUID.randomUUID().toString(), farmId, date.toString(),
                description, Instant.now().toString());

        assertThatThrownBy(() -> merges.merge(submissionId, "stale-review-key-001"))
                .isInstanceOf(Exception.class);

        Map<String, Object> returnedToReview = reviews.get(submissionId);
        assertThat(returnedToReview.get("lifecycleStatus")).isEqualTo("FLAGGED");
        @SuppressWarnings("unchecked")
        var checks = (java.util.List<Map<String, Object>>) returnedToReview.get("checks");
        assertThat(checks).anyMatch(check ->
                "OFFICIAL_EXPENSE_DUPLICATE".equals(check.get("code"))
                        && "WARNING".equals(check.get("outcome")));
        assertThat(checks).anyMatch(check ->
                "CURRENT_DATA_REVALIDATION".equals(check.get("code"))
                        && "PASSED".equals(check.get("outcome")));
        assertThat(returnedToReview.get("links")).isEqualTo(java.util.List.of());
    }

    private Map<String, Object> submitExpense(LocalDate date, String description, long amountMinor) throws Exception {
        String eventTime = LocalTime.now(ZoneId.of("Africa/Lagos"))
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        String payload = mapper.writeValueAsString(Map.of(
                "occurredDate", date.toString(),
                "eventTime", eventTime,
                "description", description,
                "amountMinor", amountMinor,
                "category", "OTHER"
        ));
        Map<String, Object> created = submissions.createDraft(
                new ReportSubmissionRequest("EXPENSE", description, mapper.readTree(payload)));
        return submissions.submit(String.valueOf(created.get("id")));
    }

    private String createManager() {
        String id = UUID.randomUUID().toString();
        users.insertWithRole(id, "review-manager-" + id.substring(0, 8),
                null, "test-password-hash", "Review Test Manager", "MANAGER",
                Instant.now().toString(), null);
        return id;
    }
}
