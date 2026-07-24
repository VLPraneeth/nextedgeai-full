package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.misc.Sharable;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class ClearRequeueCollection {

    @ChangeSet(order = "001", id = "clearRequeueCollection", author = "blesson", runAlways = true)
    public void clearRequeueCollection(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MongoCollection<Document> requeueRequest = template.getCollection("requeueRequest");
        var entityDefinitionId = System.getProperty("entityDefinitionId");
        var graphId = System.getProperty("graphId");
        long count = requeueRequest.countDocuments(and(eq("graphId", graphId), eq("entityDefinitionId", entityDefinitionId)));
        log.info("Deleting {} requeued requests", count);
        if(!dryRunMode) {
            requeueRequest.deleteMany(and(eq("graphId", graphId), eq("entityDefinitionId", entityDefinitionId)));
            log.info("Deleted {} requeued requests", count);
        }
    }
}
