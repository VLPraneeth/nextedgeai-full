package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@ChangeLog(order = "0069")
public class M0069_IdMappingUpdatedAtIndex {

    @ChangeSet(order = "001", id = "createIndexOnIdMappingUpdatedAt", author = "neelesh")
    public void createIndexOnIdMappingUpdatedAt(MongoTemplate db) {
        MongoUtils.createIndexes(db,"idMapping", List.of(new Index(false,"entityName","updatedAt")));
    }
}



