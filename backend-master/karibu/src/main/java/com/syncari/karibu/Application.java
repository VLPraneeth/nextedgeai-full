package com.syncari.karibu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
@ComponentScan(basePackages = "com.syncari")
@EnableAutoConfiguration(exclude = {MongoDataAutoConfiguration.class, DataSourceAutoConfiguration.class})
public class Application {
	public static void main(String[] args) {

		SpringApplication application = new SpringApplication(Application.class);
		application.setWebApplicationType(WebApplicationType.SERVLET);
		application.run(args);

	}

	@EventListener
	public void onApplicationEvent(ApplicationReadyEvent event) {
		//event.getApplicationContext().getBeanFactory().getBean(InitActions.class).run();
	}
}
