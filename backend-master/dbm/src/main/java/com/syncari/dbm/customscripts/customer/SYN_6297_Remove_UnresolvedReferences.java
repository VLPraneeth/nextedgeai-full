package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.UnresolvedReference;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
public class SYN_6297_Remove_UnresolvedReferences {

    @ChangeSet(order = "001", id = "removeUnresolvedReferences", author = "venkat")
    public void removeUnresolvedReferences(MongoTemplate template) {

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        String externalEntity = "Group";
        String syncariAttributeName = "OwnerId";
        String syncariEntityId = "5f9202872d4fac0001f0e5bb";

        MongoCollection<Document> unresolvedReferences = template.getCollection("unresolvedReference");

        var query = Query.query(
                Criteria.where("externalRefEntityName").is(externalEntity))
                .addCriteria(Criteria.where("syncariAttributeName").is(syncariAttributeName))
                .addCriteria(Criteria.where("syncariEntityDefId").is(syncariEntityId));

        log.info("Running in dry run mode {}", dryRunMode);
        if (!dryRunMode) {
            template.bulkOps(BulkOperations.BulkMode.UNORDERED, UnresolvedReference.class).remove(query).execute();

        } else {
            var count = template.count(query, UnresolvedReference.class);
            log.info("Deleting {} records from Unresolved references", count);
        }
    }
}
