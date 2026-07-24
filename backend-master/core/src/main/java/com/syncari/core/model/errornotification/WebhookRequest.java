package com.syncari.core.model.errornotification;

import java.util.Map;

import org.springframework.http.HttpMethod;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WebhookRequest {
    private HttpMethod method;
    private String url;
    private Map<String, String> headers;
    private String body;
}
