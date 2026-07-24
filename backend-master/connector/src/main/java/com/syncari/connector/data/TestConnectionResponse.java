package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.connector.config.AuthConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class TestConnectionResponse {

    public static final String AUTH_FAILED_MESSAGE = "Authentication failed.";
    
	private String message;
	private String code;
	private List<String> errors = new ArrayList<>();
	private AuthConfig authConfig;
	private Map<String, Object> metaConfig = new HashMap<String, Object>();

	public TestConnectionResponse() {}

	public TestConnectionResponse(String message, String code, List<String> errors) {
		this.message = message;
		this.code = code;
		this.errors = errors;
	}
	
	public boolean isSuccess() {
		return message == null && errors.isEmpty();
	}
}
