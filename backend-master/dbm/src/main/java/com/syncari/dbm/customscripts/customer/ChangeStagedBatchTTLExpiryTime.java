package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.Index;
import com.syncari.core.MigrationUtil;
import com.syncari.core.SyncariContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class ChangeStagedBatchTTLExpiryTime {

    @ChangeSet(order = "001", id = "changeStagedBatchTTLExpiryTime", author = "varsha", runAlways = true)
    public void changeStagedBatchTTLExpiryTime(MongoTemplate template) {
        MongoCollection<Document> coll = template.getCollection("stagedBatchRecord");
        try {
            String indexName = "stagedBatchRecord_updatedAt_TTL_90Days";
            if(hasIndex(coll, indexName)) {
                coll.dropIndex(indexName);
                log.info("Dropped index {} for customer {}", indexName, SyncariContext.getSyncariId());
            }
        } catch (Exception e) {
            log.error("Error dropping index {} for customer {}", e.getMessage(), SyncariContext.getSyncariId());
        }

        try {
            String newIndexName = "stagedBatchRecord_updatedAt_TTL_7Days";
            if(!hasIndex(coll, newIndexName)) {
                MigrationUtil.createIndex(template, Map.of("stagedBatchRecord",
                        List.of(new Index(newIndexName, false, false, Long.valueOf(60 * 60 * 24 * 7 /* 7 days TTL */), "updatedAt"))));
                log.info("Created index {} for customer {}", newIndexName, SyncariContext.getSyncariId());
            }
        } catch (Exception e) {
            log.error("Error creating index {} for customer {}", e.getMessage(), SyncariContext.getSyncariId());
        }
    }

    private boolean hasIndex(MongoCollection<Document> collection, String indexName) {
        List<Document> existingIndexes = new ArrayList<>();
        collection.listIndexes().into(existingIndexes);
        return existingIndexes.stream()
                .anyMatch(e -> e.get("name").toString().equalsIgnoreCase(indexName));
    }
}
