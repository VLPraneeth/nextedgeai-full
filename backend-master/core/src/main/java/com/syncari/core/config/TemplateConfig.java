package com.syncari.core.config;

import com.syncari.core.template.JTwigTemplateRenderer;
import com.syncari.core.template.TemplateRenderer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TemplateConfig {

    @Bean
    public TemplateRenderer renderer() {
        return new JTwigTemplateRenderer();
    }

    @Bean
    public RestTemplate llmRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .setConnectTimeout(300000)
                .setReadTimeout(600000)
                .build();
    }

}
