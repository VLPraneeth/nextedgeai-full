package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
@ChangeLog(order = "0073")
public class M0073_CreateGraphIndexes {

    @ChangeSet(order = "001", id = "createNodeIndex", author = "neelesh")
    public void createNodeIndex(MongoTemplate db) {
        MongoUtils.createIndexes(db,"mappingNode", List.of(new Index(false,"mappingGraphId")));
    }

    @ChangeSet(order = "002", id = "createEdgeIndex", author = "neelesh")
    public void createEdgeIndex(MongoTemplate db) {
        MongoUtils.createIndexes(db,"edge", List.of(new Index(false,"graphId")));
    }

    @ChangeSet(order = "001", id = "createMappingNodeConfigEdefIndex", author = "rohit")
    public void createMappingNodeConfigEdefIndex(MongoTemplate db) {
        MongoUtils.createIndexes(db,"mappingNode", List.of(new Index(false,"configuration.entityDefinition.$id")));
    }
}
