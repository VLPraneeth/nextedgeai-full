package com.syncari.dbm.customscripts.customer;

import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order = "0001")
public class SYN_10991_CreateIndexesForTxLog {
    @ChangeSet(order = "001", id = "createIndexForTxnOperationEntityNameAndIdAndCreatedAt", author = "sibin")
    public void createIndexForTxnOperationEntityNameAndCreatedAt(MongoTemplate db) {
    	MongoCollection<Document> collection = db.getCollection("transactionLog");
        if(!MongoUtils.isIndexExist(db, "transactionLog", "operation_1_entityName_1__id_-1_createdAt_-1")) {
        	collection.createIndex(Indexes.compoundIndex(Indexes.ascending("operation", "entityName"), Indexes.descending("_id", "createdAt")));
        	log.info("transactionLog index operation entityName _id createdAt   created");
        } else {
        	log.info("transactionLog index operation entityName _id createdAt   already exist");
        }
    }
    
    @ChangeSet(order = "002", id = "createIndexForTxnWithId", author = "sibin")
    public void createIndexForTxnWithId(MongoTemplate db) {
    	MongoCollection<Document> collection = db.getCollection("transactionLog");
        if(!MongoUtils.isIndexExist(db, "transactionLog", "entityName_1__id_-1_createdAt_-1")) {
        	collection.createIndex(Indexes.compoundIndex(Indexes.ascending("entityName"), Indexes.descending("_id", "createdAt")));
        	log.info("transactionLog index entityName _id createdAt   created");
        } else {
        	log.info("transactionLog index entityName _id createdAt   already exist");
        }
    }
    
    @ChangeSet(order = "003", id = "dropOldIndexForTxn", author = "sibin")
    public void dropOldIndexForTxn(MongoTemplate db) {
    	MongoCollection<Document> collection = db.getCollection("transactionLog");
        if(MongoUtils.isIndexExist(db, "transactionLog", "entityName_1_operation_1")) {
        	collection.dropIndex("entityName_1_operation_1");
        	log.info("transactionLog index entityName operation   dropped");
        }
        
        if(MongoUtils.isIndexExist(db, "transactionLog", "entityName_1")) {
        	collection.dropIndex("entityName_1");
        	log.info("transactionLog index entityName dropped");
        }
        
        if(MongoUtils.isIndexExist(db, "transactionLog", "createdAt_-1")) {
        	collection.dropIndex("createdAt_-1");
        	log.info("transactionLog createdAt_-1 dropped");
        }
        
    }

    @ChangeSet(order = "005", id = "createIndexForTxnWithCreatedAtAndId", author = "abhinav")
    public void createIndexForTxnWithCreatedAtAndId(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("transactionLog");
        // create new createdAt_-1__id_-1 index
        if(!MongoUtils.isIndexExist(db, "transactionLog", "createdAt_-1__id_-1")) {
            collection.createIndex(Indexes.descending("createdAt", "_id"));
            log.info("transactionLog index createdAt_-1__id_-1 created");
        } else {
            log.info("transactionLog index createdAt_-1__id_-1 already exist");
        }

        // drop existing _id_-1_createdAt_-1 index
        if(MongoUtils.isIndexExist(db, "transactionLog", "_id_-1_createdAt_-1")) {
            collection.dropIndex("_id_-1_createdAt_-1");
            log.info("transactionLog index _id_-1_createdAt_-1 dropped");
        }
    }
}
