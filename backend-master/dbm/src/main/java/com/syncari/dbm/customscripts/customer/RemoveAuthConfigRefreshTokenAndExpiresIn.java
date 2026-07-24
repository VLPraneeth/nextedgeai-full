package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.service.ConnectorService;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class RemoveAuthConfigRefreshTokenAndExpiresIn {

    @ChangeSet(order = "001", id = "addConnectorConfig", author = "venkat", runAlways = true)
    public void removeAuthConfigRefreshTokenAndExpiresIn(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        String connectorId = System.getProperty("connectorId");
        ConnectorRepo connectorRepo = MigrationContext.getConnectorRepo();

        var connectorCollection = template.getCollection("connector");

        var connector = connectorRepo.findById(connectorId);

        log.info("Connector found - {}", connector);
        if (!dryRunMode) {
            connectorCollection.updateOne(new Document("_id", new ObjectId(connectorId)), new Document("$set", new Document("authConfig." + "refreshToken", null).append("authConfig." + "expiresIn", null)));
        }
    }
}