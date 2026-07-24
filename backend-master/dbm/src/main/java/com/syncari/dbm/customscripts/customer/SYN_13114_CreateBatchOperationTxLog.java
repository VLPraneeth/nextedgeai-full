package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
@ChangeLog(order = "0001")
public class SYN_13114_CreateBatchOperationTxLog {
    @ChangeSet(order = "001", id = "createIndexForTxnOperationBatchOperation", author = "venkat")
    public void createIndexForTxnOperationBatchOperation(MongoTemplate db) {
    	MongoCollection<Document> collection = db.getCollection("transactionLog");
        if(!MongoUtils.isIndexExist(db, "transactionLog", "batchId_1_operation_1")) {
        	collection.createIndex(Indexes.compoundIndex(Indexes.ascending("batchId", "operation")));
        	log.info("transactionLog index batchId and operation created");
        } else {
        	log.info("transactionLog index batchId and operation already exist");
        }
    }
}
