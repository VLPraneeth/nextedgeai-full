package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_11092_DeleteSchemaMappings {

    @ChangeSet(order = "001", id = "deleteSchemaMappings", author = "durga", runAlways = true)
    public void deleteSchemaMappings(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        var schemaMappingsProp = System.getProperty("schemaMappings");
        String[] schemaMappingList = schemaMappingsProp.split(":");
        MongoCollection<Document> schemaMappingCollection = template.getCollection("schemaMapping");
        for(String schemaMapping: schemaMappingList) {
            log.info("Removing schemaMapping with id {}", schemaMapping);
            if (!dryRunMode) {
                schemaMappingCollection.findOneAndDelete(new Document("_id", new ObjectId(schemaMapping)));
                log.info("Removed schemaMapping with id {}", schemaMapping);
            }
        }
    }
}
