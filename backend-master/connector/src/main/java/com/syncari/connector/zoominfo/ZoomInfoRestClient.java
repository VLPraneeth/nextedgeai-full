package com.syncari.connector.zoominfo;

import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.rest.SyncariEntityDataRestClient;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
public class ZoomInfoRestClient extends SyncariEntityDataRestClient {

    private static final int WAIT_TIMEOUT_MILLIS = 60000;
    
    public ZoomInfoRestClient(ProxyConfig proxy) {
		super(proxy);
	}
    
    public ZoomInfoRestClient() {
		super();
	}


    @Override
    public RestTemplate getTemplate() {
    	HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
		if(proxy.isPresent() && StringUtils.isNotEmpty(proxy.get().getHost())) {
			HttpHost httpProxy = new HttpHost(proxy.get().getHost(), proxy.get().getPort());
			httpClientBuilder.setProxy(httpProxy);
			log.debug("ZoomInfoRestClient Setting proxy with {} {}", proxy.get().getHost(), proxy.get().getPort());
		}
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
        clientHttpRequestFactory.setConnectTimeout(WAIT_TIMEOUT_MILLIS);
        clientHttpRequestFactory.setReadTimeout(WAIT_TIMEOUT_MILLIS);
        return new RestTemplate(clientHttpRequestFactory);
    }

    @Override
    public HttpHeaders getHeaders(AuthConfig authConf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization", "Bearer " + authConf.getAccessToken());
        headers.addAll(authHeaders);
        return headers;
    }
}
