package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ChangeLog(order = "0022")
public class M0022_CreateQueryCacheUniqueIndex {
    @ChangeSet(order = "001", id = "createQueryCacheUniqeueIndex", author = "varsha")
    public void createQueryCacheUniqeueIndex(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("queryCache");
        collection.deleteMany(new Document());
        collection.createIndex(new BasicDBObject("key", 1), new IndexOptions().unique(true));
    }
}

