package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.MigrationContext;
import com.syncari.core.MigrationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@ChangeLog(order = "001")
public class CreateTTLForTransactionLog {

    @ChangeSet(order = "001", id = "createTTLIndexForTransactionLog", author = "varsha", runAlways = true)
    public void createTTLIndexForTransactionLog(MongoTemplate db) {
        String indexName = "transactionLog_createdAt_TTL_7Days";
        try {
            if (MigrationUtil.indexExists(db, "transactionLog", indexName)) {
                MigrationUtil.dropIndex(db, "transactionLog", indexName);
                MigrationUtil.createIndex(db, Map.of("transactionLog",
                        List.of(new Index("transactionLog_createdAt_TTL_8Days", false, false, Long.valueOf(60 * 60 * 24 * 8 /* 8 days TTL */), "createdAt"))));
            }
        } catch (Exception e) {
            log.error("Error while dropping index: {} for {}", e.getMessage(), MigrationContext.getSyncariId());
        }
    }

}

