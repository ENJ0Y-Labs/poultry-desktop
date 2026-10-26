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
        Map<String, Object> user = needsFilterSession ? auth.current(token(request)) : null;
        if (user != null) {
            request.setAttribute("poultry.user", user);
            CurrentUserContext.set(String.valueOf(user.get("id")));
        }

        boolean required = environment.getProperty("poultry.auth-required", Boolean.class, false);
        try {
            // Public setup/login/status routes do not need the filter's session lookup.
            // Only count users when auth enforcement needs to decide whether bootstrap is active.
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

    private String token(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> AuthService.COOKIE.equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }
}
