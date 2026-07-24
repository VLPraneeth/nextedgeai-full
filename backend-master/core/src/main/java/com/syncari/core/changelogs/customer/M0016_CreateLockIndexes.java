package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ChangeLog(order = "0016")
public class M0016_CreateLockIndexes {
    @ChangeSet(order = "001", id = "creatLockIndex", author = "neelesh")
    public void createUniqueIndexes(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("lock");
        IndexOptions keyOpts = new IndexOptions().unique(true);
        BasicDBObject dbObj = new BasicDBObject();
        dbObj.append("lockKey", 1);
        try {
            collection.createIndex(dbObj, keyOpts);
        } catch (Exception e) {
            log.error("{}", ExceptionUtils.getStackTrace(e));
        }
    }
}
