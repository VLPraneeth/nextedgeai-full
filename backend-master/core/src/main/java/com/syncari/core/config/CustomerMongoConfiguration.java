package com.syncari.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {"com.syncari.core.repositories.customer","org.springframework.security.acls.dao"}, 
mongoTemplateRef = "customerMongoTemplate")

public class CustomerMongoConfiguration {

}
