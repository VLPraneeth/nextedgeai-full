package com.syncari.core.changelogs.customer;

import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.syncari.connector.Constants;
import com.syncari.core.Index;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeLog(order="0046")
public class M0046_FixEntityDefinitionIndex {

    @ChangeSet(order = "001", id = "addUniqueIndexForEntityDef", author = "varsha")
    public void addUniqueIndexForEntityDef(MongoTemplate db) {
//        removeDuplicates(db);
        
        // Create the unique index
        MongoCollection<Document> collection = db.getCollection("entityDefinition");
        collection.dropIndexes();
        create(db, Map.of("entityDefinition", List.of(new Index("connectorId", "apiName", "draftStatus"))));
    }

    private void removeDuplicates(MongoTemplate db) {
        String syncariConnector = getSyncariConnector(db);
        EntityDefinitionRepo entityRepo = MigrationContext.getEntityDefinitionRepo();
        AttributeRepo attrRepo = MigrationContext.getAttributeRepo();
        // Pick non syncari entities
        List<EntityDefinition> entities = entityRepo.findAll().stream()
                .filter(e -> (!syncariConnector.equalsIgnoreCase(e.getConnectorId()) && e.isApproved()))
                .collect(Collectors.toList());

        List<EntityDefinition> toDelete = new ArrayList<>();
        Map<String, List<EntityDefinition>> byConnectorIdApiName = entities.stream()
                .collect(Collectors.groupingBy(e->e.getConnectorId()+"_"+e.getApiName()));

        List<AttributeDefinition> toDeleteAttr = new ArrayList<>();

        byConnectorIdApiName.forEach((k, v) -> {
            // sort the entities in descending order of createdAt keeping nulls as older records
            List<EntityDefinition> sorted = v.stream().sorted(Comparator.comparing(EntityDefinition::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());
            if(v.size() > 1) {
                log.info("Found {} entities for connectorid_apiName combination of {}", sorted.size(), k);
            }
            // Retain the latest entity def and add others for deletion
            sorted.remove(0);
            sorted.forEach(e -> toDeleteAttr.addAll(e.getAttributes()));
            toDelete.addAll(sorted);
        });

        attrRepo.deleteAll(toDeleteAttr);
        entityRepo.deleteAll(toDelete);
    }
    
    private String getSyncariConnector(MongoTemplate db) {
        MongoDatabase syncariDb = MigrationContext.getSyncariDB();
        MongoCollection<Document> metadata = syncariDb.getCollection("connectorMetadata");
        Document syncariConnectorMeta = metadata.find(eq("name", Constants.SYNCARI)).first();
        MongoCollection<Document> connector = db.getCollection("connector");
        Document syncariConnector = connector.find(eq("metadataId", syncariConnectorMeta.get("_id").toString())).first();
        return syncariConnector.getObjectId("_id").toHexString();
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
