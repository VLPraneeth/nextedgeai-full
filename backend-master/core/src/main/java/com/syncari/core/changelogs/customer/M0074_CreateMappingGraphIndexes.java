package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Index;
import com.syncari.core.utils.MongoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
@ChangeLog(order = "0074")
public class M0074_CreateMappingGraphIndexes {

    @ChangeSet(order = "001", id = "createdeleteMappingGraphIndex", author = "rohit")
    public void createdeleteMappingGraphIndex(MongoTemplate db) {
    	if(MongoUtils.isIndexExist(db, "mappingGraph", "targetId_1_name_1_draftStatus_1")) {
    		MongoUtils.dropIndexes(db,"mappingGraph", List.of(new Index("targetId_1_name_1_draftStatus_1",true,"targetId","name","draftStatus")));
    	}
    }
    
    @ChangeSet(order = "002", id = "createdeleteMappingGraphIndex2", author = "sibin")
    public void createdeleteMappingGraphIndex2(MongoTemplate db) {
    	if(MongoUtils.isIndexExist(db, "mappingGraph", "targetId_1_name_1_draftStatus_1")) {
    		MongoUtils.dropIndexes(db,"mappingGraph", List.of(new Index("targetId_1_name_1_draftStatus_1",true,"targetId","name","draftStatus")));
    	}
    	if(MongoUtils.isIndexExist(db, "mappingGraph", "targetId_1_draftStatus_1_name_1")) {
    		MongoUtils.dropIndexes(db,"mappingGraph", List.of(new Index("targetId_1_draftStatus_1_name_1",true,"targetId","draftStatus","name")));
    	}
        MongoUtils.createIndexes(db,"mappingGraph", List.of(new Index(true,"targetId","draftStatus","name","versionInfo._id")));
    }
}
