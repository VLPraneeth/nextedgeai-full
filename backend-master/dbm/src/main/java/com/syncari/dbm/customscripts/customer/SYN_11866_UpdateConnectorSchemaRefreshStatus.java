package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.model.misc.AsyncStatus;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_11866_UpdateConnectorSchemaRefreshStatus {

    @ChangeSet(order = "001", id = "updateConnectorSchemaRefreshStatus", author = "blesson", runAlways = true)
    public void updateConnectorSchemaRefreshStatus(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String connectorId = System.getProperty("connectorId");
        String status = System.getProperty("status");
        AsyncStatus asyncStatus = AsyncStatus.valueOf(status);
        if(asyncStatus == null) {
            log.warn("Invalid status");
            return;
        }

        var connector = template.getCollection("connector");

        if (!dryRunMode) {
            connector.updateOne(new Document("_id", new ObjectId(connectorId)), new Document("$set", new Document("schemaRefreshStatus", status)));
        } else {
            log.info("Setting schemaRefreshStatus for connector {} to {}", connectorId, asyncStatus);
        }
    }

}
