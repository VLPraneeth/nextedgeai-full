package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.MigrationUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
public class CreateTTLIndex {

    @ChangeSet(order = "001", id = "createTTLIndex", author = "abhinav", runAlways = true)
    public void createTTLIndex(MongoTemplate db) {
        var collectionName = System.getProperty("collection");
        var fieldName = System.getProperty("fieldName");
        var ttlDays = Integer.parseInt(System.getProperty("ttlDays"));
        var existingIndex = System.getProperty("existingIndex");
        long duration = Long.valueOf(60 * 60 * 24 * ttlDays);

        String indexName = String.format("%s_%s_TTL_%sDays", collectionName, fieldName, ttlDays);
        log.info("Creating ttl index {}", indexName);

        if (!StringUtils.isBlank(existingIndex)) {
            MigrationUtil.dropIndex(db, collectionName, existingIndex);
        }

        MigrationUtil.createIndex(db, Map.of(collectionName,
                List.of(new Index(indexName, false, false, duration, fieldName))));


    }
}
