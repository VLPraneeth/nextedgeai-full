package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.model.UpdateOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class FixDatastoreCluster {

    @ChangeSet(order = "001", id = "fixDatastoreCluster", author = "venkat", runAlways = true)
    public void fixDatastoreCluster(MongoTemplate template) {
        var metadataId = System.getProperty("metadataId");
        var clusterName = System.getProperty("clusterName");

        var datastoreConnector = template.getCollection("connector");
        log.info("For metadataId {} change cluster name {}", metadataId, clusterName);

        datastoreConnector.findOneAndUpdate(new Document("metadataId", metadataId), new Document("$set", new Document("metaConfig.clusterName", clusterName)));
    }
}
