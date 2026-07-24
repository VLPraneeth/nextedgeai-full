package com.syncari.karibu.rest.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class KaribuConfig {
    @Value("${viper.api.endpoint}")
    String viperApiEndpoint;
}
