package com.syncari.core.changelogs.customer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.syncari.core.MigrationUtil;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.syncari.core.Index;

@ChangeLog(order = "0021")
public class M0021_CreateIndexes {
    @ChangeSet(order = "001", id = "createUniqueIndexes", author = "varsha")
    public void createUniqueIndexes(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("schemaMapping", List.of(new Index(true, "connectorId", "synapseObjectId", "syncariId", "scope")));
        createIndexes(db, indexMap);
    }
    
    @ChangeSet(order = "002", id = "createUniqueIndexesForComponentDep", author = "varsha")
    public void createUniqueIndexesForComponentDep(MongoTemplate db) {
        Set<String> uniqueEntries = new HashSet<>();
        MongoCollection<Document> componentDependencies = db.getCollection("componentDependency");
        componentDependencies.find().forEach(new Consumer<Document>() {
            @Override
            public void accept(Document d) {
                String key =String.format("%s_%s_%s_%s", d.get("fromId"),d.get("fromComponent"),d.get("toId"),d.get("toComponent"));
                if(uniqueEntries.contains(key)) {
                    componentDependencies.deleteOne(Filters.eq("_id", d.getObjectId("_id")));
                }else {
                    uniqueEntries.add(key);
                }
            }
        });
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("componentDependency", List.of(new Index(true, "fromId", "fromComponent", "toId", "toComponent")));
        createIndexes(db, indexMap);
    }
    
    @ChangeSet(order = "003", id = "createUniqueIndexesForEnrichCache", author = "varsha")
    public void createUniqueIndexesForEnrichCache(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("enrichmentCache", List.of(new Index(true, "serviceId", "entityName", "enrichKey")));
        createIndexes(db, indexMap);
    }

    
    @ChangeSet(order = "004", id = "createIndexesForNotification", author = "varsha")
    public void createIndexesForNotification(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("notification", List.of(new Index(false, "userId")));
        createIndexes(db, indexMap);
    }

    @ChangeSet(order = "005", id = "createIndexOnKeyForNotification", author = "abhinav")
    public void createIndexOnKeyForNotification(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("notification", List.of(new Index(false, -1, "userId", "key", "createdAt")));
        createIndexes(db, indexMap);
    }

    @ChangeSet(order = "006", id = "createUniqueIndexOnUserForUserRole", author = "varsha")
    public void createUniqueIndexOnUserForUserRole(MongoTemplate db) {
        Map<String, List<Index>> indexMap = new HashMap<String, List<Index>>();
        indexMap.put("userRole", List.of(new Index(true, "userId")));
        createIndexes(db, indexMap);
    }

    @ChangeSet(order = "007", id = "createTTLIndexApiErrorLog", author = "rohit")
    public void createTTLIndexApiErrorLog(MongoTemplate db) {
        long duration = Long.valueOf(60 * 60 * 24 * 7);
        String indexName = String.format("%s_%s_TTL_%sDays", "apiErrorLog", "updatedAt", 7);
        MigrationUtil.createIndex(db, Map.of("apiErrorLog",
                List.of(new Index(indexName, false, false, duration, "updatedAt"))));
    }

    private void createIndexes(MongoTemplate db, Map<String, List<Index>> indexMap) {
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