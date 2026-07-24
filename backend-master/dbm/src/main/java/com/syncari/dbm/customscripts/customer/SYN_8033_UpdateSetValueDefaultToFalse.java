package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_8033_UpdateSetValueDefaultToFalse {

    @ChangeSet(order = "001", id = "updateSetValueDefaultToFalse", author = "blesson", runAlways = true)
    public void updateSetValueDefaultToFalse(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        log.info("Running in dry run mode - {}", dryRunMode);
        MongoCollection<Document> mappingNode = template.getCollection("mappingNode");
        Bson where = new Document()
                .append("apiName", "setValue")
                .append("configuration.functionCall.config.dataType", "boolean")
                .append("configuration.functionCall.config.newValue", "");
        Bson update = new Document().append("configuration.functionCall.config.newValue", false);
        Bson set = new Document().append("$set", update);
        var updatedDocs = mappingNode.find(where);
        updatedDocs.forEach((Block<? super Document>) doc -> {
            log.info("Updating node id - {}", doc.get("_id"));
            if(!dryRunMode) {
                mappingNode.updateOne(doc, set);
            }
        });
    }
}
