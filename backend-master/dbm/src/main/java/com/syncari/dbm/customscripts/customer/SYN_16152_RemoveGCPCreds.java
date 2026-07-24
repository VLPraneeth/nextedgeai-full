package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.HashMap;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class SYN_16152_RemoveGCPCreds {

    @ChangeSet(order = "001", id = "removeGCPCredential", author = "sibin", runAlways = true)
    public void removeGCPCredential(MongoTemplate db) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoCollection<Document> connectors = db.getCollection("connector");
        Document fileDataConnector = connectors.find(new Document("name", "Imported Files")).first();
        Map<String, Object> meta = new HashMap<>((Map<String, Object>) fileDataConnector.get("metaConfig"));
        log.info("Found Imported Files connector with meta {}", meta.keySet());
        var val = meta.remove("gcpCredentialsKey");
        if (val != null) {
            log.info("Successfully removed gcpCredentialsKey");
        } else {
            log.info("gcpCredentialsKey not found");
        }
        Bson updatedVal = Updates.set("metaConfig", meta);
        if (!dryRun) {
            connectors.findOneAndUpdate(eq("name", "Imported Files"), updatedVal);
            log.info("Imported Files connector updated");
        }
    }
}
