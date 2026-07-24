package com.syncari.connector.auth;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HttpContext;
import org.apache.olingo.client.core.http.AbstractOAuth2HttpClientFactory;
import org.apache.olingo.client.core.http.OAuth2Exception;
import org.apache.olingo.commons.api.http.HttpMethod;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SyncariOauth2HttpClientFactory extends AbstractOAuth2HttpClientFactory {
    
    // The access token obtained by Oauth2 mechanism
    private final String token;
    private final String authority;
    
    public SyncariOauth2HttpClientFactory(String token, final String authority) {
        super(URI.create(authority + "/oauth2/authorize"), URI.create(authority + "/oauth2/token"));
        this.token = token;
        this.authority = authority;
    }

    @Override
    protected boolean isInited() throws OAuth2Exception {
        return token != null;
    }

    @Override
    protected void init() throws OAuth2Exception {
        // Dummy implementation.
        assert(token != null);
    }

    @Override
    protected void accessToken(DefaultHttpClient client) throws OAuth2Exception {
        client.addRequestInterceptor(new HttpRequestInterceptor() {
            @Override
            public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
                request.removeHeaders(HttpHeaders.AUTHORIZATION);
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
        });
    }

    @Override
    protected void refreshToken(DefaultHttpClient client) throws OAuth2Exception {
        // Dummy implementation.
        assert(token != null);
    }

    @Override
    public HttpClient create(final HttpMethod method, final URI uri) {
        final int HTTP_REQUEST_TIMEOUT = 5 * 60 * 1000; 

        if (!isInited()) {
            init();
        }

        final DefaultHttpClient httpClient = wrapped.create(method, uri);
        HttpConnectionParams.setConnectionTimeout(httpClient.getParams(), HTTP_REQUEST_TIMEOUT);
        HttpConnectionParams.setSoTimeout(httpClient.getParams(), HTTP_REQUEST_TIMEOUT);

        accessToken(httpClient);

        httpClient.addRequestInterceptor(new HttpRequestInterceptor() {
            @Override
            public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
                if (request instanceof HttpUriRequest) {
                    currentRequest = (HttpUriRequest) request;
                } else {
                    currentRequest = null;
                }
            }
        });
        httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
            @Override
            public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                    refreshToken(httpClient);

                    if (currentRequest != null) {
                        httpClient.execute(currentRequest);
                    }
                }
            }
        });
        return httpClient;
    }
}
