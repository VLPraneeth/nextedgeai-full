package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.syncari.connector.Operation;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.TransactionLog;
import com.syncari.core.model.misc.Source;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SYN_10372_RemoveMappingByEntityDefinition {

    @ChangeSet(order = "001", id = "removeMappingByEntityDefinition", author = "venkat", runAlways = true)
    public void removeMappingByEntityDefinition(MongoTemplate template) throws Exception {

        /*var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));
        String externalEntityId = System.getProperty("externalEntityId");
        String entityName = System.getProperty("entityName");
        MongoCollection<Document> idMapping = template.getCollection("idMapping");
        TransactionLogRepo transactionLogRepo = MigrationContext.getTransactionLogRepo();
        var connectorService = MigrationContext.getConnectorService();


        log.info("Removing mapping for entityName {} and externalEntityId {}", entityName, externalEntityId);

        var filter = Filters.and(Filters.eq("entityName", entityName),
                Filters.elemMatch("mappings", Filters.eq("entityDefinitionId", externalEntityId)));

        var idMappings = idMapping.find(filter)
                .projection(Projections.elemMatch("mappings", Filters.eq("entityDefinitionId", externalEntityId))).into(new ArrayList<>());
        log.info("Total count of mapped records {}", idMappings.size());
        for (Document d : idMappings) {
            List<Document> mappings = (List<Document>) d.get("mappings");
            log.info("Id {} entityId {} entityDefinitionId {} Connector Id {} Disconnected {}",
                    ((ObjectId)d.get("_id")).toHexString(), mappings.get(0).get("entityId"), mappings.get(0).get("entityDefinitionId"),
                    mappings.get(0).get("connectorId"), mappings.get(0).get("disconnected"));

            Document fields = new Document().append("mappings", new Document().append( "entityId",mappings.get(0).get("entityId"))
                    .append("entityDefinitionId", mappings.get(0).get("entityDefinitionId")).append("connectorId", mappings.get(0).get("connectorId")).append("disconnected", mappings.get(0).get("disconnected")));

            if (!dryRun) {
                idMapping.updateOne(new Document("_id", d.get("_id")), new Document("$pull", fields));
                var txnLog = new TransactionLog().setEntityName(entityName)
                        .setOperation(Operation.disconnect)
                        .setSyncariId(((ObjectId)d.get("_id")).toHexString())
                        .setSources(List.of(new Source().setExternalId(mappings.get(0).get("entityId").toString())
                                .setEntityDefinitionId(mappings.get(0).get("entityDefinitionId").toString())
                                .setConnectorId(mappings.get(0).get("connectorId").toString())
                                .setConnectorName(connectorService.find(mappings.get(0).get("connectorId").toString(), false).get().getName())));
                transactionLogRepo.save(txnLog);
            }
        }*/
    }
}
