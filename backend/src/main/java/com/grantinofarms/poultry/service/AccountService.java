package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.AccountUpdateRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AccountService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{2,31}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository users;
    private final PasswordHasher passwords;
    private final FarmRepository farms;
    private final AuditRepository audit;
    private final ObjectMapper objectMapper;

    public AccountService(UserRepository users, PasswordHasher passwords, FarmRepository farms,
                          AuditRepository audit, ObjectMapper objectMapper) {
        this.users = users;
        this.passwords = passwords;
        this.farms = farms;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> current() {
        String id = requireUserId();
        Map<String, Object> user = users.findById(id);
        if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No active session.");
        return publicUser(user);
    }

    @Transactional
    public Map<String, Object> update(AccountUpdateRequest request) {
        String id = requireUserId();
        Map<String, Object> current = users.findById(id);
        if (current == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No active session.");

        String username = request.username() == null ? "" : request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        String fullName = request.fullName().trim();

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME",
                    "Username must be 3–32 characters and use letters, numbers, dots, underscores, or hyphens.");
        }
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Enter a valid email address or leave it blank.");
        }
        Map<String, Object> usernameOwner = users.findByUsername(username);
        if (usernameOwner != null && !id.equals(String.valueOf(usernameOwner.get("id")))) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "That username is already in use.");
        }

        boolean changingPassword = request.newPassword() != null && !request.newPassword().isBlank();
        if (changingPassword) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()
                    || !passwords.matches(request.currentPassword(), String.valueOf(current.get("password_hash")))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Current password is incorrect.");
            }
            users.updatePassword(id, passwords.hash(request.newPassword()), Instant.now().toString());
        }

        String oldUsername = String.valueOf(current.get("username"));
        String oldEmail = current.get("email") == null ? null : String.valueOf(current.get("email"));
        String oldName = String.valueOf(current.get("full_name"));
        try {
            users.updateProfile(id, username, email, fullName, Instant.now().toString());
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_IDENTIFIER_ALREADY_EXISTS",
                    "That username or email is already in use.");
        }

        var farm = farms.findActive();
        String now = Instant.now().toString();
        audit.append(farm == null ? null : farm.id(), "UPDATE", "USER_ACCOUNT", id,
                "User account settings changed",
                auditJson(oldUsername, oldEmail, oldName),
                auditJson(username, email, fullName), now);
        return current();
    }

    private String auditJson(String username, String email, String fullName) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("username", username);
            details.put("email", email);
            details.put("fullName", fullName);
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize account audit details.", e);
        }
    }

    private String requireUserId() {
        String id = CurrentUserContext.get();
        if (id == null || id.isBlank())
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No active session.");
        return id;
    }

    private Map<String, Object> publicUser(Map<String, Object> user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.get("id"));
        result.put("username", user.get("username"));
        result.put("email", user.get("email"));
        result.put("fullName", user.get("full_name"));
        result.put("role", user.get("role"));
        return result;
    }
}
