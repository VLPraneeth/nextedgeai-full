package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_12075_Datastorefixup {

    @ChangeSet(order = "001", id = "addDatastoreOldname", author = "venkat", runAlways = true)
    public void addDatastoreOldname(MongoTemplate template) {
        String id = System.getProperty("entityId");
        String oldName = System.getProperty("oldName");

        MongoCollection<Document> collection = template.getCollection("entityDefinition");
        Document query = new Document("_id", new ObjectId(id));
        collection.updateOne(query, new Document("$set", new Document("storeConfig.oldName", oldName)));
    }
}
