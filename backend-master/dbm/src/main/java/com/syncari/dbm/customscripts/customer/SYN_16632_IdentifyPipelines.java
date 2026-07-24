package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.model.SyncStream;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Updates.set;

@Slf4j
public class SYN_16632_IdentifyPipelines {

    @ChangeSet(order = "001", id = "SYN_16632_IdentifyPipelines", author = "venkat", runAlways = true)
    public void unpausePipeline(MongoTemplate template) {
        MongoCollection<Document> mappingGraph = template.getCollection("mappingGraph");
        MongoCollection<Document> syncStream = template.getCollection("syncStream");
        var dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        List<Document> docs = syncStream.find(new Document("status" , "PAUSED").append("errorDetail.message", "No Active Sources for this Pipeline"))
                .projection(new Document("graphId" , 1)).into(new ArrayList<>());

        try {
            var graphService = MigrationContext.getMappingGraphService();
            var schemaService = MigrationContext.getSchemaService();
            var connectorService = MigrationContext.getConnectorService();

            List<String> graphIds = docs.stream().map(d -> d.get("graphId").toString()).collect(Collectors.toList());
            var graphs = graphIds.stream().map(id -> graphService.retrieve(id)).flatMap(Optional::stream).collect(Collectors.toList());


            graphs.stream().forEach(g -> {
                var sources = g.getConnectedSources().map(n -> schemaService.getEntity(((EntitySourceNodeConfig) n.getConfiguration()).getEntityDefinition().getId()))
                        .filter(entity -> connectorService.getSyncariConnector().getId().equals(entity.getConnectorId())
                                || connectorService.find(entity.getConnectorId(), false).filter(e -> e.isActive()).isPresent()).collect(Collectors.toList());
                if (!sources.isEmpty()) {
                    // if the source is not empty
                    log.info("Instance {}, Paused pipeline {}, {}. Inactive Sources {}", MigrationContext.getSyncariId(),  g.getTargetId(), g.getName(), sources.stream().map(EntityDefinition::getDisplayName).collect(Collectors.joining(",")));
                    if (!dryRun) {
                        var stream = syncStream.find(new Document("graphId", g.getId())).first();
                        if(stream != null) {
                            log.info("Found stream with status {} for graph {}", stream.get("graphId"), g.getTargetId());
                            var result = syncStream.updateOne(stream, set("status", SyncStream.Status.READY.name()));
                            log.info("Updated - {}", result.getModifiedCount());
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.info("Error with script " + e.getMessage());
        }
    }
}
