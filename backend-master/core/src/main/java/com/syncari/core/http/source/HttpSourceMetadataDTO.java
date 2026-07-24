package com.syncari.core.http.source;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.AuthType;
import com.syncari.utils.KeyValue;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpSourceMetadataDTO{
	private String name;
	private String displayName;
	private AuthType authType;
	private AuthConfig authConfig;
    private String endpoint;
    private String method;
    private String body;
    private Map<String, String> headers;
    private List<KeyValue> variables;
    private List<KeyValue> variableValues;
    private MultipartFile icon;
}
