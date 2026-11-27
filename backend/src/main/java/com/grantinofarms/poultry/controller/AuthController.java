package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.AuthSetupRequest;
import com.grantinofarms.poultry.dto.LoginRequest;
import com.grantinofarms.poultry.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String COOKIE_ATTRIBUTES = "; Path=/; HttpOnly; SameSite=None; Secure";
    private static final int PERSISTENT_COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/setup")
    ResponseEntity<?> setup(@Valid @RequestBody AuthSetupRequest request) {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.setup(request)));
    }

    @GetMapping("/status")
    ResponseEntity<?> status(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.status(sessionToken(request))));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthService.Session session = service.login(request, httpRequest.getRemoteAddr());
        response.addHeader("Set-Cookie", sessionCookieHeader(session.token(), request.persistentSession(),
                isSameOriginBrowserRequest(httpRequest), httpRequest.isSecure()));
        return ResponseEntity.ok(Map.of("ok", true, "data", session.user()));
    }

    @PostMapping("/logout")
    ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        service.logout(sessionToken(request));
        response.addHeader("Set-Cookie", logoutCookieHeader(
                isSameOriginBrowserRequest(request), request.isSecure()));
        return ResponseEntity.ok(Map.of("ok", true, "data", Map.of()));
    }

    static String sessionCookieHeader(String token) {
        return sessionCookieHeader(token, false);
    }

    static String sessionCookieHeader(String token, boolean persistent) {
        return sessionCookieHeader(token, persistent, false, false);
    }

    static String sessionCookieHeader(String token, boolean persistent,
                                      boolean sameOriginBrowserRequest, boolean secureTransport) {
        String attributes = sameOriginCookieAttributes(sameOriginBrowserRequest, secureTransport);
        return AuthService.COOKIE + "=" + token + attributes
                + (persistent ? "; Max-Age=" + PERSISTENT_COOKIE_MAX_AGE_SECONDS : "");
    }

    static String logoutCookieHeader() {
        return logoutCookieHeader(false, false);
    }

    static String logoutCookieHeader(boolean sameOriginBrowserRequest, boolean secureTransport) {
        if (!sameOriginBrowserRequest) {
            return AuthService.COOKIE + "=; Path=/; HttpOnly; Max-Age=0; SameSite=None; Secure";
        }
        return AuthService.COOKIE + "=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax"
                + (secureTransport ? "; Secure" : "");
    }

    private static String sameOriginCookieAttributes(boolean sameOriginBrowserRequest, boolean secureTransport) {
        if (!sameOriginBrowserRequest) return COOKIE_ATTRIBUTES;
        // A browser app served by the LAN host is same-origin with its API. Lax
        // works on plain HTTP LAN addresses without weakening cross-site cookie
        // protections. Secure is retained whenever the host uses HTTPS.
        return "; Path=/; HttpOnly; SameSite=Lax" + (secureTransport ? "; Secure" : "");
    }

    static boolean isSameOriginBrowserRequest(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank() || "null".equalsIgnoreCase(origin)) return false;
        try {
            URI parsed = URI.create(origin);
            if (parsed.getScheme() == null || parsed.getHost() == null
                    || parsed.getUserInfo() != null
                    || (parsed.getRawPath() != null && !parsed.getRawPath().isEmpty())
                    || parsed.getRawQuery() != null || parsed.getRawFragment() != null) {
                return false;
            }
            String scheme = request.getScheme();
            if (!parsed.getScheme().equalsIgnoreCase(scheme)
                    || !parsed.getHost().equalsIgnoreCase(request.getServerName())) return false;
            int originPort = parsed.getPort() >= 0 ? parsed.getPort()
                    : ("https".equalsIgnoreCase(parsed.getScheme()) ? 443 : 80);
            int requestPort = request.getServerPort();
            return originPort == requestPort;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @GetMapping("/me")
    ResponseEntity<?> me(HttpServletRequest request) {
        Object value = request.getAttribute("poultry.user");
        if (!(value instanceof Map<?, ?> map)) return ResponseEntity.status(401).body(Map.of(
                "ok", false, "error", Map.of("code", "UNAUTHENTICATED", "message", "No active session.")));
        return ResponseEntity.ok(Map.of("ok", true, "data", map));
    }

    private String sessionToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> AuthService.COOKIE.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
