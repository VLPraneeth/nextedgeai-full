package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.print.Doc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class SYN_11005_ReferencesFix {


    @ChangeSet(order = "001", id = "resetMappingGraphLock", author = "abhinav", runAlways = true)
    public void resetMappingGraphLock(MongoTemplate template) {

        String incorrectReferenceEntity = System.getProperty("incorrectReferenceEntity");
        String referenceEntity = System.getProperty("referenceEntity");

        // external ids
        String connectorId = System.getProperty("connectorId");
        String externalEntityDefinitionId = System.getProperty("externalEntityDefinitionId");

        // Map of referring-entity and field
        // format - referenceEntityMap=contact:AccountId&lead:AccountID
        String referenceEntityMap = System.getProperty("referenceEntityMap");
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        Map<String, String> entityReference = Arrays.stream(referenceEntityMap.split("&"))
                .collect(Collectors.toMap(s -> s.split(":")[0], s -> s.split(":")[1]));

        MongoCollection<Document> incorrectReference = template.getCollection("syncari_" + incorrectReferenceEntity.toLowerCase());

        List<Document> incorrectDocs = new ArrayList<>();
        incorrectReference.find().projection(new Document("_id", 1)).into(incorrectDocs);
        var incorrectReferences = incorrectDocs.stream().map(d -> d.getObjectId("_id").toHexString()).collect(Collectors.toList());

        var idMapping = template.getCollection("idMapping");

        entityReference.entrySet().stream().forEach(e -> {
            var syncariColl = template.getCollection("syncari_" + e.getKey().toLowerCase());
            syncariColl.find(new Document(e.getValue(), new Document("$in", incorrectReferences))).forEach((Block<? super Document>) doc -> {

                String entityId = ((List<Document>) idMapping.find(new Document("entityName", incorrectReferenceEntity)
                        .append("syncariId", doc.getString(e.getValue()))
                        .append("mappings.connectorId", connectorId)
                        .append("mappings.entityDefinitionId", externalEntityDefinitionId)).first().get("mappings")).get(0).getString("entityId");

                log.info("Querying Entity Name {} Connector Id {} External Entity {} Entity ID {}", referenceEntity, connectorId, externalEntityDefinitionId, entityId);

                // take this entityId and find the original syncari id.
                String syncariId = idMapping.find(new Document("entityName", referenceEntity).append("mappings.connectorId", connectorId)
                        .append("mappings.entityDefinitionId", externalEntityDefinitionId).append("mappings.entityId", entityId))
                        .first().getString("syncariId");

                log.info("Correct Syncari Id {}, to set in entity {} field {} row id {}", syncariId, e.getKey(), e.getValue(), doc.getObjectId("_id").toHexString());

                if (!dryRun) {
                    syncariColl.findOneAndUpdate(new Document("_id", doc.getObjectId("_id")), new Document("$set", new Document(e.getValue(), syncariId)));
                }
            });
        });
    }

}
