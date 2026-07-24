package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;
import static com.mongodb.client.model.Updates.*;

@Slf4j
public class FixSyncariIdUnresolvedReferences {

    @ChangeSet(order = "001", id = "fixSyncariIdUnresolvedReferences", author = "venkat")
    public void fixSyncariIdUnresolvedReferences(MongoTemplate template) {
        findAndFixSyncariUnresolvedReferences(template, "Campaign"); // for Inkling
        findAndFixSyncariUnresolvedReferences(template, "User"); // for Servicemax
    }

    private void findAndFixSyncariUnresolvedReferences(MongoTemplate template, String externalEntity) {

        MongoCollection<Document> unResolvedReferences = template.getCollection("unresolvedReference");
        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        MongoCollection<Document> entityDef = template.getCollection("entityDefinition");

        unResolvedReferences.find(Filters.and(new Document("externalRefEntityName", externalEntity),
                Filters.regex("externalRefRecordId", "^[0-9a-fA-F]{24}$")))
                .forEach((Block<? super Document>) doc -> {

                    ObjectId unresolvedReferenceObjectId = doc.getObjectId("_id");
                    String externalRefRecordId = (String)doc.get("externalRefRecordId");
                    String connectorId = (String)doc.get("connectorId");

                    log.info("UnresolvedRefId {}, ExternalRefId/SyncariId {}, connectorId {}", unresolvedReferenceObjectId, externalRefRecordId, connectorId);

                    // get the corresponding entityDefId
                    String externalEntityDefId = entityDef.find(new Document("connectorId", connectorId).append("apiName", externalEntity)).
                            first().getObjectId("_id").toHexString();

                    // look up idMapping
                     idMapping.find(new Document("entityName", externalEntity).append("syncariId", externalRefRecordId)
                            .append("mappings.connectorId", connectorId)
                            .append("mappings.entityDefinitionId", externalEntityDefId)
                    ).forEach((Block<? super Document>) id -> {
                         Optional<String> entityId = ((List<Document>) id.get("mappings")).stream().filter(
                                 map -> map.get("connectorId").equals(connectorId) && map.get("entityDefinitionId").equals(externalEntityDefId)).
                                 map(d -> d.getString("entityId")).findFirst();

                         if (entityId.isPresent()) {
                             log.info("Update record with Id {} in collection unresolvedReference: Attribute externalRefRecordId to {}", unresolvedReferenceObjectId.toHexString(), entityId.get());
                             if (unresolvedReferenceObjectId != null && entityId.get().matches("[a-zA-Z0-9]{18}|[a-zA-Z0-9]{15}")) {
                                 log.info("Collection {}", unResolvedReferences.toString());

                                 unResolvedReferences.findOneAndUpdate(new Document("_id", unresolvedReferenceObjectId), set("externalRefRecordId", entityId.get()));
                             }
                         }
                     });
         });
    }
}
