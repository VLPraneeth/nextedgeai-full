package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class DeprovisionDatastore {

    @ChangeSet(order = "001", id = "deprovisionDatastore", author = "blesson", runAlways = true)
    public void deprovisionDatastore(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        var datastoreService = MigrationContext.getDatastoreService();
        var featureService = MigrationContext.getFeatureService();
        var syncariId = MigrationContext.getSyncariId();
        if(featureService.isEnabled(Features.Datastore)) {
            log.info("Datastore enabled");
            if(!dryRun) {
                log.info("Deprovision datastore");
                datastoreService.deprovision(syncariId);
                MongoCollection<Document> features = template.getCollection("feature");
                log.info("Disable feature");
                features.findOneAndUpdate(new Document("name", "Datastore"), new Document("$set", new Document("status", "inactive")));
            }
        }
    }

}