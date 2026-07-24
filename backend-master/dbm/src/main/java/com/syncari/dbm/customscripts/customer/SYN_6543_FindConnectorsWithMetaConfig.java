package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_6543_FindConnectorsWithMetaConfig {
    @ChangeSet(order = "001", id = "findConnectorsWithMetaConfig", author = "sudee")
    public void findConnectorsWithMetaConfig(MongoTemplate template) {

        // Find if connector with timeZoneId property exists
        MongoCollection<Document> connectorCol = template.getCollection("connector");
        var tzConnectors = connectorCol.find(new Document("metaConfig.timeZoneId", new Document("$exists", true))
                .append("status", "ACTIVE"));
        tzConnectors.forEach((Block<? super Document>) connector -> {
            log.info("DB: {}, Found connector {}/{} with timeZoneId {}", template.getDb().getName(),
                    connector.getObjectId("_id").toHexString(), connector.getString("name"),
                    ((Document) connector.get("metaConfig")).getString("timeZoneId"));
        });
    }
}
