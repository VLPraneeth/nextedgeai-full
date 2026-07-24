package com.syncari.karibu.rest.config;

import java.util.Arrays;
import java.util.List;

public final class KaribuConstants {

    // Server Errors
    public static final String INTERNAL_ERROR = "Internal Server Error";


    // Server Errors
    public static final String SERVICE_UNAVAILABLE = "Service Unavailable Please Retry";

    // Authentication Errors
    public static final String EXPIRED_TOKEN_ERROR = "Expired Token. A valid token is required to access this resource";

    // cursor pagination
    public static final int MAX_LIMIT = 100;
    public static final String MAX_LIMIT_STRING = "100";

    // Synapses that support field creation
    public static final List<String> SYNAPSES_THAT_SUPPORT_FIELD_CREATION = Arrays.asList("syncari", "marketo", "amazon_dynamo_db", "postgresql");

    // Synapses that support field update
    public static final List<String> SYNAPSES_THAT_SUPPORT_FIELD_UPDATE = Arrays.asList("syncari");

    // standard date format
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
}
