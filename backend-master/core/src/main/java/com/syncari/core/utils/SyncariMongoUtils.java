package com.syncari.core.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SyncariMongoUtils extends MongoUtils {
    @Autowired
    private MongoDbFactory syncariDBFactory;

    @Autowired
    private MongoTemplate syncariMongoTemplate;

    @Override
    public String getDB() {
        return "syncaridb";
    }

    @Override
    protected MongoDbFactory getMongoDBFactory() {
        return syncariDBFactory;
    }

    @Override
    protected MongoTemplate getMongoTemplate() {
        return syncariMongoTemplate;
    }
}
