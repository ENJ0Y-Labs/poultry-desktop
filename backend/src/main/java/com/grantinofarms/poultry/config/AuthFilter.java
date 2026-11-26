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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        boolean stateChanging = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
        if (stateChanging && !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"ok":false,"error":{"code":"CSRF_CHECK_FAILED","message":"The request could not be verified."}}
                    """);
            return;
        }

        String path = request.getRequestURI();
        boolean publicEndpoint = path.startsWith("/api/v1/auth/") || path.equals("/api/v1/health");
        boolean needsFilterSession = !publicEndpoint || path.equals("/api/v1/auth/me");
        String rawSessionToken = token(request);
        Map<String, Object> user = needsFilterSession ? auth.current(rawSessionToken) : null;
        if (user != null) {
            request.setAttribute("poultry.user", user);
            CurrentUserContext.set(String.valueOf(user.get("id")), String.valueOf(user.get("role")),
                    auth.hashToken(rawSessionToken));
        }

        boolean required = environment.getProperty("poultry.auth-required", Boolean.class, false);
        try {
            if (user != null && "WORKER".equals(user.get("role")) && !workerEndpoint(path)) {
                denyRole(response);
                return;
            }
            if (user != null && "ADMINISTRATOR".equals(user.get("role")) && !administratorEndpoint(path)) {
                denyRole(response);
                return;
            }
            if (required && !publicEndpoint) {
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

    private void denyRole(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("""
                {"ok":false,"error":{"code":"ROLE_FORBIDDEN","message":"Your account does not have permission to access this resource."}}
                """);
    }

    private boolean workerEndpoint(String path) {
        return (path.equals("/api/v1/submissions") || path.startsWith("/api/v1/submissions/"))
                || (path.equals("/api/v1/account") || path.startsWith("/api/v1/account/"))
                || path.equals("/api/v1/auth/me")
                || path.equals("/api/v1/health")
                || path.matches("^/api/v1/review/submissions/[^/]+/comments$");
    }

    private boolean administratorEndpoint(String path) {
        return (path.equals("/api/v1/submissions") || path.startsWith("/api/v1/submissions/"))
                || (path.equals("/api/v1/admin/users") || path.startsWith("/api/v1/admin/users/"))
                || (path.equals("/api/v1/account") || path.startsWith("/api/v1/account/"))
                || path.equals("/api/v1/auth/me")
                || path.equals("/api/v1/health")
                || path.matches("^/api/v1/review/submissions/[^/]+/comments$");
    }

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> AuthService.COOKIE.equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }
}
