package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class UpdateDatastorePoolsize {

    @ChangeSet(order = "001", id = "updatedDatastorePoolsize", author = "venkat", runAlways = true)
    public void updatedDatastoreMetaconfig(MongoTemplate template) {
        var metadataId = System.getProperty("metadataId");
        var value = System.getProperty("value");

        var datastoreConnector = template.getCollection("connector");
        log.info("For metadataId {} change key {}", metadataId);

        datastoreConnector.findOneAndUpdate(new Document("metadataId", metadataId), new Document("$set", new Document("metaConfig.poolSize", Integer.valueOf(value))));
    }
}
