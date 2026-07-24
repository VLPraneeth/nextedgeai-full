package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class AddConnectorMetadataConfig {

    @ChangeSet(order = "001", id = "addConnectorConfig", author = "venkat", runAlways = true)
    public void addConnectorConfig(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String connectorId = System.getProperty("connectorId");
        String key = System.getProperty("key");
        String value = System.getProperty("value");


        var connector = template.getCollection("connector");

        if (!dryRunMode) {
            connector.updateOne(new Document("_id", new ObjectId(connectorId)), new Document("$set", new Document("metaConfig." + key, value)));
        } else {
            log.info("Updating conneotor id {} Metaconfig key {} value {}", connectorId, key, value);
        }
    }
}
