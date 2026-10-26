package com.grantinofarms.poultry.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookiePolicyTest {
    @Test
    void loginCookieUsesDocumentedLoopbackRendererPolicyAndSessionExpiry() {
        String header = AuthController.sessionCookieHeader("opaque-token");

        assertThat(header).isEqualTo(
                "poultry_session=opaque-token; Path=/; HttpOnly; SameSite=None; Secure");
        assertThat(header).contains("Path=/", "HttpOnly", "SameSite=None", "Secure");
        assertThat(header).doesNotContain("SameSite=Strict", "Max-Age=", "Expires=");
    }

    @Test
    void logoutCookieClearsTheSameCookieScopeAndSecurityAttributes() {
        String header = AuthController.logoutCookieHeader();

        assertThat(header).isEqualTo(
                "poultry_session=; Path=/; HttpOnly; Max-Age=0; SameSite=None; Secure");
        assertThat(header).contains("Path=/", "HttpOnly", "Max-Age=0", "SameSite=None", "Secure");
    }
}
