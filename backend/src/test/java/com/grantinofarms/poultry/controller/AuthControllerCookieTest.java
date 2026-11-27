package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
    void sameOriginLanHttpUsesLaxWithoutSecureSoBrowsersAcceptTheHostOnlyCookie() {
        String header = AuthController.sessionCookieHeader("lan-token", false, true, false);

        assertEquals(AuthService.COOKIE + "=lan-token; Path=/; HttpOnly; SameSite=Lax", header);
    }

    @Test
    void sameOriginHttpsKeepsSecureAndUsesLax() {
        String header = AuthController.sessionCookieHeader("lan-token", true, true, true);

        assertEquals(AuthService.COOKIE + "=lan-token; Path=/; HttpOnly; SameSite=Lax; Secure; Max-Age=2592000",
                header);
    }

    @Test
    void sameOriginLogoutClearsTheMatchingLanCookieScope() {
        assertEquals(AuthService.COOKIE + "=; Path=/; HttpOnly; Max-Age=0; Path=/; HttpOnly; SameSite=Lax",
                AuthController.logoutCookieHeader(true, false));
    }

    @Test
    void onlyExactHttpOriginAndHostAreTreatedAsSameOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("192.168.1.20");
        request.setServerPort(18942);
        request.addHeader("Origin", "http://192.168.1.20:18942");

        assertTrue(AuthController.isSameOriginBrowserRequest(request));

        request.addHeader("Origin", "http://192.168.1.21:18942");
        assertTrue(!AuthController.isSameOriginBrowserRequest(request));
    }

    @Test
    void opaqueAndMissingOriginsKeepTheExistingDesktopCookiePolicy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "null");
        assertTrue(!AuthController.isSameOriginBrowserRequest(request));

        request.removeHeader("Origin");
        assertTrue(!AuthController.isSameOriginBrowserRequest(request));
    }

    @Test
    void logoutCookieClearsTheSameCookieScopeAndSecurityAttributes() {
        assertEquals(AuthService.COOKIE + "=; Path=/; HttpOnly; Max-Age=0; SameSite=None; Secure",
                AuthController.logoutCookieHeader());
    }
}
