package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.syncari.core.Index;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;
import java.util.function.Consumer;

@ChangeLog(order = "0032")
public class M0032_CreateIdMappingIndexes {
    @ChangeSet(order = "001", id = "createIdMappingIndexes", author = "neelesh")
    public void createIdMappingIndexes(MongoTemplate db) {

        Set<String> duplicates= new HashSet<>();

        MongoCollection<Document> idMapping = db.getCollection("idMapping");
        FindIterable<Document> allDocs = idMapping.find();
        List<ObjectId> toRemove = new ArrayList<>();
        Consumer<Document> dupeFinder = document -> {
            String id = document.getString("syncariId");
            boolean handled = duplicates.contains(id) ? toRemove.add(document.getObjectId("_id")) : duplicates.add(id);
        };
        allDocs.forEach(dupeFinder);
        idMapping.deleteMany(Filters.in("_id",toRemove));

        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("idMapping", List.of(
                //A syncari record must have a single id-mapping entry
                new Index(true,"syncariId"),
                //fiind externalId By syncariId and other fields
                new Index(false,"syncariId", "mappings.connectorId","mappings.entityDefinitionId"),
                //find syncariId by externalId (entityId) and other fields
                new Index( false,"mappings.connectorId","mappings.entityDefinitionId","mappings.entityId")
                ));
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
