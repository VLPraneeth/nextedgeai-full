package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_12391_FixMarketoLeadId {

    @ChangeSet(order = "001", id = "updateEntityIdFromIdMapping", author = "rohit", runAlways = true)
    public void updateEntityIdFromIdMapping(MongoTemplate template) {
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String collectionName = System.getProperty("collectionName");
        String connectorId = System.getProperty("connectorId"); // 63bd83af22d2e70001c458bd
        String entityDefId = System.getProperty("entityDefId"); // 63bd83bc22d2e70001c45916
        String fieldName = System.getProperty("fieldName"); // marketo_lead_id
        log.info("Parameters with collectionName {}, connectorId {}, entityDefId {}, fieldName {}", collectionName, connectorId, entityDefId, fieldName);

        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        MongoCollection<Document> syncari_lead = template.getCollection(collectionName);
        log.info("Document to find {}", new Document(fieldName, "null"));

        var leadsWithEmptyField = syncari_lead.find(Filters.eq(fieldName,null));
        leadsWithEmptyField.forEach((Block<? super Document>) lead -> {
            var syncariId = lead.getObjectId("_id").toHexString();

            log.info("DB: {}, Entity with syncariId {},", template.getDb().getName(),
                    syncariId);
            // query Id Mapping for given syncariId
            var idMappingIterator = idMapping.find(new Document("syncariId", syncariId));
            idMappingIterator.forEach((Block<? super Document>) idM ->{
                var mappings = (List)idM.get("mappings");
                var mappingOneItemList = mappings.stream().filter(x ->
                    (((Map<String, String>)x).get("connectorId")).toString().equalsIgnoreCase(connectorId)
                            && (((Map<String, String>)x).get("entityDefinitionId")).equalsIgnoreCase(entityDefId)
                ).collect(Collectors.toList());
                ((List)mappingOneItemList).stream().findFirst().ifPresentOrElse(mapping -> {
                    // get Marketo lead id and update if not dryRun
                    String fieldExternalId = ((Map<String, String>)mapping).get("entityId");
                    if (!dryRun){
                        // update the value without modifying updated at
                        log.info("Updating values in collection {}.Field Name {}, Field id {} to be added  for syncari id {}", collectionName, fieldName, fieldExternalId, syncari_lead);
                        syncari_lead.findOneAndUpdate(new Document("_id", new ObjectId(syncariId)), set(fieldName, fieldExternalId));
                    }else{
                        // just log what will be the value to update
                        log.info("Not updating running in dry run.Field Name {}, Field id {} to be added  for syncari id {}", fieldName, fieldExternalId, syncari_lead);
                    }
                },() -> {
                    log.info("Mapping of respective connector {}, and entitydef {} does not exists", connectorId, entityDefId);
                });
            });
        });
    }
}
