package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.AuthSetupRequest;
import com.grantinofarms.poultry.dto.LoginRequest;
import com.grantinofarms.poultry.service.AuthService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.PasswordHasher;
import com.grantinofarms.poultry.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:security-acceptance-test?mode=memory&cache=shared",
        "poultry.database-path=:memory:",
        "poultry.auth-required=true"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SecurityAcceptanceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired PasswordHasher passwords;
    @Autowired FarmService farms;

    private String workerId;
    private String managerId;
    private MockCookie workerCookie;
    private MockCookie managerCookie;
    private MockCookie adminCookie;

    @BeforeEach
    void setUp() {
        auth.setup(new AuthSetupRequest("security-admin@example.test", "Admin-password-123!", "Security Admin"));
        adminCookie = cookieFor("security-admin@example.test", "Admin-password-123!", "admin-client");

        String now = Instant.now().toString();
        workerId = UUID.randomUUID().toString();
        managerId = UUID.randomUUID().toString();
        users.insertWithRole(workerId, "security-worker", "worker@example.test",
                passwords.hash("Worker-password-123!"), "Security Worker", "WORKER", now, null);
        users.insertWithRole(managerId, "security-manager", "manager@example.test",
                passwords.hash("Manager-password-123!"), "Security Manager", "MANAGER", now, null);
        workerCookie = cookieFor("security-worker", "Worker-password-123!", "worker-client");
        managerCookie = cookieFor("security-manager", "Manager-password-123!", "manager-client");
        farms.create(new com.grantinofarms.poultry.dto.FarmCreateRequest(
                "Security Acceptance Farm", "Port Harcourt", "Africa/Lagos", "NGN"));
    }

    @Test
    void rejectsAnonymousAndUnverifiedStateChangingRequestsBeforeAnyWrite() throws Exception {
        mvc.perform(get("/api/v1/farm"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        mvc.perform(post("/api/v1/farm")
                        .contentType("application/json")
                        .content("""
                                {"name":"Must Not Exist","location":"Port Harcourt","timezone":"Africa/Lagos","currency":"NGN"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_CHECK_FAILED"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM farms WHERE name='Must Not Exist'", Integer.class)).isZero();

        mvc.perform(post("/api/v1/farm")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content("""
                                {"name":"Still Must Not Exist","location":"Port Harcourt","timezone":"Africa/Lagos","currency":"NGN"}
                                """))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM farms WHERE name='Still Must Not Exist'", Integer.class)).isZero();
    }

    @Test
    void enforcesWorkerAdministratorAndManagerApiBoundariesOnTheServer() throws Exception {
        mvc.perform(get("/api/v1/auth/me").cookie(workerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("WORKER"));

        mvc.perform(get("/api/v1/submissions").cookie(workerCookie))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/farm").cookie(workerCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROLE_FORBIDDEN"));

        mvc.perform(get("/api/v1/review/queue").cookie(workerCookie))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/review/queue").cookie(adminCookie))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/review/queue").cookie(managerCookie))
                .andExpect(status().isOk());
    }

    @Test
    void preventsWorkersFromReadingOrMutatingAnotherWorkersSubmissionAndManagerOrAdminActions() throws Exception {
        mvc.perform(post("/api/v1/submissions")
                        .cookie(workerCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Idempotency-Key", "worker-a-report-key-001")
                        .contentType("application/json")
                        .content("""
                                {
                                  "reportType":"MORTALITY",
                                  "title":"Private Worker A report",
                                  "payload":{"batchId":"batch-a","quantity":1}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty());

        String submissionId = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(get("/api/v1/submissions").cookie(workerCookie))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.data[0].id");

        String otherWorkerId = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insertWithRole(otherWorkerId, "security-worker-b", "worker-b@example.test",
                passwords.hash("Worker-B-password-123!"), "Security Worker B", "WORKER", now, null);
        MockCookie otherWorkerCookie = cookieFor("security-worker-b", "Worker-B-password-123!", "worker-b-client");

        mvc.perform(get("/api/v1/submissions/" + submissionId).cookie(otherWorkerCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_NOT_FOUND"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/submissions/" + submissionId)
                        .cookie(otherWorkerCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content("""
                                {"reportType":"MORTALITY","title":"Hijacked report","payload":{"quantity":999}}
                                """))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/submissions/" + submissionId + "/submit")
                        .cookie(otherWorkerCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Idempotency-Key", "worker-b-submit-key-001"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/submissions/" + submissionId + "/attachments").cookie(otherWorkerCookie))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/review/submissions/" + submissionId + "/comments")
                        .cookie(otherWorkerCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content("""
                                {"body":"Unauthorized access attempt"}
                                """))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/review/submissions/" + submissionId).cookie(otherWorkerCookie))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/review/managers").cookie(otherWorkerCookie))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").cookie(otherWorkerCookie))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/review/submissions/" + submissionId + "/assign")
                        .cookie(otherWorkerCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content("{\"managerId\":\"" + managerId + "\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/review/submissions/" + submissionId + "/decision")
                        .cookie(otherWorkerCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVED"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/review/submissions/" + submissionId + "/merge")
                        .cookie(otherWorkerCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Idempotency-Key", "worker-b-merge-key-001"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolvesRoleChangesAndSuspensionAgainstTheDatabaseOnEveryRequest() throws Exception {
        mvc.perform(get("/api/v1/review/queue").cookie(workerCookie))
                .andExpect(status().isForbidden());

        String now = Instant.now().toString();
        jdbc.update("UPDATE user_roles SET role='MANAGER', updated_at=? WHERE user_id=?", now, workerId);

        mvc.perform(get("/api/v1/review/queue").cookie(workerCookie))
                .andExpect(status().isOk());

        jdbc.update("UPDATE users SET status='SUSPENDED', updated_at=? WHERE id=?", now, workerId);
        mvc.perform(get("/api/v1/auth/me").cookie(workerCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void allowsOnlyDocumentedRendererOriginsToRequestCredentialedCorsAccess() throws Exception {
        mvc.perform(options("/api/v1/farm")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Requested-With"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mvc.perform(options("/api/v1/farm")
                        .header("Origin", "http://localhost.attacker.invalid:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Requested-With"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private MockCookie cookieFor(String identifier, String password, String clientAddress) {
        AuthService.Session session = auth.login(new LoginRequest(identifier, password), clientAddress);
        MockCookie cookie = new MockCookie(AuthService.COOKIE, session.token());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        return cookie;
    }
}
