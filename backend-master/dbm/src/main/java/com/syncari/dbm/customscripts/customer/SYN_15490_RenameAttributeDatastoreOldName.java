package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_15490_RenameAttributeDatastoreOldName {

    @ChangeSet(order = "001", id = "SYN_15490_RenameAttributeDatastoreOldName", author = "venkat", runAlways = true)
    public void renameAttributeStoreName(MongoTemplate mongoTemplate) {
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var attributeId = System.getProperty("attributeId");
        var oldName = System.getProperty("oldName");
        log.info("Dry Run {} oldName {} Attribute {}", dryRun, oldName, attributeId);
        if (!StringUtils.isBlank(oldName) && !dryRun) {
            var attributeDefinitions = mongoTemplate.getCollection("attributeDefinition");
            attributeDefinitions.updateOne(new Document("_id", new ObjectId(attributeId)), set("storeConfig.oldName", oldName));
        }
    }
}