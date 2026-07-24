package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;


@Slf4j
public class DeleteEventData {

    @ChangeSet(order = "001", id = "deleteEventData", author = "venkat", runAlways = true)
    public void deleteMappingGraph(MongoTemplate template) {

        var graphId = System.getProperty("graphId");
        var connectorId = System.getProperty("connectorId");
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        log.info("Dropping records for graph id {} connector id {}", graphId, connectorId);
        if (!dryRunMode) {
            MongoCollection<Document> eventData = template.getCollection("eventData");
            var query = Filters.and(Filters.eq("connectorId", connectorId), Filters.eq("graphId", graphId));
            log.info("Query {}", query.toString());
            eventData.deleteMany(query);
        }
    }
}
