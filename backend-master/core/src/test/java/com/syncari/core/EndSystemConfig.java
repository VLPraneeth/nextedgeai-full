package com.syncari.core;

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

	@Value("${salesforce.nopermission.url}")
	private String nopermissionUrl;

	@Value("${salesforce.nopermission.user}")
	private String nopermissionUser;

	@Value("${salesforce.nopermission.password}")
	private String nopermissionPassword;

	@Value("${salesforce.nopermission.token}")
	private String nopermissionToken;

	@Value("${salesforce.test.reset.url}")
	private String sfResetTestUrl;

	@Value("${salesforce.test.reset.user}")
	private String sfResetTestUser;

	@Value("${salesforce.test.reset.password}")
	private String sfResetTestPassword;

	@Value("${salesforce.test.reset.token}")
	private String sfResetTestToken;

	@Value("${netsuite.test.url}")
	public String netsuiteTestUrl;

	@Value("${netsuite.test.user}")
	public String netsuiteTestUser;


	@Value("${netsuite.test.password}")
	public String netsuiteTestPassword;

	@Value("${netsuite.test.consumer.key}")
	public String netsuiteTestConsumerKey;

	@Value("${netsuite.test.consumer.secret}")
	public String netsuiteTestConsumerSecret;

	@Value("${netsuite.test.token.id}")
	public String netsuiteTestTokenId;

	@Value("${netsuite.test.token.secret}")
	public String netsuiteTestTokenSecret;

	@Value("${syncari.salesloft.client.id}")
	public String salesloftTestClientId;

	@Value("${syncari.salesloft.client.secret}")
	public String salesloftTestClientSecret;

    @Value("${syncari.hubspot.client.id}")
	public String hubspotTestClientId;

	@Value("${syncari.hubspot.client.secret}")
	public String hubspotTestClientSecret;

	@Value("${syncari.hubspot.client.refreshToken}")
	public String hubspotTestClientRefreshToken;

    @Value("${syncari.gsuite.client.id}")
	public String gsuiteTestClientId;

	@Value("${syncari.gsuite.client.secret}")
	public String gsuiteTestClientSecret;

	@Value("${httpaction.api_key}")
	public String httpActionAPIKey;

	@Value("${httpaction.simpleoauth.clientId}")
	public String httpTestSimpleOAuthClientId;

	@Value("${httpaction.simpleoauth.clientSecret}")
	public String httpTestSimpleOAuthClientSecret;

}


