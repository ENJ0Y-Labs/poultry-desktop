package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.AuthSetupRequest;
import com.grantinofarms.poultry.dto.LoginRequest;
import com.grantinofarms.poultry.service.AuthService;
import com.grantinofarms.poultry.service.CurrentUserContext;
import com.grantinofarms.poultry.repository.AuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:audit-auth-test?mode=memory&cache=shared"
})
class AuditAndAuthIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditRepository audit;
    @Autowired AuthService auth;

    @Test
    void auditRowsCannotBeUpdatedOrDeleted() {
        String farmId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO farms(id,name,timezone,currency,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
                farmId,"Audit Farm","Africa/Lagos","NGN","ACTIVE","2026-10-03T00:00:00Z","2026-10-03T00:00:00Z");
        String auditId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO audit_logs(id,farm_id,occurred_at,action,entity_type,entity_id) VALUES(?,?,?,?,?,?)",
                auditId,farmId,"2026-10-03T00:00:00Z","CREATE","TEST",auditId);

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET reason='changed' WHERE id=?",auditId)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE id=?",auditId)).isInstanceOf(Exception.class);
    }

    @Test
    void ownerCanSetupAndLogin() {
        var setup=auth.setup(new AuthSetupRequest("owner@example.com","correct-horse-battery","Farm Owner"));
        assertThat(setup.get("email")).isEqualTo("owner@example.com");
        assertThat(setup.get("username")).isEqualTo("owner");

        assertThatThrownBy(() -> auth.setup(
                new AuthSetupRequest("another@example.com", "another-secure-password", "Another Owner")))
                .isInstanceOf(com.grantinofarms.poultry.exception.ApiException.class);
        assertThatThrownBy(() -> auth.login(new LoginRequest("owner@example.com", "wrong-password")))
                .isInstanceOf(com.grantinofarms.poultry.exception.ApiException.class);

        var usernameSession=auth.login(new LoginRequest("OWNER","correct-horse-battery"));
        assertThat(usernameSession.token()).isNotBlank();
        assertThat(usernameSession.user().get("username")).isEqualTo("owner");
        var session=auth.login(new LoginRequest("OWNER@example.com","correct-horse-battery"));
        assertThat(session.token()).isNotBlank();
        assertThat(session.user().get("role")).isEqualTo("OWNER");
        assertThat(auth.current(session.token()).get("email")).isEqualTo("owner@example.com");
        auth.logout(session.token());
        assertThat(auth.current(session.token())).isNull();
        CurrentUserContext.clear();

        jdbc.update("DELETE FROM user_sessions");
        jdbc.update("DELETE FROM users");
        assertThatThrownBy(() -> auth.setup(
                new AuthSetupRequest("x", null, "correct-horse-battery", "Invalid Username")))
                .isInstanceOf(com.grantinofarms.poultry.exception.ApiException.class);

        var usernameOnly = auth.setup(new AuthSetupRequest("Noble_Dev", null,
                "another-secure-password", "Owner Without Email"));
        assertThat(usernameOnly.get("username")).isEqualTo("noble_dev");
        assertThat(usernameOnly.get("email")).isNull();
        var usernameOnlySession = auth.login(new LoginRequest("NOBLE_DEV", "another-secure-password"));
        assertThat(usernameOnlySession.user().get("email")).isNull();
        assertThat(usernameOnlySession.user().get("username")).isEqualTo("noble_dev");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users
                (id, username, email, password_hash, full_name, role, status, created_at, updated_at)
                VALUES (?, ?, NULL, ?, ?, 'OWNER', 'ACTIVE', ?, ?)
                """, UUID.randomUUID().toString(), "NOBLE_DEV", "hash", "Duplicate Owner",
                "2026-10-26T00:00:00Z", "2026-10-26T00:00:00Z"))
                .isInstanceOf(Exception.class);
    }
}
