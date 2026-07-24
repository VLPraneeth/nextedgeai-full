package com.syncari.core.changelogs.customer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.syncari.core.Index;

@ChangeLog(order = "M0037")
public class M0037_AddIndexForIsDeleted {

    @ChangeSet(order = "001", id = "addIndexForIsDeletedForEntity", author = "varsha")
    public void addIndexForIsDeletedForEntity(MongoTemplate template) {
        Iterator<String> collections = template.getDb().listCollectionNames().iterator();

        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        while (collections.hasNext()) {
            String c = collections.next();
            if (c.startsWith("syncari_")) {
                indexMap.put(c, List.of(new Index(false, "isDeleted"), new Index(false, "syncariTimestamp")));
            }
        }
        create(template, indexMap);
    }

    private void create(MongoTemplate db, Map<String, List<Index>> indexMap) {
        indexMap.forEach((k, v) -> {
            v.stream().forEach(index -> {
                MongoCollection<Document> collection = db.getCollection(k);
                IndexOptions keyOpts = new IndexOptions().unique(index.isUnique());
                BasicDBObject dbObj = new BasicDBObject();
                index.getFields().stream().forEach(f -> dbObj.append(f, index.getAscending()));
                collection.createIndex(dbObj, keyOpts);
            });
        });
    }

}
