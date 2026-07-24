package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SYN_13452_Datafix {

    @ChangeSet(order = "001", id = "forceSyncariSink", author = "venkat", runAlways = true)
    public void forceSyncariSink(MongoTemplate template) {

        MongoCollection<Document> txnLog = template.getCollection("transactionLog");
        String fromDate = System.getProperty("fromDate");
        String toDate = System.getProperty("toDate");
        boolean dryRun = Boolean.parseBoolean(System.getProperty("dryRun"));

        int pageSize = 2000;
        int writeBatchSize = 1000;

        log.info("Start Date {} End Date {}", fromDate, toDate);
        var filter = Filters.and(Filters.in("operation", "create", "update", "merge"), Filters.gte("createdAt", Instant.parse(fromDate)),
                Filters.lt("createdAt", Instant.parse(toDate)));

        var aggregationList = Arrays.asList(Aggregates.match(filter),
                Aggregates.group(new Document("entityId", "$entityId").append("entityName", "$entityName").append("syncariId", "$syncariId"),
                        Accumulators.sum("count", new Document("$sum", 1))), Aggregates.project(new Document("_id", 0)
                        .append("entityId", "$_id.entityId").append("entityName", "$_id.entityName").append("syncariId", "$_id.syncariId")), Aggregates.sort(new Document("entityName", 1)));


        var txnIterator = txnLog.aggregate(aggregationList).allowDiskUse(true).iterator();

        Set<String> syncariIds = new HashSet<>();
        String entityName = "";
        String entityId = "";
        Map<String, List<EntityDefinition>> connectorSinkMap = new HashMap<>();
        int count = 0;
        while(txnIterator.hasNext()) {
            var txn = txnIterator.next();
            if (!entityName.equals(txn.getString("entityName"))) {
                if (syncariIds.size() > 0) {
                    update(entityName, syncariIds, template, connectorSinkMap, dryRun);
                }
                entityName = txn.getString("entityName");
                entityId = txn.getString("entityId");
                // create a connector to dest entity mapping for this syncari entity
                connectorSinkMap = connectorSinkMap(entityId, template);
                syncariIds.clear();
            }
            syncariIds.add(txn.getString("syncariId"));
            if(syncariIds.size() > writeBatchSize) {
                if (!dryRun) {
                    update(entityName, syncariIds, template, connectorSinkMap, dryRun);
                }
                syncariIds.clear();
            }

            if (count % 5000 == 0) {
                log.info("Processed {} records", count);
            }
            count++;
        }

        if (!StringUtils.isBlank(entityName)) {
            if (!dryRun) {
                update(entityName, syncariIds, template, connectorSinkMap, dryRun);
            }
        }
    }

    private String toCollectionName(String entityName) {
        return "syncari_" + entityName.toLowerCase();
    }

    private Map<String, List<EntityDefinition>> connectorSinkMap(String syncariEntityId, MongoTemplate template) {
        var mappingGraphService = MigrationContext.getMappingGraphService();
        var connectorEntityMap = mappingGraphService.retrieveEntityGraph(syncariEntityId)
                .map(g -> getConnectorToEntityMapForSinks(g.getId())).orElse(Map.of());

        return connectorEntityMap;
    }

    private void update(String entityName, Set<String> ids, MongoTemplate template, Map<String, List<EntityDefinition>> connectorSinkMap, boolean dryRun) {

        var idMappingRepo = MigrationContext.getIdMappingRepo();

        var mappings = idMappingRepo.findBySyncariIds(entityName, ids);

        var idsToUpdate = mappings.stream().filter(idMapping -> {
            for (var connectorId : connectorSinkMap.keySet()) {
                List<String> sinkEntityDefIds = connectorSinkMap.get(connectorId).stream().map(e -> e.getId()).collect(Collectors.toList());
                // if there is atleast one destination with no mapping then we need to update
                for (var sinkEntityDefId : sinkEntityDefIds) {
                    if (idMapping.getAllMappings(connectorId, sinkEntityDefId).size() == 0) {
                        return true;
                    }
                }
            }
            return false;
        }).map(m -> m.getSyncariId()).collect(Collectors.toList());

        if (idsToUpdate.size() > 0) {
            log.info("Updating {} {} records", entityName, idsToUpdate.size());
            if (!dryRun) {
                MongoCollection<Document> syncariEntity = template.getCollection(toCollectionName(entityName));

                var objectIds = idsToUpdate.stream().map(id -> new ObjectId(id)).collect(Collectors.toList());

                final UpdateResult updateResult = syncariEntity.updateMany(
                        new Document("_id", new Document("$in", objectIds)),
                        new Document("$set", new Document("syncariTimestamp", Instant.now().toEpochMilli()))
                        , new UpdateOptions().upsert(false));
                log.info(updateResult.toString());
            }

        }

    }

    public Map<String, List<EntityDefinition>> getConnectorToEntityMapForSinks(String graphId) {
        var mappingGraphService = MigrationContext.getMappingGraphService();
        var entityRepo = MigrationContext.getEntityDefinitionRepo();

        Optional<MappingGraph> graph = mappingGraphService.retrieve(graphId);
        if (graph.isEmpty())
            throw new RuntimeException(String.format("Graph with id {} not found", graphId));
        Map<String, List<EntityDefinition>> sinkMap = new HashMap<>();
        Stream<MappingNode> sinks = graph.get().getSinks();
        sinks.forEach(s -> {
            String defId = s.getConfiguration().getConfigMap().get("entityDefinition").toString();
            EntityDefinition def = entityRepo.findById(defId).get();
            sinkMap.computeIfAbsent(def.getConnectorId(), k -> new ArrayList<EntityDefinition>()).add(def);
        });
        return sinkMap;
    }

}
