package com.grantinofarms.poultry;

import com.grantinofarms.poultry.dto.AuthSetupRequest;
import com.grantinofarms.poultry.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:file:lan-host-origin-test?mode=memory&cache=shared",
        "poultry.database-path=:memory:",
        "poultry.auth-required=true",
        "poultry.lan.enabled=true",
        "server.address=127.0.0.1"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LanHostOriginSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;

    private RequestPostProcessor lanRequest() {
        return request -> {
            request.setScheme("http");
            request.setServerName("127.0.0.1");
            request.setServerPort(80);
            request.setLocalAddr("127.0.0.1");
            request.setLocalPort(80);
            return request;
        };
    }

    @Test
    void acceptsOnlyTheExactBoundHost() throws Exception {
        mvc.perform(get("/api/v1/health").with(lanRequest()).header("Host", "127.0.0.1"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/health").header("Host", "attacker.example"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/health").header("Host", "127.0.0.1:9999"))
                .andExpect(status().isForbidden());
    }


    @Test
    void allowsUnauthenticatedBrowserShellAssetsButKeepsApiAuthenticationRequired() throws Exception {
        auth.setup(new AuthSetupRequest("lan-shell@example.test", "Manager-password-123!", "LAN Shell Test"));
        mvc.perform(get("/assets/not-present.js").with(lanRequest())
                        .header("Host", "127.0.0.1"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/farm").with(lanRequest())
                        .header("Host", "127.0.0.1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsOpaqueAndCrossOriginBrowserRequests() throws Exception {
        mvc.perform(get("/api/v1/health").with(lanRequest())
                        .header("Host", "127.0.0.1")
                        .header("Origin", "null"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/health")
                        .header("Host", "127.0.0.1")
                        .header("Origin", "http://attacker.example"))
                .andExpect(status().isForbidden());
    }


    @Test
    void sameOriginLanLoginUsesHostOnlyLaxCookieWithoutSecureOnHttp() throws Exception {
        auth.setup(new AuthSetupRequest("lan-manager@example.test", "Manager-password-123!", "LAN Manager"));

        mvc.perform(post("/api/v1/auth/login").with(lanRequest())
                        .header("Host", "127.0.0.1")
                        .header("Origin", "http://127.0.0.1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content("""
                                {"loginIdentifier":"lan-manager","password":"Manager-password-123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Set-Cookie", not(containsString("Secure"))));
    }

    @Test
    void requiresExactSameOriginAndCsrfMarkerForWrites() throws Exception {
        auth.setup(new AuthSetupRequest("lan-write@example.test", "Manager-password-123!", "LAN Write Test"));
        String body = """
                {"name":"LAN test farm","location":"Test","timezone":"Africa/Lagos","currency":"NGN"}
                """;

        mvc.perform(post("/api/v1/farm").with(lanRequest())
                        .header("Host", "127.0.0.1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/farm").with(lanRequest())
                        .header("Host", "127.0.0.1")
                        .header("Origin", "http://127.0.0.1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/farm")
                        .header("Host", "127.0.0.1")
                        .header("Origin", "http://attacker.example")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/farm")
                        .header("Host", "127.0.0.1")
                        .header("Origin", "http://127.0.0.1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBadHostBeforeCorsPreflightIsProcessed() throws Exception {
        mvc.perform(options("/api/v1/farm").with(lanRequest())
                        .header("Host", "attacker.example")
                        .header("Origin", "http://127.0.0.1")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
