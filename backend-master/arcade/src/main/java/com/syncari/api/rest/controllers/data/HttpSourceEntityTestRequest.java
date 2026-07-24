package com.syncari.api.rest.controllers.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpSourceEntityTestRequest {
	private String metadataId;
	private AuthType authType;
	private AuthConfig authConfig;
    private String endpoint;
    private String method;
    private String body;
    private Map<String, String> headers;
    private List<KeyValue> variables;
    private List<KeyValue> variableValues;    
}
