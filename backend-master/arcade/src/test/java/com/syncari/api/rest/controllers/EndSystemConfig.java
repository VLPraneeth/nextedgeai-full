package com.syncari.api.rest.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
public class EndSystemConfig {
	@Value("${salesforce.url}")
	String salesforceUrl;

	@Value("${salesforce.user}")
	private String user;
	
	@Value("${salesforce.password}")
	private String password;
	
	@Value("${salesforce.token}")
	private String token;
	
	@Value("${salesforce.nopermission.user}")
	private String nopermissionUser;
	
	@Value("${salesforce.nopermission.password}")
	private String nopermissionPassword;
	
	@Value("${salesforce.nopermission.token}")
	private String nopermissionToken;
}
