package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.*;

@Slf4j
public class SYN_8962_RenameDatastoreTable {

    @ChangeSet(order = "001", id = "SYN_8962_RenameDatastore", author = "blesson", runAlways = true)
    public void renameDatastore(MongoTemplate mongoTemplate) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var syncariId = MigrationContext.getSyncariId();
        var datastoreService = MigrationContext.getDatastoreService();
        var entityDefId = System.getProperty("entityDefId");
        var newDatastoreTableName = System.getProperty("newDatastoreTableName");
        var entityDefinitions = mongoTemplate.getCollection("entityDefinition");
        var entityDef = entityDefinitions.find(new Document("_id", new ObjectId(entityDefId))).first();
        var storeConfig = (Document) entityDef.get("storeConfig");
        var oldName = (String)storeConfig.get("newName");
        if(oldName != null) {
            oldName = oldName.toLowerCase();
            log.info("Updating entitydefinition from {} to {}", oldName, newDatastoreTableName);
            if (!dryRun) {
                datastoreService.renameTable(oldName, newDatastoreTableName, syncariId);
                entityDefinitions.updateOne(entityDef, set("storeConfig.newName", newDatastoreTableName));
                log.info("Updated entitydefinition with new name {}", newDatastoreTableName);
            }
        }
    }
}