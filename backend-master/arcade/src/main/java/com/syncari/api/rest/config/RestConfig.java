package com.syncari.api.rest.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.NodeConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.syncari.api.rest")
public class RestConfig {

    @Bean
    public com.fasterxml.jackson.databind.Module nodeConfigurationDeserializer() {
        SimpleModule module = new SimpleModule();
        module.setMixInAnnotation(NodeConfiguration.class, NodeConfigurationMixin.class);
        return module;
    }

    @Bean
    public com.fasterxml.jackson.databind.Module datatypeDeserializer() {
        SimpleModule module = new SimpleModule();
        module.setMixInAnnotation(Datatype.class, DatatypeMixin.class);
        return module;
    }

//    @Bean
//    public Jackson2ObjectMapperBuilderCustomizer customSerDe(){
//        return new Jackson2ObjectMapperBuilderCustomizer() {
//            @Override
//            public void customize(Jackson2ObjectMapperBuilder jacksonObjectMapperBuilder) {
//                jacksonObjectMapperBuilder.mixIn(NodeConfiguration.class,NodeConfigurationMixin.class);
//            }
//        };
//    }
}

