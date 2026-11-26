package com.grantinofarms.poultry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantinofarms.poultry.dto.AdminUserCreateRequest;
import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.SessionRepository;
import com.grantinofarms.poultry.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AdminUserService {
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,31}");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher passwords;
    private final AuditRepository audit;
    private final FarmRepository farms;
    private final ObjectMapper mapper;

    public AdminUserService(UserRepository users, SessionRepository sessions, PasswordHasher passwords,
                            AuditRepository audit, FarmRepository farms, ObjectMapper mapper) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.audit = audit;
        this.farms = farms;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public java.util.List<Map<String, Object>> list() {
        RoleAuthorization.requireAdministrator();
        return users.findAccounts();
    }

    @Transactional
    public Map<String, Object> create(AdminUserCreateRequest request) {
        RoleAuthorization.requireAdministrator();
        String actor = CurrentUserContext.get();
        String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (!USERNAME.matcher(username).matches())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME", "Username format is invalid.");
        if (email != null && !EMAIL.matcher(email).matches())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Email format is invalid.");

        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        try {
            users.insertWithRole(id, username, email, passwords.hash(request.password()),
                    request.fullName().trim(), request.role(), now, actor);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_IDENTIFIER_ALREADY_EXISTS",
                    "That username or email is already in use.");
        }
        auditEvent("CREATE", id, null, Map.of("username", username, "role", request.role(), "status", "ACTIVE"), now);
        return users.findAccounts().stream().filter(row -> id.equals(row.get("id"))).findFirst().orElseThrow();
    }

    @Transactional
    public Map<String, Object> changeRole(String userId, String role) {
        RoleAuthorization.requireAdministrator();
        String actor = CurrentUserContext.get();
        if (actor.equals(userId))
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_CHANGE_OWN_ROLE",
                    "You cannot change your own administrator role.");
        Map<String, Object> target = requireUser(userId);
        String oldRole = String.valueOf(target.get("role"));
        if (oldRole.equals(role)) return publicAccount(target);
        if ("ACTIVE".equals(target.get("status")) && "ADMINISTRATOR".equals(oldRole)
                && !"ADMINISTRATOR".equals(role) && users.countActiveAdministratorsExcept(userId) == 0)
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ACTIVE_ADMINISTRATOR",
                    "At least one active Administrator must remain.");
        String now = Instant.now().toString();
        users.updateRole(userId, role, actor, now);
        sessions.deleteForUser(userId);
        auditEvent("ROLE_CHANGED", userId, Map.of("role", oldRole), Map.of("role", role), now);
        return publicAccount(users.findById(userId));
    }

    @Transactional
    public Map<String, Object> changeStatus(String userId, String status) {
        RoleAuthorization.requireAdministrator();
        String actor = CurrentUserContext.get();
        if (actor.equals(userId) && "SUSPENDED".equals(status))
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_SUSPEND_SELF", "You cannot suspend your own account.");
        Map<String, Object> target = requireUser(userId);
        String oldStatus = String.valueOf(target.get("status"));
        if (oldStatus.equals(status)) return publicAccount(target);
        if ("ACTIVE".equals(oldStatus) && "SUSPENDED".equals(status)
                && "ADMINISTRATOR".equals(target.get("role"))
                && users.countActiveAdministratorsExcept(userId) == 0)
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ACTIVE_ADMINISTRATOR",
                    "At least one active Administrator must remain.");
        String now = Instant.now().toString();
        users.updateStatus(userId, status, now);
        if ("SUSPENDED".equals(status)) sessions.deleteForUser(userId);
        auditEvent("STATUS_CHANGED", userId, Map.of("status", oldStatus), Map.of("status", status), now);
        return publicAccount(users.findById(userId));
    }

    @Transactional
    public Map<String, Object> resetPassword(String userId, String password) {
        RoleAuthorization.requireAdministrator();
        Map<String, Object> target = requireUser(userId);
        String now = Instant.now().toString();
        users.updatePassword(userId, passwords.hash(password), now);
        sessions.deleteForUser(userId);
        auditEvent("PASSWORD_RESET", userId, null, Map.of("sessionsRevoked", true), now);
        return publicAccount(users.findById(userId));
    }

    private Map<String, Object> requireUser(String id) {
        Map<String, Object> user = users.findById(id);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account was not found.");
        return user;
    }

    private Map<String, Object> publicAccount(Map<String, Object> user) {
        return Map.of("id", user.get("id"), "username", user.get("username"),
                "email", user.get("email") == null ? "" : user.get("email"),
                "fullName", user.get("full_name"), "role", user.get("role"), "status", user.get("status"));
    }

    private void auditEvent(String action, String userId, Object before, Object after, String now) {
        try {
            var farm = farms.findActive();
            audit.append(farm == null ? null : farm.id(), action, "USER_ACCOUNT", userId,
                    "Administrator account management", before == null ? null : mapper.writeValueAsString(before),
                    after == null ? null : mapper.writeValueAsString(after), now);
        } catch (Exception e) {
            throw new IllegalStateException("Could not record account administration audit event.", e);
        }
    }
}
