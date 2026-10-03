package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.AuthSetupRequest;
import com.grantinofarms.poultry.dto.LoginRequest;
import com.grantinofarms.poultry.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/setup")
    ResponseEntity<?> setup(@Valid @RequestBody AuthSetupRequest request) {
        return ResponseEntity.ok(Map.of("ok", true, "data", service.setup(request)));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.Session session = service.login(request);
        response.addHeader("Set-Cookie", AuthService.COOKIE + "=" + session.token() + "; Path=/; HttpOnly; SameSite=Strict");
        return ResponseEntity.ok(Map.of("ok", true, "data", session.user()));
    }

    @PostMapping("/logout")
    ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        jakarta.servlet.http.Cookie cookie = request.getCookies() == null ? null :
                Arrays.stream(request.getCookies()).filter(c -> AuthService.COOKIE.equals(c.getName())).findFirst().orElse(null);
        service.logout(cookie == null ? null : cookie.getValue());
        response.addHeader("Set-Cookie", AuthService.COOKIE + "=; Path=/; HttpOnly; Max-Age=0; SameSite=Strict");
        return ResponseEntity.ok(Map.of("ok", true, "data", Map.of()));
    }

    @GetMapping("/me")
    ResponseEntity<?> me(HttpServletRequest request) {
        Object value = request.getAttribute("poultry.user");
        if (!(value instanceof Map<?, ?> map)) return ResponseEntity.status(401).body(Map.of(
                "ok", false, "error", Map.of("code", "UNAUTHENTICATED", "message", "No active session.")));
        return ResponseEntity.ok(Map.of("ok", true, "data", map));
    }
}
