package com.syncari.core;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.syncari.core.config.DatatypeMixin;
import com.syncari.core.config.FilterFailedResultSerializer;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.pipeline.FilterFailedResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ComponentScan(basePackages = "com.syncari")
public class JsonConfig {

    @Bean
    public com.fasterxml.jackson.databind.Module datatypeSerializer() {
        log.info("Registering datatype serializer");
        SimpleModule module = new SimpleModule();
        module.setMixInAnnotation(Datatype.class, DatatypeMixin.class);
        log.info("Registered datatype serializer");
        return module;
    }

    @Bean
    public com.fasterxml.jackson.databind.Module filterFailedSerializer() {
        log.info("Registering filterFailed serializer");
        SimpleModule module = new SimpleModule();
        module.addSerializer(FilterFailedResult.class, new FilterFailedResultSerializer());
        return module;
    }
}
