package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.AuthService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerCookieTest {

    @Test
    void loginCookieHasRequiredAttributesAndNoClientSideExpiry() {
        String header = AuthController.sessionCookieHeader("test-session-token");

        assertEquals(AuthService.COOKIE + "=test-session-token; Path=/; HttpOnly; SameSite=None; Secure", header);
        assertTrue(header.contains("; Path=/"));
        assertTrue(header.contains("; HttpOnly"));
        assertTrue(header.contains("; SameSite=None"));
        assertTrue(header.endsWith("; Secure"));
        assertTrue(!header.contains("Max-Age="));
        assertTrue(!header.contains("Expires="));
    }

    @Test
    void logoutCookieClearsTheSameCookieScopeAndSecurityAttributes() {
        assertEquals(AuthService.COOKIE + "=; Path=/; HttpOnly; Max-Age=0; SameSite=None; Secure",
                AuthController.logoutCookieHeader());
    }
}
