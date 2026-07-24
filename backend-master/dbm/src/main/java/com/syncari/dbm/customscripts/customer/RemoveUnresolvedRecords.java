package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.model.UnresolvedRecord;
import com.syncari.core.model.UnresolvedReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
public class RemoveUnresolvedRecords {

    @ChangeSet(order = "001", id = "removeUnresolvedRecords", author = "blesson", runAlways = true)
    public void removeUnresolvedRecords(MongoTemplate template) {
        String syncariEntityDefinitionId = System.getProperty("syncariEntityDefinitionId");

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        var query = Query.query(
                        Criteria.where("syncariEntityDefinitionId").is(syncariEntityDefinitionId));

        log.info("Running in dry run mode {}", dryRunMode);
        if (!dryRunMode) {
            template.bulkOps(BulkOperations.BulkMode.UNORDERED, UnresolvedRecord.class).remove(query).execute();
        } else {
            var count = template.count(query, UnresolvedRecord.class);
            log.info("Deleting {} records from Unresolved references", count);
        }
    }
}