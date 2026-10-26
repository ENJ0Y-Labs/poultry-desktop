package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.dto.AuthSetupRequest;
import com.grantinofarms.poultry.dto.LoginRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.SessionRepository;
import com.grantinofarms.poultry.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final String COOKIE = "poultry_session";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{2,31}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher passwords;
    private final SecureRandom random = new SecureRandom();
    private static final int MAX_FAILED_LOGINS = 5;
    private static final long SESSION_ONLY_HOURS = 12;
    private static final long PERSISTENT_SESSION_DAYS = 30;
    private static final long LOGIN_WINDOW_MILLIS = ChronoUnit.MINUTES.getDuration().toMillis() * 15;
    private final ConcurrentMap<String, LoginAttempts> loginAttempts = new ConcurrentHashMap<>();

    public AuthService(UserRepository users, SessionRepository sessions, PasswordHasher passwords) {
        this.users = users; this.sessions = sessions; this.passwords = passwords;
    }

    @Transactional
    public Map<String, Object> setup(AuthSetupRequest request) {
        long totalStarted = System.nanoTime();
        long stageStarted = System.nanoTime();
        long ownerCount = users.count();
        long ownerCountMs = elapsedMs(stageStarted);
        if (ownerCount > 0) {
            log.info("auth_setup_timing outcome=owner_exists ownerCountMs={} totalMs={}",
                    ownerCountMs, elapsedMs(totalStarted));
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_ALREADY_EXISTS", "The primary owner account already exists.");
        }

        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username(), email);
        validateUsername(username);
        validateEmail(email);
        if (users.findByUsername(username) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "That username is already in use.");
        }

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        stageStarted = System.nanoTime();
        String passwordHash = passwords.hash(request.password());
        long passwordHashMs = elapsedMs(stageStarted);

        stageStarted = System.nanoTime();
        try {
            users.insert(id, username, email, passwordHash, request.fullName().trim(), now);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_IDENTIFIER_ALREADY_EXISTS",
                    "That username or email is already in use.");
        }
        long sqliteWriteMs = elapsedMs(stageStarted);

        log.info("auth_setup_timing outcome=success ownerCountMs={} passwordHashMs={} sqliteWriteMs={} totalMs={}",
                ownerCountMs, passwordHashMs, sqliteWriteMs, elapsedMs(totalStarted));
        return publicUser(id, username, email, request.fullName().trim(), "OWNER");
    }

    @Transactional
    public Session login(LoginRequest request) {
        return login(request, "unknown", request.persistentSession());
    }

    @Transactional
    public Session login(LoginRequest request, String clientAddress) {
        return login(request, clientAddress, request.persistentSession());
    }

    @Transactional
    public Session login(LoginRequest request, String clientAddress, boolean persistentSession) {
        long totalStarted = System.nanoTime();
        String identifier = request.loginIdentifier().trim().toLowerCase(Locale.ROOT);
        if (identifier.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Username/email or password is incorrect.");
        }
        String address = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
        String attemptKey = address + "|" + identifier;
        long nowMillis = System.currentTimeMillis();
        LoginAttempts attempts = loginAttempts.compute(attemptKey, (key, current) ->
                current == null || nowMillis - current.windowStartedAt() >= LOGIN_WINDOW_MILLIS
                        ? new LoginAttempts(nowMillis, 0) : current);
        if (attempts.failures() >= MAX_FAILED_LOGINS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED",
                    "Too many unsuccessful login attempts. Wait 15 minutes and try again.");
        }

        long stageStarted = System.nanoTime();
        Map<String, Object> user = users.findByIdentifier(identifier);
        long userLookupMs = elapsedMs(stageStarted);

        stageStarted = System.nanoTime();
        boolean passwordMatches = user != null
                && passwords.matches(request.password(), String.valueOf(user.get("password_hash")));
        long passwordVerifyMs = elapsedMs(stageStarted);
        if (!passwordMatches) {
            loginAttempts.compute(attemptKey, (key, current) ->
                    new LoginAttempts(attempts.windowStartedAt(), attempts.failures() + 1));
            log.info("auth_login_timing outcome=invalid_credentials userLookupMs={} passwordVerifyMs={} sessionPurgeMs=0 sessionWriteMs=0 totalMs={}",
                    userLookupMs, passwordVerifyMs, elapsedMs(totalStarted));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Username/email or password is incorrect.");
        }
        loginAttempts.remove(attemptKey);
        if (!"ACTIVE".equals(user.get("status")))
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "This account is suspended.");

        String raw = newToken();
        String now = Instant.now().toString();
        String expires = Instant.now().plus(
                persistentSession ? PERSISTENT_SESSION_DAYS : SESSION_ONLY_HOURS,
                persistentSession ? ChronoUnit.DAYS : ChronoUnit.HOURS
        ).toString();
        stageStarted = System.nanoTime();
        sessions.purgeExpired(now);
        long sessionPurgeMs = elapsedMs(stageStarted);

        stageStarted = System.nanoTime();
        sessions.insert(UUID.randomUUID().toString(), String.valueOf(user.get("id")), hashToken(raw), now, expires);
        long sessionWriteMs = elapsedMs(stageStarted);
        log.info("auth_login_timing outcome=success userLookupMs={} passwordVerifyMs={} sessionPurgeMs={} sessionWriteMs={} totalMs={}",
                userLookupMs, passwordVerifyMs, sessionPurgeMs, sessionWriteMs, elapsedMs(totalStarted));
        return new Session(raw, publicUser(
                String.valueOf(user.get("id")), String.valueOf(user.get("username")),
                (String) user.get("email"), String.valueOf(user.get("full_name")), String.valueOf(user.get("role"))));
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

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String requested, String email) {
        String value = requested == null || requested.isBlank()
                ? (email == null ? "" : email.substring(0, email.indexOf('@')))
                : requested.trim();
        return value.toLowerCase(Locale.ROOT);
    }

    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME",
                    "Username must be 3–32 characters and use lowercase letters, numbers, dots, underscores, or hyphens.");
        }
    }

    private void validateEmail(String email) {
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Enter a valid email address or leave it blank.");
        }
    }

    private Map<String, Object> publicUser(String id, String username, String email, String fullName, String role) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("username", username);
        result.put("email", email);
        result.put("fullName", fullName);
        result.put("role", role);
        return result;
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

    private long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private record LoginAttempts(long windowStartedAt, int failures) {}

    public record Session(String token, Map<String, Object> user) {}
}
