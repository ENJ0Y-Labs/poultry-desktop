package com.grantinofarms.poultry.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieTest {
    @Test
    void sessionOnlyCookieHasNoPersistentMaxAge() {
        String header = AuthController.sessionCookieHeader("opaque-token", false);
        assertThat(header).isEqualTo("poultry_session=opaque-token; Path=/; HttpOnly; SameSite=None; Secure");
        assertThat(header).doesNotContain("Max-Age=");
    }

    @Test
    void persistentCookieExpiresAfterThirtyDaysAndKeepsSecurityAttributes() {
        String header = AuthController.sessionCookieHeader("opaque-token", true);
        assertThat(header).isEqualTo("poultry_session=opaque-token; Path=/; HttpOnly; SameSite=None; Secure; Max-Age=2592000");
    }

    @Test
    void logoutCookieExpiresImmediatelyAndKeepsMatchingScopeAndSecurityAttributes() {
        String header = AuthController.logoutCookieHeader();
        assertThat(header).isEqualTo("poultry_session=; Path=/; HttpOnly; Max-Age=0; SameSite=None; Secure");
    }
}
