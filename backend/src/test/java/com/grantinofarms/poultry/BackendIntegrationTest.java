package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.BatchCreateRequest;
import com.grantinofarms.poultry.dto.FarmCreateRequest;
import com.grantinofarms.poultry.dto.HouseCreateRequest;
import com.grantinofarms.poultry.service.AuthService;
import com.grantinofarms.poultry.service.BatchService;
import com.grantinofarms.poultry.service.FarmService;
import com.grantinofarms.poultry.service.HouseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:phase26-integration?mode=memory&cache=shared",
        "poultry.database-path=:memory:",
        "poultry.auth-required=true"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BackendIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired FarmService farmService;
    @Autowired HouseService houseService;
    @Autowired BatchService batchService;

    @Test
    void protectedApiRejectsAnonymousRequestsAndAcceptsAuthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/batches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        auth.setup(new com.grantinofarms.poultry.dto.AuthSetupRequest(
                "owner@example.com", "correct-horse-battery", "Farm Owner"));

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "owner@example.com",
                                  "password": "correct-horse-battery"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AuthService.COOKIE))
                .andReturn();

        var session = login.getResponse().getCookie(AuthService.COOKIE);
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();

        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));

        mockMvc.perform(get("/api/v1/batches").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void failedBatchCreationRollsBackSequenceBatchPurchaseAndAudit() {
        farmService.create(new FarmCreateRequest(
                "Grantino Farms", "Port Harcourt", "Africa/Lagos", "NGN"));
        var house = houseService.create(new HouseCreateRequest(
                "Layer House", "LH1", null));

        jdbc.execute("""
                CREATE TRIGGER phase26_fail_batch_audit
                BEFORE INSERT ON audit_logs
                BEGIN
                    SELECT RAISE(ABORT, 'forced phase 26 audit failure');
                END
                """);

        var request = new BatchCreateRequest(
                "LAYER",
                LocalDate.of(2026, 10, 3),
                house.id(),
                100,
                null,
                1_000_000L
        );

        assertThatThrownBy(() -> batchService.create(request))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM batches", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM bird_purchases", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_code_sequences", Integer.class)).isZero();

        jdbc.execute("DROP TRIGGER phase26_fail_batch_audit");

        var created = batchService.create(request);

        assertThat(created.code()).isEqualTo("L-2026-001");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM bird_purchases WHERE batch_id = ?",
                Integer.class,
                created.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'BATCH' AND entity_id = ?",
                Integer.class,
                created.id())).isEqualTo(1);
    }
}
