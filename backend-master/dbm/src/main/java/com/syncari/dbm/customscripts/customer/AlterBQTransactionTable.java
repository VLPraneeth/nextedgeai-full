package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "001")
public class AlterBQTransactionTable {
    @ChangeSet(order = "001", id = "alterTxnLogInBQ", author = "varsha", runAlways = true)
    public void createTxnLogInBQ(MongoTemplate db) {
        MigrationContext.getBigQueryTransactionLogStore().setRequirePartitionFilter(true);
    }
}

