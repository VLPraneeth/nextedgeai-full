package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_11164_PopulateExpiresInField {

    @ChangeSet(order = "001", id = "populateExpiresInField", author = "durga", runAlways = true)
    public void populateExpiresInField(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var connectorProp = System.getProperty("connectors");
        String[] connectorIdList = connectorProp.split(":");

        MongoCollection<Document> connectorCollection = template.getCollection("connector");

        for(String connectorId: connectorIdList) {
            log.info("Updating connector with id {}", connectorId);

            if (!dryRunMode) {
                Bson query = new Document("_id", new ObjectId(connectorId));
                connectorCollection.findOneAndUpdate(query, new Document("$set", new Document("authConfig.expiresIn", "6600")));
                log.info("Updated connector with id {}", connectorId);
            }

        }
    }

}
