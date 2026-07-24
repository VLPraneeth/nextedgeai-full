package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.model.UnresolvedReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
public class SYN_16607_RemoveUnresolvedReferencesForEntity {

    @ChangeSet(order = "001", id = "removeUnresolvedReferences", author = "venkat", runAlways = true)
    public void removeUnresolvedReferencesForEntity(MongoTemplate template) {
        String externalRefEntityName = System.getProperty("externalRefEntityName");
        String syncariAttributeName = System.getProperty("syncariAttributeName");
        String sourceEntityDefId = System.getProperty("syncariEntityDefId");

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        var query = Query.query(
                Criteria.where("externalRefEntityName").is(externalRefEntityName))
                .addCriteria(Criteria.where("syncariAttributeName").is(syncariAttributeName))
                .addCriteria(Criteria.where("syncariEntityDefId").is(sourceEntityDefId))
                .addCriteria(Criteria.where("externalRefRecordId").regex("EntityData", "i"));

        log.info("Running in dry run mode {}", dryRunMode);
        if (!dryRunMode) {
            template.bulkOps(BulkOperations.BulkMode.UNORDERED, UnresolvedReference.class).remove(query).execute();
        } else {
            var count = template.count(query, UnresolvedReference.class);
            log.info("Deleting {} records from Unresolved references", count);
        }
    }
}
