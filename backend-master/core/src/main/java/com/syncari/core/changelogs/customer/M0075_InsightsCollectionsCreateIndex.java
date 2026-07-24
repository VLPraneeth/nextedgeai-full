package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.MigrationUtil;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.InsightsDashboard;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetExport;
import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@ChangeLog(order = "0075")
public class M0075_InsightsCollectionsCreateIndex {
    @ChangeSet(order = "001", id = "createCollectionAndCreateIndex", author = "rohit")
    public void createCollectionAndCreateIndex(MongoTemplate db) {
        if (!db.collectionExists(Dataset.class)){
            db.createCollection(Dataset.class);
        }
        MongoUtils.createIndexes(db,"dataset", List.of(new Index(true,"name", "draftStatus")));
    }

    @ChangeSet(order = "002", id = "createDatacardCollectionAndCreateIndex", author = "abhinav")
    public void createDatacardCollectionAndCreateIndex(MongoTemplate db) {
        if (!db.collectionExists(Datacard.class)){
            db.createCollection(Datacard.class);
        }
        MongoUtils.createIndexes(db,"datacard", List.of(new Index(true,"name", "draftStatus")));
    }

    @ChangeSet(order = "003", id = "createDashboardCollectionAndCreateIndex", author = "abhinav")
    public void createDashboardCollectionAndCreateIndex(MongoTemplate db) {
        if (!db.collectionExists(InsightsDashboard.class)){
            db.createCollection(InsightsDashboard.class);
        }
        MongoUtils.createIndexes(db,"insightsDashboard", List.of(new Index(true,"name", "draftStatus")));
    }

    @ChangeSet(order = "004", id = "createDatasetExportCollectionAndCreateIndex", author = "rohit")
    public void createDatasetExportCollectionAndCreateIndex(MongoTemplate db) {
        if (!db.collectionExists(DatasetExport.class)){
            db.createCollection(DatasetExport.class);
        }
        MongoUtils.createIndexes(db,"datasetExport", List.of(new Index(false,"datasetId", "status")));
    }
}
