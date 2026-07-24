package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Credential {
	private String id;
	private String name;
	private String key;
	private String type;
	private String username;
	private String password;
    private String clientId;
	private String clientSecret;
	private String endPoint;
}
