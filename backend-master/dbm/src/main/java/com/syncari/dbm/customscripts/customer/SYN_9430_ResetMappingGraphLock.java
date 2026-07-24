package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_9430_ResetMappingGraphLock {

    @ChangeSet(order = "001", id = "resetMappingGraphLock", author = "abhinav", runAlways = true)
    public void resetMappingGraphLock(MongoTemplate template) {

        MongoCollection<Document> mappingGraph = template.getCollection("mappingGraph");

        Bson updatedVal = Updates.set("locked",false);
        UpdateResult mappingGraphUpadteResult = mappingGraph.updateMany(
                Filters.and(
                        Filters.eq("locked", true),
                        Filters.eq("draftStatus", "NEW")),
                updatedVal);

        log.info("Updated {} mappingGraphs", mappingGraphUpadteResult.getModifiedCount());
    }
}
