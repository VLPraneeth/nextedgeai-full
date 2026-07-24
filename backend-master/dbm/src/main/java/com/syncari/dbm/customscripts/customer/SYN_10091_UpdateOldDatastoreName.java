package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_10091_UpdateOldDatastoreName {

    @ChangeSet(order = "001", id = "SYN_10091_UpdateOldDatastoreName", author = "blesson", runAlways = true)
    public void updateOldDatastoreName(MongoTemplate mongoTemplate) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var entityDefId = System.getProperty("entityDefId");
        var oldDatastoreName = System.getProperty("oldDatastoreTableName");
        var entityDefinitions = mongoTemplate.getCollection("entityDefinition");
        var entityDef = entityDefinitions.find(new Document("_id", new ObjectId(entityDefId))).first();
        var storeConfig = (Document) entityDef.get("storeConfig");
        var currentOldDatastoreName = (String)storeConfig.get("oldName");
        log.info("Updating old datastore name from {} to {}", currentOldDatastoreName, oldDatastoreName);
        if(!dryRun) {
            entityDefinitions.updateOne(entityDef, set("storeConfig.oldName", oldDatastoreName));
            log.info("Updated entitydefinition with new name {}", oldDatastoreName);
        }
    }
}
