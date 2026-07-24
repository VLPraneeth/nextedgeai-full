package com.syncari.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.rest.SyncariEntityDataRestClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Slf4j
public class EloquaRestClient extends SyncariEntityDataRestClient {

    private static final int WAIT_TIMEOUT_MILLIS = 90000;

    public EloquaRestClient(ProxyConfig proxy) {
		super(proxy);
	}

    public EloquaRestClient(JsonParserConfig jsonParserConfig, ObjectMapper objectMapper) {
        super(jsonParserConfig, objectMapper);
    }

    public EloquaRestClient() {
		super();
	}

    @Override
    public RestTemplate getTemplate() {
    	HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
		if(proxy.isPresent() && StringUtils.isNotEmpty(proxy.get().getHost())) {
			HttpHost httpProxy = new HttpHost(proxy.get().getHost(), proxy.get().getPort());
			httpClientBuilder.setProxy(httpProxy);
			log.debug("EloquaRestClient Setting proxy with {} {}", proxy.get().getHost(), proxy.get().getPort());
		}
        CloseableHttpClient client =
                httpClientBuilder.build();
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
        clientHttpRequestFactory.setConnectTimeout(WAIT_TIMEOUT_MILLIS);
        clientHttpRequestFactory.setReadTimeout(WAIT_TIMEOUT_MILLIS);
        return new RestTemplate(clientHttpRequestFactory);
    }
}
