package com.syncari.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@ComponentScan(basePackages = {"com.syncari.connector"})
public class ConnectorConfig {
    public static final int READ_TIMEOUT = 30000;
    public static final int CONNECT_TIMEOUT = 10000;
    public static final int CONNECT_REQUEST_TIMEOUT = 10000;

    @Bean
    public RestTemplate restTemplate() {
        // Do any additional configuration here
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        clientHttpRequestFactory.setReadTimeout(READ_TIMEOUT);
        clientHttpRequestFactory.setConnectionRequestTimeout(CONNECT_REQUEST_TIMEOUT);

        return new RestTemplate(clientHttpRequestFactory);
    }

}
