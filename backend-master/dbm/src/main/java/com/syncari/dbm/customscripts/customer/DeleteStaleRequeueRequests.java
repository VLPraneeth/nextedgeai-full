package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;


/**
 * Script to delete stale RequeueRequest records for a specific entity.
 * This removes requeued records that are no longer needed after the retry action was removed from UI.

 * Parameters:
 * - entityDefinitionId (required): The entity definition ID to filter requeue requests
 * - graphId (optional): The graph ID to further filter requeue requests. If not provided, deletes for all graphs
 * - dryRun (optional, default=true): Set to false to actually delete records
 */
@Slf4j
public class DeleteStaleRequeueRequests {

    @ChangeSet(order = "001", id = "deleteStaleRequeueRequests", author = "sumanth", runAlways = true)
    public void deleteStaleRequeueRequests(MongoTemplate template) {

        // Get parameters
        String entityDefinitionId = System.getProperty("entityDefinitionId");
        String graphId = System.getProperty("graphId");
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun", "true"));

        // Validate required parameters
        if (entityDefinitionId == null || entityDefinitionId.trim().isEmpty()) {
            log.error("entityDefinitionId parameter is required. Usage: -DentityDefinitionId=<entity_id>");
            throw new IllegalArgumentException("entityDefinitionId parameter is required");
        }

        log.info("=================================================");
        log.info("Delete Stale RequeueRequest Records");
        log.info("=================================================");
        log.info("Entity Definition ID: {}", entityDefinitionId);
        log.info("Graph ID: {}", graphId != null ? graphId : "ALL");
        log.info("Dry Run Mode: {}", dryRunMode);
        log.info("=================================================");

        MongoCollection<Document> requeueCollection = template.getCollection("requeueRequest");

        // Build query
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("entityDefinitionId", entityDefinitionId));

        if (graphId != null && !graphId.trim().isEmpty()) {
            filters.add(Filters.eq("graphId", graphId));
        }

        Bson query = filters.size() > 1 ? Filters.and(filters) : filters.get(0);

        // Count records before deletion
        long recordCount = requeueCollection.countDocuments(query);
        log.info("Found {} RequeueRequest records matching criteria", recordCount);

        if (recordCount == 0) {
            log.info("No records found to delete");
            return;
        }

        // Log sample of records to be deleted (first 5)
        log.info("Sample records to be deleted:");
        for (Document doc : requeueCollection.find(query).limit(5)) {
            log.info("  - Record ID: {}, Entity: {}, Graph: {}, Retry Time: {}",
                doc.getString("recordId"),
                doc.getString("entityDefinitionId"),
                doc.getString("graphId"),
                doc.get("retryTimeLimit"));
        }

        // Delete records
        if (!dryRunMode) {
            log.info("Deleting {} records...", recordCount);
            var deleteResult = requeueCollection.deleteMany(query);
            log.info("Successfully deleted {} records", deleteResult.getDeletedCount());

            // Verify deletion
            long remainingCount = requeueCollection.countDocuments(query);
            if (remainingCount > 0) {
                log.warn("Warning: {} records still remain after deletion", remainingCount);
            } else {
                log.info("Verification: All matching records have been successfully deleted");
            }
        } else {
            log.info("DRY RUN MODE - No records were actually deleted");
            log.info("To actually delete these records, run with -DdryRun=false");
        }

        log.info("=================================================");
        log.info("Script execution completed");
        log.info("=================================================");
    }
}
