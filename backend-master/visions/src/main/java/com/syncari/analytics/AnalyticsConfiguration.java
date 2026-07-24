package com.syncari.analytics;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {"com.syncari.analytics.repositories"}, 
mongoTemplateRef = "customerMongoTemplate")

public class AnalyticsConfiguration {

}
