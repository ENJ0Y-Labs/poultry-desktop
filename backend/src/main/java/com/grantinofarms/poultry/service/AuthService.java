package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.AuthSetupRequest;
import com.grantinofarms.poultry.dto.LoginRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.SessionRepository;
import com.grantinofarms.poultry.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AuthService {
    public static final String COOKIE = "poultry_session";
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher passwords;
    private final SecureRandom random = new SecureRandom();
    private static final int MAX_FAILED_LOGINS = 5;
    private static final long LOGIN_WINDOW_MILLIS = ChronoUnit.MINUTES.getDuration().toMillis() * 15;
    private final ConcurrentMap<String, LoginAttempts> loginAttempts = new ConcurrentHashMap<>();

    public AuthService(UserRepository users, SessionRepository sessions, PasswordHasher passwords) {
        this.users = users; this.sessions = sessions; this.passwords = passwords;
    }

    @Transactional
    public Map<String, Object> setup(AuthSetupRequest request) {
        if (users.count() > 0) throw new ApiException(HttpStatus.CONFLICT, "OWNER_ALREADY_EXISTS", "The primary owner account already exists.");
        String email = request.email().trim().toLowerCase();
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        users.insert(id, email, passwords.hash(request.password()), request.fullName().trim(), now);
        return Map.of("id", id, "email", email, "fullName", request.fullName().trim(), "role", "OWNER");
    }

    @Transactional
    public Session login(LoginRequest request) {
        return login(request, "unknown");
    }

    @Transactional
    public Session login(LoginRequest request, String clientAddress) {
        String email = request.email().trim().toLowerCase();
        String address = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
        String attemptKey = address + "|" + email;
        long nowMillis = System.currentTimeMillis();
        LoginAttempts attempts = loginAttempts.compute(attemptKey, (key, current) ->
                current == null || nowMillis - current.windowStartedAt() >= LOGIN_WINDOW_MILLIS
                        ? new LoginAttempts(nowMillis, 0) : current);
        if (attempts.failures() >= MAX_FAILED_LOGINS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                    "Too many unsuccessful login attempts. Wait 15 minutes and try again.");
        }

        Map<String, Object> user = users.findByEmail(email);
        if (user == null || !passwords.matches(request.password(), String.valueOf(user.get("password_hash")))) {
            loginAttempts.compute(attemptKey, (key, current) ->
                    new LoginAttempts(attempts.windowStartedAt(), attempts.failures() + 1));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        loginAttempts.remove(attemptKey);
        if (!"ACTIVE".equals(user.get("status")))
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "This account is suspended.");

        String raw = newToken();
        String now = Instant.now().toString();
        String expires = Instant.now().plus(12, ChronoUnit.HOURS).toString();
        sessions.purgeExpired(now);
        sessions.insert(UUID.randomUUID().toString(), String.valueOf(user.get("id")), hashToken(raw), now, expires);
        return new Session(raw, Map.of(
                "id", user.get("id"), "email", user.get("email"),
                "fullName", user.get("full_name"), "role", user.get("role")));
    }

    public Map<String, Object> current(String token) {
        if (token == null || token.isBlank()) return null;
        return sessions.findUserByTokenHash(hashToken(token), Instant.now().toString());
    }

    public Map<String, Object> status(String token) {
        return Map.of(
                "authenticated", current(token) != null,
                "setupRequired", users.count() == 0
        );
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) sessions.delete(hashToken(token));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String token) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private record LoginAttempts(long windowStartedAt, int failures) {}

    public record Session(String token, Map<String, Object> user) {}
}
