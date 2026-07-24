package com.syncari.connector.data;

public enum AuthType {
	UserPassword,
	UserPasswordToken,
	ApiKey,
	BearerToken,
	ApiSecretKey,
	Oauth,
	OauthToken,
	SimpleOAuth,
	OneClickOAuth,
	NetSuiteTokenBasedAuthentication,
	Custom,
	PrivateKey,
	None,
	Hmac
}
