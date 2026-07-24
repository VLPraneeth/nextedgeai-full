package com.syncari.api.rest.config.security;

public final class SecurityConstants {

    public static final String AUTH_LOGIN_URL = "/api/v1/authenticate";
    public static final String SET_PASSWORD = "/api/v1/user/setpassword/*";
    public static final String FORGOT_PASSWORD = "/api/v1/user/forgotPassword";
    public static final String ERROR_NOTIFICATION_ACCEPT_INVITE = "/api/v1/errorNotifications/invitation/**";

    // JWT token defaults
    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String TOKEN_TYPE = "JWT";
    public static final String TOKEN_ISSUER = "secure-api";
    public static final String TOKEN_AUDIENCE = "secure-app";
    public static final Long TOKEN_EXPIRATION = 3600000L;

    private SecurityConstants() {
        throw new IllegalStateException("Cannot create instance of static util class");
    }
}