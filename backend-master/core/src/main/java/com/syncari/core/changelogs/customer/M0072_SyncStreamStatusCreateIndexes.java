package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
@ChangeLog(order = "0072")
public class M0072_SyncStreamStatusCreateIndexes {

    @ChangeSet(order = "001", id = "createIndexOnSyncStream", author = "rohit")
    public void createIndexOnSyncStream(MongoTemplate db) {
        MongoUtils.createIndexes(db,"syncStream", List.of(new Index(false,"graphId")));
    }

    @ChangeSet(order = "002", id = "createIndexOnPipelineTest", author = "rohit")
    public void createIndexOnPipelineTest(MongoTemplate db) {
        MongoUtils.createIndexes(db,"pipelineTest", List.of(new Index(false,"graphId","status")));
    }

    @ChangeSet(order = "003", id = "createIndexOnResyncDetail", author = "rohit")
    public void createIndexOnResyncDetail(MongoTemplate db) {
        MongoUtils.createIndexes(db,"resyncDetail", List.of(new Index(false,"syncariEntityId")));
    }
}
