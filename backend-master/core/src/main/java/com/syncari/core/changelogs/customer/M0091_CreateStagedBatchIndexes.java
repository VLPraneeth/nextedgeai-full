package com.syncari.core.changelogs.customer;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;

@ChangeLog(order = "0091")
public class M0091_CreateStagedBatchIndexes {
    @ChangeSet(order = "001", id = "addStagedBatchEntityNameCreatedAtIndex", author = "sibin")
    public void addStagedBatchRecordIndex(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("stagedBatch");
    	collection.createIndex(Indexes.descending("entityName", "createdAt"));
    }
}

