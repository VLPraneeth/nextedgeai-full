package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.MigrationUtil;
import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@ChangeLog(order = "0071")
public class M0071_SyncDetailMetricCreateIndexes {

    @ChangeSet(order = "001", id = "createIndexOnSyncDetailMetric", author = "rohit")
    public void createIndexOnSyncDetailMetric(MongoTemplate db) {
        if (!db.collectionExists(SyncDetailMetric.class)){
            db.createCollection(SyncDetailMetric.class);
        }
        MongoUtils.createIndexes(db,"syncDetailMetric", List.of(new Index(true,"syncariEntityId","syncCycleId")));
    }

    @ChangeSet(order = "002", id = "createTTLIndexForSyncDetailMetric", author = "rohit")
    public void createTTLIndexForSyncDetailMetric(MongoTemplate db) {
        String indexName = "syncDetailMetric_updatedAt_TTL_30Days";
        MigrationUtil.createIndex(db, Map.of("syncDetailMetric",
                List.of(new Index(indexName, false, false, Long.valueOf(60 * 60 * 24 * 30  /* for 30 days  TTL */), "updatedAt"))));
    }

    // 3 and 4 changeset orders were deleted
    @ChangeSet(order = "005", id = "createIndexUpdatedAtAndRecordsProcessedOnSyncDetailMetric", author = "rohit")
    public void createIndexUpdatedAtAndRecordsProcessedOnSyncDetailMetric(MongoTemplate db) {
        MongoUtils.dropIndexes(db,"syncDetailMetric", List.of(new Index("syncariEntityId_1_recordsProcessedInLastStage_1_updatedAt_1", false,"syncariEntityId","recordsProcessedInLastStage", "updatedAt") ));
        final Index e1 = new Index(false, Map.of("updatedAt", -1), "syncariEntityId", "updatedAt", "recordsProcessedInLastStage");
        e1.setName("syncariEntityId_1_recordsProcessedInLastStage_1_updatedAt_1");
        MongoUtils.createIndexes(db, "syncDetailMetric", List.of(e1));
    }

    @ChangeSet(order = "006", id = "createIndexProcessingStage", author = "rohit")
    public void createIndexProcessingStage(MongoTemplate db) {
        final Index e1 = new Index(false, Map.of("summary.processingStage", 1, "syncariEntityId", 1, "updatedAt", -1,
                "recordsProcessedInLastStage", 1),
                "summary.processingStage", "syncariEntityId", "updatedAt", "recordsProcessedInLastStage");
        e1.setName("idx_processingStage");
        MongoUtils.createIndexes(db, "syncDetailMetric", List.of(e1));
    }
}
