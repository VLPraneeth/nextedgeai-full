package com.syncari.core.changelogs.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.syncari.core.model.util.Status;
import com.syncari.core.Index;
import com.syncari.core.MigrationContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.repositories.customer.AttributeRepo;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ChangeLog(order="0042")
public class M0042_FixDuplicateAttribute {

    @ChangeSet(order = "001", id = "archiveDuplicateAttributes", author = "abhinav")
    public void archiveDuplicateAttributes(MongoTemplate db) {

        AttributeRepo attributeRepo = MigrationContext.getAttributeRepo();
        List<AttributeDefinition> allAttributes = attributeRepo.findAll();

        List<AttributeDefinition> toUpdate = new ArrayList<>();
        List<AttributeDefinition> archived = allAttributes.stream().filter(a -> a.isArchived()).collect(Collectors.toList());
        allAttributes.removeAll(archived);
        Map<String, List<AttributeDefinition>> byEntityIdApiName = allAttributes.stream()
                .collect(Collectors.groupingBy(e->e.getEntityId()+"_"+e.getApiName()));

        byEntityIdApiName.forEach((k, v) -> {
            // sort the attributes in descending order of createdAt keeping nulls as older records
            List<AttributeDefinition> sorted = v.stream().sorted(Comparator.comparing(AttributeDefinition::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());
            if(v.size() > 1) {
                log.info("Found {} attributes for entityId_apiName combination of {}", sorted.size(), k);
            }
            // remove first element from reverse sorted list by createdDate and add others for archiving
            sorted.remove(0);
            toUpdate.addAll(sorted);
        });

        // filter out attributes which were renamed before
        List<AttributeDefinition> notArchivedBefore = archived.stream().filter(a -> !a.getApiName().endsWith("_DELETED")).collect(Collectors.toList());
        toUpdate.addAll(notArchivedBefore);

        toUpdate.forEach(a -> {
            log.info("Archiving duplicate attribute {} with id {}", a.getApiName(), a.getId());
            a.setApiName(String.format("%s_%s_DELETED", a.getApiName(), a.getId()));
            a.setDraftStatus(DraftStatus.ARCHIVED);
        });
        attributeRepo.saveAll(toUpdate);
    }

    @ChangeSet(order = "002", id = "createUniqueIndexForAttributeDef", author = "abhinav")
    public void createUniqueIndexForAttributeDef(MongoTemplate db) {
        MongoCollection<Document> collection = db.getCollection("attributeDefinition");
        collection.dropIndexes();

        create(db, Map.of("attributeDefinition", List.of(new Index("entityId", "apiName"))));
    }

    @ChangeSet(order = "003", id = "fixDeletedAttributesApiName", author = "abhinav")
    public void fixDeletedAttributesApiName(MongoTemplate db) {
        AttributeRepo attributeRepo = MigrationContext.getAttributeRepo();
        List<AttributeDefinition> allAttributes = attributeRepo.findAll();
        List<AttributeDefinition> deletedWithoutRename = allAttributes.stream()
                .filter(a -> (a.isArchived() || Status.DELETED.equals(a.getStatus())) && !a.getApiName().endsWith("_DELETED") )
                .collect(Collectors.toList());

        log.info("Found {} DELETED attributes for renaming", deletedWithoutRename.size());

        deletedWithoutRename.forEach(a -> {
            String deletedName = String.format("%s_%s_DELETED", a.getApiName(), a.getId());
            log.info("Renaming DELETED attribute apiName from {} to {}", a.getApiName(), deletedName);
            a.setApiName(deletedName);
            a.setDraftStatus(DraftStatus.ARCHIVED);
            a.setStatus(Status.DELETED);
        });
        attributeRepo.saveAll(deletedWithoutRename);
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
