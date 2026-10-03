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

        var session=auth.login(new LoginRequest("OWNER@example.com","correct-horse-battery"));
        assertThat(session.token()).isNotBlank();
        assertThat(session.user().get("role")).isEqualTo("OWNER");
        assertThat(auth.current(session.token()).get("email")).isEqualTo("owner@example.com");
        auth.logout(session.token());
        assertThat(auth.current(session.token())).isNull();
        CurrentUserContext.clear();
    }
}
