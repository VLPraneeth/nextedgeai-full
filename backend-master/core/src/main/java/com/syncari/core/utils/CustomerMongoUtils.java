package com.syncari.core.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.syncari.core.SyncariContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
    public class CustomerMongoUtils extends MongoUtils {
    @Autowired
    private MongoDbFactory customerDBFactory;

    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public String getDB() {
        return SyncariContext.getDatabase();
    }

    @Override
    protected MongoDbFactory getMongoDBFactory() {
        return customerDBFactory;
    }

    @Override
    protected MongoTemplate getMongoTemplate() {
        return customerMongoTemplate;
    }

}
