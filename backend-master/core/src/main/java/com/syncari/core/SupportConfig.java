package com.syncari.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Properties;

@PropertySource("classpath:support/support_urls.properties")
@Component
public class SupportConfig {
    @Autowired
    private Environment supportLinks;

    public String getUrl(String placeholder) {
        return supportLinks.getProperty(placeholder);
    }
}
