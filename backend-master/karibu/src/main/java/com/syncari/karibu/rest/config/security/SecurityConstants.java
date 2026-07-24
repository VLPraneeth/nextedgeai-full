package com.syncari.karibu.rest.config.security;

public final class SecurityConstants {

    public static final String AUTH_LOGIN_URL = "/api/v1/authenticate";
    public static final String SET_PASSWORD = "/api/v1/user/setpassword/*";
    public static final String FORGOT_PASSWORD = "/api/v1/user/forgotPassword";

    // JWT token defaults
    public static final String TOKEN_HEADER = "Authorization";
    public static final String SYNCARI_ID = "syncariId";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String TOKEN_TYPE = "JWT";
    public static final String TOKEN_ISSUER = "secure-api";
    public static final String TOKEN_AUDIENCE = "secure-app";
    public static final Long TOKEN_EXPIRATION = 10800000L; // 3600000L;

    // Authentication Errors
    public static final String EXPIRED_TOKEN = "Expired Token. A valid token is required to access this resource";


    private SecurityConstants() {
        throw new IllegalStateException("Cannot create instance of static util class");
    }
}
