package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0022")
public class M0023_CreateTxLogAndStagedBatchRecordIndexes {
    @ChangeSet(order = "001", id = "addSyncariIdIndex", author = "neelesh")
    public void addSyncariIdIndex(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("transactionLog");
        collection.createIndex(new BasicDBObject("syncariId", 1), new IndexOptions().unique(false));
    }
    @ChangeSet(order = "002", id = "addStagedBatchRecordIndex", author = "neelesh")
    public void addStagedBatchRecordIndex(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("stagedBatch");
        collection.createIndex(new BasicDBObject("currentBatchId", 1), new IndexOptions().unique(false));
    }
}

