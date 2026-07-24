package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

@Slf4j
public class SYN_11501_FindEntityRepoAndStagedExternalMismatch {

    @ChangeSet(order = "001", id = "findEntityRepoAndStagedExternalMismatch", author = "blesson", runAlways = true)
    public void findEntityRepoAndStagedExternalMismatch(MongoTemplate template) {
        String syncariFieldApiName = System.getProperty("syncariFieldApiName");
        String externalFieldApiName = System.getProperty("externalFieldApiName");
        String entityApiName = System.getProperty("entityApiName");
        String externalEntityDefinitionId = System.getProperty("externalEntityDefinitionId");
        MongoCollection<Document> entityRepo = template.getCollection("syncari_" + entityApiName.toLowerCase());
        MongoCollection<Document> idMappingRepo = template.getCollection("idMapping");
        MongoCollection<Document> stagedExternalRepo = template.getCollection("stagedExternalRecord");
        MongoCursor<Document> cursor = entityRepo.find(new Document("isDeleted", false)).batchSize(1000).iterator();
        while (cursor.hasNext()) {
            Document syncariRecord = cursor.next();
            ObjectId syncariId = syncariRecord.getObjectId("_id");
            Document idMapping = idMappingRepo.find(new Document("syncariId", syncariId.toString())).first();
            if(idMapping != null) {
                var mappings = (List<Document>)idMapping.get("mappings");
                mappings.forEach(mapping -> {
                    String entityDefinitionId = mapping.getString("entityDefinitionId");
                    if(entityDefinitionId.equalsIgnoreCase(externalEntityDefinitionId)) {
                        var externalRecordId = mapping.getString("entityId");
                        Document externalRecord = stagedExternalRepo.find(new Document("externalRecordId", externalRecordId).append("externalEntityDefinitionId", externalEntityDefinitionId)).first();
                        if(externalRecord != null) {
                            Document entityData = (Document) externalRecord.get("entityData");
                            Document values = (Document) entityData.get("values");
                            Object externalValue = values.get(externalFieldApiName);
                            Object syncariValue = syncariRecord.get(syncariFieldApiName);
                            if((syncariValue != null && externalValue == null) || (syncariValue == null && externalValue != null)) {
                                log.info("Syncari record with id {} has field value {} and external record has value {}", syncariId, syncariValue, externalValue);
                            }
                            if(syncariValue instanceof String && externalValue instanceof String) {
                                if(((String)syncariValue).compareTo((String)externalValue) != 0) {
                                    log.info("Syncari record with id {} has field value {} and external record has value {}", syncariId, syncariValue, externalValue);
                                }
                            }
                        }
                    }
                });
            }
        }
    }
}
