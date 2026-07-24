package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class SYN_11562_RequeueRequestIndex {
    @ChangeSet(order = "001", id = "createRequeRequestIndex", author = "neelesh", runAlways = true)
    public void createRequeRequestIndex(MongoTemplate db) {
        MongoUtils.createIndexes(db, "requeueRequest", List.of(new Index(false, "entityDefinitionId", "graphId", "retryTimeLimit")));
    }
}
