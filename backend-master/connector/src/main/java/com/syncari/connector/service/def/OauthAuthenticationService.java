package com.syncari.connector.service.def;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.custom.CloudFunctionInfo;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.OAuthRequest;
import com.syncari.connector.exception.RetriableException;

import java.util.function.Supplier;

public interface OauthAuthenticationService extends AuthenticationService {
	
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	AuthConfig getAccessToken(OAuthRequest oAuthRequest);

	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	AuthConfig refreshToken(ConnectorInfo connector);
	
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	default String getAuthHost(AuthConfig config) {
	    return config.getEndpoint();
	}

	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	default String getAuthHost(AuthConfig config, CloudFunctionInfo cfInfo) {
		return getAuthHost(config);
	}
	
	String getOAuthUri(ConnectorInfo connector);

	default Supplier<AuthConfig> getTokenHandler(ConnectorInfo connector){
		return () -> refreshToken(connector);
	}

	default String getCodeVerifier() {
		return null;
	}

	default String getCodeChallenge(String verifier) {
		return null;
	}
}
