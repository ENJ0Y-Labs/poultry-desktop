package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public final class RoleAuthorization {
    private RoleAuthorization() {}

    public static String requireAuthenticated() {
        String userId = CurrentUserContext.get();
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Login is required.");
        }
        return userId;
    }

    public static void requireAny(String... allowedRoles) {
        requireAuthenticated();
        String role = CurrentUserContext.getRole();
        if (role == null || Arrays.stream(allowedRoles).noneMatch(role::equals)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_FORBIDDEN",
                    "Your account does not have permission to perform this action.");
        }
    }

    public static void requireAdministrator() {
        requireAny("ADMINISTRATOR");
    }

    public static void requireManager() {
        requireAny("MANAGER");
    }

    public static void requireReportSubmitter() {
        requireAny("WORKER", "ADMINISTRATOR", "MANAGER");
    }
}
