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
public class TurnONDebugMode {

    @ChangeSet(order = "001", id = "turnONDebugMode", author = "sudee", runAlways = true)
    public void turnONDebugMode(MongoTemplate template) {
        int expiryInSecs = Integer.parseInt(System.getProperty("expiryInSecs","900"));
        MongoCollection<Document> instanceConfiguration = template.getCollection("instanceConfiguration");
        String debugModeConfigId = instanceConfiguration.find(new Document("key", "debugMode")).first().getObjectId("_id").toHexString();
        String debugModeExpiryConfigId = instanceConfiguration.find(new Document("key", "debugModeExpirySecs")).first().getObjectId("_id").toHexString();
        log.info("Found debugMode config with id: {}", debugModeConfigId);
        log.info("Found debugModeExpirySecs config with id: {}", debugModeExpiryConfigId);
        if (StringUtils.isNotEmpty(debugModeConfigId) && StringUtils.isNotEmpty(debugModeExpiryConfigId)) {
            instanceConfiguration.findOneAndUpdate(new Document("_id", new ObjectId(debugModeConfigId)), 
                combine(set("value", true), set("updatedAt", new Date())));
            instanceConfiguration.findOneAndUpdate(new Document("_id", new ObjectId(debugModeExpiryConfigId)), 
                combine(set("value", expiryInSecs), set("updatedAt", new Date())));
            log.info("Successfully turned ON debug mode, it will expire in {} seconds", expiryInSecs);
        }
    }
}
