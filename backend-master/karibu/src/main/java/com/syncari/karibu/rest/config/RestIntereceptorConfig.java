package com.syncari.karibu.rest.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class RestIntereceptorConfig implements WebMvcConfigurer {

    @Autowired
    ControllerInterceptor interceptor;
    @Autowired
    RealtimeIpWhiteListInterceptor realtimeIpWhiteListInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(interceptor);
        registry.addInterceptor(realtimeIpWhiteListInterceptor).addPathPatterns("/api/v1/realtime/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

}
