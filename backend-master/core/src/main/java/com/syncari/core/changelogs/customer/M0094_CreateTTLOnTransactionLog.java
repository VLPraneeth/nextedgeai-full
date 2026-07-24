package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.MigrationUtil;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@ChangeLog(order = "0094")
public class M0094_CreateTTLOnTransactionLog {
    @ChangeSet(order = "001", id = "createTTLIndexOnTxn", author = "varsha", runAlways = true)
    public void createTTLIndex(MongoTemplate db) {
        var collectionName = "transactionLog";
        var fieldName = "createdAt";
        long duration = Long.valueOf(60 * 60 * 24 * 7);
        String oldIndexName = String.format("%s_%s_TTL_%sDays", collectionName, fieldName, 8);
        if (MigrationUtil.indexExists(db, collectionName, oldIndexName)) {
            MigrationUtil.dropIndex(db, collectionName, oldIndexName);
        }
        String indexName = String.format("%s_%s_TTL_%sDays", collectionName, fieldName, 7);
        if (MigrationUtil.indexExists(db, collectionName, indexName)) {
            var existingIndex  = MigrationUtil.getIndex(db, collectionName, indexName);
            Object expireObj = existingIndex.get().get("expireAfterSeconds");
            long expireAfterSeconds = 0;
            if (expireObj instanceof Long) {
                expireAfterSeconds = ((Long) expireObj).longValue();
            } else {
                expireAfterSeconds = ((Integer) expireObj).longValue();
            }

            if (expireAfterSeconds != duration) {
                MigrationUtil.dropIndex(db, collectionName, indexName);
            } else {
                return;
            }
        }

        MigrationUtil.createIndex(db, Map.of(collectionName,
                List.of(new Index(indexName, false, false, duration, fieldName))));

    }
}
