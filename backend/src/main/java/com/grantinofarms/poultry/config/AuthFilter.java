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
        String token = token(request);
        Map<String, Object> user = auth.current(token);
        if (user != null) {
            request.setAttribute("poultry.user", user);
            CurrentUserContext.set(String.valueOf(user.get("id")));
        }

        boolean required = environment.getProperty("poultry.auth-required", Boolean.class, false);
        boolean bootstrap = users.count() == 0;
        boolean publicEndpoint = request.getRequestURI().startsWith("/api/v1/auth/")
                || request.getRequestURI().equals("/api/v1/health");
        try {
            if (required && !bootstrap && !publicEndpoint && user == null) {
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"ok\":false,\"error\":{\"code\":\"UNAUTHENTICATED\",\"message\":\"Login is required.\"}}");
                return;
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
