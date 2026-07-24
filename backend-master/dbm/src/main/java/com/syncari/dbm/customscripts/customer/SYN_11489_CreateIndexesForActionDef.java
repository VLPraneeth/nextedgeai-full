package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ChangeLog(order = "0001")
public class SYN_11489_CreateIndexesForActionDef {
    @ChangeSet(order = "001", id = "createIndexesForActionDef", author = "varsha", runAlways = true)
    public void createIndexesForActionDef(MongoTemplate db) {
    	MongoCollection<Document> collection = db.getCollection("actionDefinition");
        IndexOptions keyOpts = new IndexOptions().unique(true);
        Map map = new HashMap<>();
        List.of("name", "apiName", "draftStatus").stream().forEach(f -> map.put(f, 1));
        try {
            if(!MongoUtils.isIndexExist(db, "actionDefinition", "name_1_apiName_1_draftStatus_1")) {
                collection.createIndex(new BasicDBObject(map), keyOpts);
                log.info("Unique index created for actionDefinition");
            }
        } catch (Exception e) {
            log.error("Failed to create for actionDefinition {}", e.getMessage());
        }
    }
}
