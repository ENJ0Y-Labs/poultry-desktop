package com.grantinofarms.poultry.config;

import com.grantinofarms.poultry.repository.UserRepository;
import com.grantinofarms.poultry.service.AuthService;
import com.grantinofarms.poultry.service.CurrentUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

@Component
public class AuthFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final UserRepository users;
    private final Environment environment;

    public AuthFilter(AuthService auth, UserRepository users, Environment environment) {
        this.auth = auth; this.users = users; this.environment = environment;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                && !environment.getProperty("poultry.lan.enabled", Boolean.class, false);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean lanEnabled = environment.getProperty("poultry.lan.enabled", Boolean.class, false);
        if (lanEnabled) {
            if (!hasExpectedHost(request)) {
                denyRequest(response, "HOST_VALIDATION_FAILED", "The request host is not served by this Pomas instance.");
                return;
            }
            String origin = request.getHeader("Origin");
            if (origin != null && !isExactSameOrigin(request, origin)) {
                denyRequest(response, "ORIGIN_VALIDATION_FAILED", "The request origin could not be verified.");
                return;
            }
            if (isStateChanging(request.getMethod())
                    && (origin == null || !isExactSameOrigin(request, origin))) {
                denyRequest(response, "ORIGIN_VALIDATION_FAILED", "State-changing LAN requests must come from this Pomas origin.");
                return;
            }
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (isStateChanging(request.getMethod())
                && !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            denyRequest(response, "CSRF_CHECK_FAILED", "The request could not be verified.");
            return;
        }

        String path = request.getRequestURI();
        boolean apiRequest = path.equals("/api") || path.startsWith("/api/");
        boolean browserAsset = lanEnabled && !apiRequest;
        boolean publicEndpoint = path.startsWith("/api/v1/auth/") || path.equals("/api/v1/health");
        boolean needsFilterSession = apiRequest && (!publicEndpoint || path.equals("/api/v1/auth/me"));
        String rawSessionToken = token(request);
        Map<String, Object> user = needsFilterSession ? auth.current(rawSessionToken) : null;
        if (user != null) {
            request.setAttribute("poultry.user", user);
            CurrentUserContext.set(String.valueOf(user.get("id")), String.valueOf(user.get("role")),
                    auth.hashToken(rawSessionToken));
        }

        boolean required = environment.getProperty("poultry.auth-required", Boolean.class, false);
        try {
            if (user != null && "WORKER".equals(user.get("role")) && !workerEndpoint(path) && !browserAsset) {
                denyRole(response);
                return;
            }
            if (required && !publicEndpoint && !browserAsset) {
                boolean bootstrap = users.count() == 0;
                if (!bootstrap && user == null) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                            {"ok":false,"error":{"code":"UNAUTHENTICATED","message":"Login is required."}}
                            """);
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }

    private boolean hasExpectedHost(HttpServletRequest request) {
        String rawHost = request.getHeader("Host");
        if (rawHost == null || rawHost.isBlank() || rawHost.indexOf(',') >= 0) return false;
        try {
            URI parsed = URI.create("http://" + rawHost);
            if (parsed.getHost() == null || parsed.getRawUserInfo() != null
                    || (parsed.getRawPath() != null && !parsed.getRawPath().isEmpty())
                    || parsed.getRawQuery() != null || parsed.getRawFragment() != null) return false;
            int port = parsed.getPort() >= 0 ? parsed.getPort() : 80;
            return parsed.getHost().equalsIgnoreCase(request.getLocalAddr())
                    && port == request.getLocalPort();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isExactSameOrigin(HttpServletRequest request, String origin) {
        if (origin == null || origin.isBlank() || "null".equalsIgnoreCase(origin)) return false;
        try {
            URI parsed = URI.create(origin);
            if (parsed.getScheme() == null || parsed.getHost() == null
                    || parsed.getRawUserInfo() != null
                    || (parsed.getRawPath() != null && !parsed.getRawPath().isEmpty())
                    || parsed.getRawQuery() != null || parsed.getRawFragment() != null) return false;
            int expectedPort = parsed.getPort() >= 0 ? parsed.getPort()
                    : ("https".equalsIgnoreCase(parsed.getScheme()) ? 443 : 80);
            return parsed.getScheme().equalsIgnoreCase(request.getScheme())
                    && parsed.getHost().equalsIgnoreCase(request.getLocalAddr())
                    && expectedPort == request.getLocalPort();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isStateChanging(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    private void denyRequest(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"ok\":false,\"error\":{\"code\":\"" + code
                + "\",\"message\":\"" + message + "\"}}");
    }

    private void denyRole(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("""
                {"ok":false,"error":{"code":"ROLE_FORBIDDEN","message":"Your account does not have permission to access this resource."}}
                """);
    }

    private boolean workerEndpoint(String path) {
        return (path.equals("/api/v1/submissions") || path.startsWith("/api/v1/submissions/"))
                || (path.equals("/api/v1/incidents") || path.startsWith("/api/v1/incidents/"))
                || (path.equals("/api/v1/account") || path.startsWith("/api/v1/account/"))
                || path.equals("/api/v1/auth/me")
                || path.equals("/api/v1/health")
                || path.equals("/api/v1/review/notifications")
                || path.startsWith("/api/v1/review/notifications/")
                || path.matches("^/api/v1/review/submissions/[^/]+/comments$");
    }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> AuthService.COOKIE.equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }
}