package com.grantinofarms.poultry.service;

public final class CurrentUserContext {
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ROLE = new ThreadLocal<>();

    private CurrentUserContext() {}

    public static void set(String userId) {
        set(userId, null);
    }

    public static void set(String userId, String role) {
        USER_ID.set(userId);
        USER_ROLE.set(role);
    }

    public static String get() { return USER_ID.get(); }
    public static String getRole() { return USER_ROLE.get(); }

    public static void clear() {
        USER_ID.remove();
        USER_ROLE.remove();
    }
}
