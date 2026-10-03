package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.AccountUpdateRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class AccountService {
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

    public Map<String,Object> current() {
        String id = requireUserId();
        Map<String,Object> user = users.findById(id);
        if (user == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No active session.");
        return publicUser(user);
    }

    @Transactional
    public Map<String,Object> update(AccountUpdateRequest request) {
        String id = requireUserId();
        Map<String,Object> current = users.findById(id);
        if (current == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No active session.");

        String email = request.email().trim().toLowerCase();
        String fullName = request.fullName().trim();
        boolean changingPassword = request.newPassword() != null && !request.newPassword().isBlank();

        if (changingPassword) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()
                    || !passwords.matches(request.currentPassword(), String.valueOf(current.get("password_hash")))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Current password is incorrect.");
            }
            users.updatePassword(id, passwords.hash(request.newPassword()), Instant.now().toString());
        }

        String oldEmail = String.valueOf(current.get("email"));
        String oldName = String.valueOf(current.get("full_name"));
        try {
            users.updateProfile(id, email, fullName, Instant.now().toString());
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "That email is already in use.");
        }

        var farm = farms.findActive();
        String now = Instant.now().toString();
        audit.append(farm == null ? null : farm.id(), "UPDATE", "USER_ACCOUNT", id,
                "User account settings changed",
                auditJson(oldEmail, oldName),
                auditJson(email, fullName), now);
        return current();
    }

    private String auditJson(String email, String fullName) {
        try {
            return objectMapper.writeValueAsString(Map.of("email", email, "fullName", fullName));
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

    private Map<String,Object> publicUser(Map<String,Object> user) {
        return Map.of("id", user.get("id"), "email", user.get("email"),
                "fullName", user.get("full_name"), "role", user.get("role"));
    }
}
