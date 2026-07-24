package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SYN_8774_CreateActionDefinitionIndex {
    @ChangeSet(order = "001", id = "createActionDefinitionIndex", author = "venkat", runAlways = true)
    public void createActionDefinitionIndex(MongoTemplate template) {
        MongoCollection<Document> actionDefinition = template.getCollection("actionDefinition");
        IndexOptions keyOpts = new IndexOptions().unique(true);
        BasicDBObject dbObj = new BasicDBObject();
        dbObj.append("name",1);
        dbObj.append("apiName",1);
        dbObj.append("parentId",1);
        dbObj.append("type",1);
        dbObj.append("draftStatus",1);
        actionDefinition.createIndex(dbObj, keyOpts);
    }
}
