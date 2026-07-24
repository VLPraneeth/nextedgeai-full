package com.syncari.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "com.syncari.core.repositories.syncari", 
mongoTemplateRef = "syncariMongoTemplate")

public class SyncariMongoConfig {

}
