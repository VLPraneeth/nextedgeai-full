package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeLog(order = "0068")
public class M0068_CreateIndex {

    @ChangeSet(order = "001", id = "createUniqueIndexesMappingGraph", author = "rohit")
    public void createUniqueIndexesMappingGraph(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("mappingGraph");
        IndexOptions keyOpts = new IndexOptions().unique(true);
        BasicDBObject dbObj = new BasicDBObject();
        dbObj.append("targetId",1);
        dbObj.append("draftStatus",1);
        dbObj.append("name",1);
        dbObj.append("versionInfo._id",1);
        collection.createIndex(dbObj, keyOpts);
    }
}



