package com.syncari.api.rest.controllers.data;

import java.util.Map;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WebhookTestRequest {
	private AuthType authType;
	private AuthConfig authConfig;
    private String schema;
    private String body;
    private String idSelector;
    private String recordSelector;
    private Map<String, Object> headers;
    private Integer responseCode;
    private String responseTemplate;
}
