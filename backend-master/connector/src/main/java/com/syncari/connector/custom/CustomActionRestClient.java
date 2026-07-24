package com.syncari.connector.custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.config.JsonParserConfig;
import com.syncari.connector.config.ProxyConfig;
import com.syncari.connector.rest.SyncariEntityDataRestClient;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Slf4j
public class CustomActionRestClient extends SyncariEntityDataRestClient {

	public CustomActionRestClient(ObjectMapper objectMapper) {
    	super(new JsonParserConfig(null, null, null, "id", true, null), objectMapper);
    }
    public CustomActionRestClient(ObjectMapper objectMapper, ProxyConfig proxy) {
    	super(new JsonParserConfig(null, null, null, "id", true, null), objectMapper, proxy);
    }

    @Override
    public RestTemplate getTemplate() {
    	return geRestTemplate(true);
    }
    
    @Override
    public RestTemplate getNonRedirectTemplate() {
        return geRestTemplate(false);
	}

    private RestTemplate geRestTemplate(boolean enableRedirect) {
    	if(proxy.isPresent()) {
    		log.info("geRestTemplate with {} {}", proxy.get().getHost(), proxy.get().getPort());
    	}
    	int timeout = 30000;
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
		if(!enableRedirect) {
			httpClientBuilder.disableRedirectHandling();
		}
		if(proxy.isPresent() && StringUtils.isNotEmpty(proxy.get().getHost())) {
        	HttpHost httpProxy = new HttpHost(proxy.get().getHost(), proxy.get().getPort());
        	httpClientBuilder.setProxy(httpProxy);
        	log.debug("Setting proxy with {} {}", proxy.get().getHost(), proxy.get().getPort());
        }
		CloseableHttpClient client =
				httpClientBuilder.build();
		HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
		clientHttpRequestFactory.setConnectTimeout(timeout);
		clientHttpRequestFactory.setReadTimeout(timeout);
		RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory);
		DefaultUriBuilderFactory defaultUriBuilderFactory = new DefaultUriBuilderFactory();
        defaultUriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        restTemplate.setUriTemplateHandler(defaultUriBuilderFactory);
        restTemplate.getMessageConverters().forEach(a -> {
            if (a instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) a).setWriteAcceptCharset(false);
            }
        });
        return restTemplate;
    }
}
