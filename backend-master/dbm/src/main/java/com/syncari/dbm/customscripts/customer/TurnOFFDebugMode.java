package com.syncari.dbm.customscripts.customer;

import java.util.Date;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import lombok.extern.slf4j.Slf4j;

import static com.mongodb.client.model.Updates.*;

@Slf4j
public class TurnOFFDebugMode {

    @ChangeSet(order = "001", id = "turnOFFDebugMode", author = "sudee", runAlways = true)
    public void turnOFFDebugMode(MongoTemplate template) {
        MongoCollection<Document> instanceConfiguration = template.getCollection("instanceConfiguration");
        String debugModeConfigId = instanceConfiguration.find(new Document("key", "debugMode")).first().getObjectId("_id").toHexString();
        String debugModeExpiryConfigId = instanceConfiguration.find(new Document("key", "debugModeExpirySecs")).first().getObjectId("_id").toHexString();
        log.info("Found debugMode config with id: {}", debugModeConfigId);
        log.info("Found debugModeExpirySecs config with id: {}", debugModeExpiryConfigId);
        if (StringUtils.isNotEmpty(debugModeConfigId) && StringUtils.isNotEmpty(debugModeExpiryConfigId)) {
            instanceConfiguration.findOneAndUpdate(new Document("_id", new ObjectId(debugModeConfigId)), 
                combine(set("value", false), set("updatedAt", new Date())));
            instanceConfiguration.findOneAndUpdate(new Document("_id", new ObjectId(debugModeExpiryConfigId)), 
                combine(set("value", 60), set("updatedAt", new Date())));
            log.info("Successfully turned OFF debug mode, reset expirysecs to 60 seconds");
        }
    }
}
