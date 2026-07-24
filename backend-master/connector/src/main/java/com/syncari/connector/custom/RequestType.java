package com.syncari.connector.custom;

import lombok.ToString;

@ToString
public enum RequestType {
    SYNAPSE_INFO,
    TEST,
    DESCRIBE,
    READ,
    GET_BY_ID,
    CREATE,
    UPDATE,
    DELETE,
    EXTRACT_WEBHOOK_IDENTIFIER,
    PROCESS_WEBHOOK,
    HTTP_POST,
    HTTP_PUT,
    HTTP_DELETE,
    REFRESH_TOKEN,
    GET_ACCESS_TOKEN,
    GET_HEADERS,
    SEARCH
}
