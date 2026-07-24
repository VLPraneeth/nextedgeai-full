package com.syncari.analytics.repositories;

import com.syncari.analytics.model.QueryCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.springframework.data.mongodb.core.query.Criteria.where;

public class CustomQueryCacheRepoImpl implements CustomQueryCacheRepo {
    @Autowired
    private MongoTemplate customerMongoTemplate;

    @Override
    public QueryCache upsert(String key, Object value) {
        Query query = new Query().addCriteria(where("key").is(key));
        Update update = new Update().set("value", value);
        QueryCache cached = customerMongoTemplate.findAndModify(query, update,
                new FindAndModifyOptions().returnNew(true).upsert(true), QueryCache.class);
        return cached;
    }
}
