package com.syncari.connector.custom;

import lombok.Data;

@Data
public class SynapseErrorResponse {
    String message;
    String detail;
    int status_code;

    public SynapseErrorResponse() {
    }
}
