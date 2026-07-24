package com.syncari.viper;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {MongoDataAutoConfiguration.class, DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class})
@EnableRetry
@ComponentScan(basePackages = "com.syncari")
@EnableScheduling
@Slf4j
public class ViperApp {

    public static void main(String[] args) {

        SpringApplication application = new SpringApplication(ViperApp.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.run(args);

    }


    @EventListener
    public void onApplicationEvent(ApplicationFailedEvent failure) {
        log.error("Error Starting Viper",failure.getException());
        System.exit(1);
    }
}

