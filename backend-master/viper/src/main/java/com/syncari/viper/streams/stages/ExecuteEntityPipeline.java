package com.syncari.viper.streams.stages;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.SyncRequest;
import com.syncari.core.DataTransformer;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.EntitySyncStatusMetric;
import com.syncari.core.model.misc.EntitySynchStatusMetricSummary;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.pipeline.*;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.core.service.*;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.EntitySourceHelper;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import com.syncari.viper.ViperContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class ExecuteEntityPipeline {

    private static final Set<MappingNodeType> ACTION_TERMINALS = Set.of(MappingNodeType.ATTRIBUTE_SOURCE, MappingNodeType.ENTITY_SOURCE);
    private static final Set<MappingNodeType> OTHER_TERMINALS = Set.of(MappingNodeType.CORE_ATTRIBUTE, MappingNodeType.CORE_ENTITY,MappingNodeType.ENTITY_SINK,MappingNodeType.ATTRIBUTE_SINK);
    ConnectorService connectorService;
    IdMappingRepo idMappingRepo;
    PipelineEvaluator evaluator;
    DataServiceFactory dataServiceFactory;
    DataTransformer transformer;
    EntityRepoService entityRepoService;
    SchemaService schemaService;
    SyncDetailMetricService syncDetailMetricService;
    UnresolvedReferenceRepo unresolvedReferenceRepo;
    EntitySourceHelper helper;
    WatermarkService watermarkService;

    FeatureService featureService;

    @Autowired
    public ExecuteEntityPipeline(ConnectorService connectorService, IdMappingRepo idMappingRepo, PipelineEvaluator pipelineEvaluator,
                                 DataServiceFactory dataServiceFactory, DataTransformer dataTransformer, EntityRepoService entityRepoService,
                                 SchemaService schemaService, SyncDetailMetricService syncDetailMetricService, UnresolvedReferenceRepo unresolvedReferenceRepo,
                                 EntitySourceHelper helper, WatermarkService watermarkService, FeatureService featureService){
        this.connectorService = connectorService;
        this.idMappingRepo = idMappingRepo;
        this.evaluator = pipelineEvaluator;
        this.dataServiceFactory = dataServiceFactory;
        this.transformer = dataTransformer;
        this.entityRepoService = entityRepoService;
        this.schemaService = schemaService;
        this.syncDetailMetricService = syncDetailMetricService;
        this.unresolvedReferenceRepo = unresolvedReferenceRepo;
        this.helper = helper;
        this.watermarkService = watermarkService;
        this.featureService = featureService;
    }

    public ExecuteEntityPipeline(){

    }

    public GraphContext execute(ViperContext context, GraphContext graphContext) {
        var batch = graphContext.getCurrentBatch();
        execute(context, graphContext,batch.getEntityBatches(), true);
        execute(context, graphContext,batch.getConnectedEntityBatches(),false);
        graphContext.clearCache("_advancedAttachRecordResults");
        return graphContext;
    }
    protected GraphContext execute(ViperContext context, GraphContext graphContext, Map<EntityDefinition, StagedBatch> batches, boolean processConnectedRecords) {

        var batch = graphContext.getCurrentBatch();
        var entityGraph = graphContext.getGraph();
        var coreNode = entityGraph.getCoreNode();

        EntityDefinition coreEntity = schemaService.getEntity(entityGraph.getTargetId());
        var sourceNodes = entityGraph.getConnectedSources().collect(Collectors.toList());
        log.info("Stage: ExecuteEntityPipeline for graph {}, staging id {}",entityGraph.getName(),batch.getCurrentBatchId());

        AtomicInteger removeCount = new AtomicInteger(0);
        AtomicInteger updateCount = new AtomicInteger(0);
        Map<String, Connector> connectorMap = new HashMap<>();
        batches.forEach((entityDefintion, cb) -> {
            Timer timer = new Timer("ExecuteEntityPipeline::execute::batch::connector id" + entityDefintion.getConnectorId());
            var connector = connectorService.refreshAuthentication(graphContext.cache(entityDefintion.getConnectorId(),()->connectorService.get(entityDefintion.getConnectorId())));
            connectorMap.put(connector.getId(), connector);
            var fieldNameToIdMap = entityDefintion.getAttributes().stream().collect(Collectors.toMap(a->a.getApiName(),a->a.getId()));

            long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
            EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityDefintion.getApiName(), null,
                    (float)timer.getTimeTakenUntilNow(),0, 0,0);
            updateSyncDetailMetricDuration(syncStatusMetric, coreEntity.getId(),batch.getCurrentBatchId(),totalDurationtillNow);

            batch.iterator(cb).forEachRemaining(records -> {
                List<StagedBatchRecord> toRemove = new ArrayList<>();
                List<StagedBatchRecord> toUpdate = new ArrayList<>();
                var updatedRecords = attachSyncariIds(batch,entityDefintion,connector, records);
                Iterable<EntityData> syncariRecords = entityRepoService.findByIds(entityGraph.getTargetId(), updatedRecords.stream().map(e -> e.getSyncariId()).collect(Collectors.toSet()));
                Map<String, EntityData> syncariRecordById = new HashMap<>();
                syncariRecords.forEach(record->{
                    syncariRecordById.put(record.getSyncariEntityId(),record);
                });
                for (StagedBatchRecord record : updatedRecords) {
                    log.debug("EEP: Processing record with syncariId: {} and externalId: {}", record.getSyncariId(), record.getExternalRecordId());
                    if (StringUtils.isBlank(record.getExternalRecordId())) {
                        log.error("Skipping StagedBatch Record Id {} for Syncari Id {} because external record id is null or empty, which should not be from Synapse", record.getId(), record.getSyncariId());
                        continue;
                    }
                    var executionContext = graphContext.copy().set("syncariContext", context);
                    executionContext.setCurrentSyncariId(record.getSyncariId());
                    //Initialize executionContext with current record against the appropriate source node
                    // that matches the record's external entity definition id
                    sourceNodes.stream().filter(node-> {
                        EntitySourceNodeConfig configuration = (EntitySourceNodeConfig) node.getConfiguration();
                        if(record.getExternalEntityDefinitionId().equals(configuration.getEntityDefinition().getId())){
                            return true;
                        } else {
                            var srcInput = new FunctionResult(FilterFailedResult.VALUE, ObjectType.VALUE);
                            executionContext.put("output_" + node.getId(), Pair.of(srcInput, node));
                            executionContext.captureTestOutputForNode(srcInput, node, null);
                            return false;
                        }
                    }).forEach(sourceNode -> {
                        log.debug("Seeding source for node {} with id {} output_{}",sourceNode.getName(),sourceNode.getId(),record);
                        var srcInput = new FunctionResult(record.getEntityData(), ObjectType.VALUE);
                        executionContext.put("output_" + sourceNode.getId(), Pair.of(srcInput, sourceNode));
                        executionContext.captureTestOutputForNode(srcInput, sourceNode, null);
                    });
                    executionContext.put(PipelineHelper.INCOMING_CHANGE_FIELD,record.isNew() ?  "insert":"update");
                    record.getEntityData().getValues().forEach((apiName,value) -> {
                        executionContext.put("field_"+fieldNameToIdMap.get(apiName),value);
                    });
                    //Setup context for syncari record, if present
                    Optional<EntityData> syncariRecord = Optional.ofNullable(syncariRecordById.get(record.getSyncariId()));
                    syncariRecord.ifPresent(sRecord -> {
                                executionContext.put("existing", sRecord);
                                coreEntity.getActiveAttributes().forEach(attribute -> {
                                    if (sRecord.has(attribute.getApiName())) {
                                        executionContext.put("field_" + attribute.getId(), sRecord.getValue(attribute.getApiName()));
                                    }
                                });
                            }
                    );
                    if ((syncariRecord.isEmpty() || syncariRecord.get().isDeleted()) && record.getEntityData().isDeleted()) {
                        log.info("Skipping deleted incoming record with syncariId {}, as it is not present/already deleted in Syncari", record.getSyncariId());
                        toRemove.add(record);
                        continue;
                    }

                    if (!record.getEntityData().isDeleted() && (!syncariRecord.isEmpty() && syncariRecord.get().isDeleted())) {
                        // check if the record is already deleted in syncari
                        if (checkRecordDestinationProcessed(coreEntity, entityGraph, record, syncariRecord.get(), graphContext)) {
                            log.info("Skipping incoming record with syncariId {}, as it is marked as deleted in syncari but the watermark is not processed yet", record.getSyncariId());
                            toRemove.add(record);
                            continue;
                        }
                        entityRepoService.save(entityDefintion, syncariRecord.get().setDeleted(false));
                    }

                    var entityDataMap = new HashMap<String, Object>();
                    entityDataMap.put(record.getEntityData().getName(), record.getEntityData().getValues());
                    addConnectorContext(connector.getName(), entityDataMap, executionContext);
                    //TODO: get rid of this after fully moving to new tokenresolver
                    entityDataMap.put(PipelineHelper.toApiName(record.getEntityData().getName()), record.getEntityData().getValues());
                    addConnectorContext(PipelineHelper.toApiName(connector.getName()), entityDataMap, executionContext);

                    executionContext.setStagedBatchRecord(record);

                    try {
                        runSourceActions(entityGraph, executionContext);
                        evaluator.evaluate(coreNode, entityGraph, executionContext, node -> node.getType() == MappingNodeType.ENTITY_SOURCE, new HashSet<String>());

                        executionContext.getErrors().forEach((syncariId, errors) -> {
                            errors.forEach(error -> graphContext.addError(syncariId, error));
                        });
                        //Find non-failed results
                        var results = entityGraph.getInboundEdges(coreNode).stream().
                                flatMap(edge -> Optional.ofNullable((Pair<FunctionResult, MappingNode>) executionContext.get("output_" + edge.getSourceStage().getId())).stream())
                                .filter(result -> !FilterFailedResult.isFailedFilter(result.x.typedValue()))
                                .collect(Collectors.toList());

                        results.forEach(result -> {
                            //pipelines have changed syncariId. Save it on the stagedBatchRecord
                            if (!record.getSyncariId().equals(record.getEntityData().getSyncariEntityId())) {
                                         /*
                                            Since syncariId got changed for each record due to either attachRecord or any other formation
                                            We have to ensure we fix testContext which means we have to remove all older syncariId references
                                            from dataSnapshot present in testContext because these older syncariId are dangling references
                                            and will not be considered for any iteration because older syncariId doesn't exist in stagedBatchRecord.
                                         */
                                        var isSyncariIdInTestContext = graphContext.getTestContext().getDataSnapshot().containsKey(record.getSyncariId());
                                        //process only those record which exist in testContext
                                        if(isSyncariIdInTestContext){
                                            Map<String, NodeData> combinedMap = new HashMap<>(graphContext.getTestContext().getDataSnapshot().get(record.getSyncariId()));
                                            for(Map.Entry<String, Map<String, NodeData>> data : graphContext.getTestContext().getDataSnapshot().entrySet()){
                                                if(!data.getKey().equals(record.getSyncariId())){
                                                    graphContext.getTestContext().getDataSnapshot().get(data.getKey()).entrySet()
                                                            .forEach(entry -> {
                                                                combinedMap.put(entry.getKey() , entry.getValue());
                                                            });
                                                    graphContext.getTestContext().getDataSnapshot().put(data.getKey() , combinedMap);
                                                    graphContext.getTestContext().getDataSnapshot().remove(record.getSyncariId());
                                                }
                                            }
                                        }
                                        record.setSyncariId(record.getEntityData().getSyncariEntityId());
                                    }
                                    toUpdate.add(record);
                                });
                                //No results found - basically amounts to failedFilters, or an action/function has explicitly marked it to be removed
                                if(results.isEmpty() || record.isDeleted()){
                                    log.debug("Marking record as deleted {}",record);
                                    toRemove.add(record);
                                } else {
                                    graphContext.addCoreEntityNodeInput(record.getSyncariId(), results.get(0));
                                }
                            } catch (TerminateExecutionPathException e) {
                                log.warn("Skipping record because execution path was terminated due to a filter for stagedBatchRecord {}", record);
                                toRemove.add(record);
                            }
                            if(processConnectedRecords) {
                                executionContext.getAllConnectedRecords().forEach((externalEntityDefinitionId, newRecords) ->
                                        newRecords.forEach(r -> graphContext.addConnectedRecord(externalEntityDefinitionId, r))
                                );
                            }

                            executionContext.clear();
                        }
                            if(processConnectedRecords) {
                                Timer connectedRecordsProcessing = new Timer("ExecuteEntityPipeline::execute::processConnectedRecords");
                                graphContext.getAllConnectedRecords().forEach((externalEntityDefinition, newRecords) -> {
                                            Connector c = connectorMap.containsKey(externalEntityDefinition.getConnectorId()) ? connectorMap.get(externalEntityDefinition.getConnectorId()) :
                                                      connectorService.get(externalEntityDefinition.getConnectorId());
                                            connectorMap.put(c.getId(), c);
                                            EntitySchema entitySchemaWithMappedField = graphContext.cache(externalEntityDefinition.getId()+ "_SchemaWithMappedField" ,
                                                    () -> transformer.toEntitySchema(schemaService.getSourceEntityWithMappedAndSystemFields(coreEntity, externalEntityDefinition, entityGraph), connector));
                                            SyncRequest syncRequest = new SyncRequest().setConnector(transformer.toConnectorInfo(c))
                                                    .setEntitySchema(transformer.toEntitySchema(externalEntityDefinition, c))
                                                    .setEntitySchemaWithMappedFields(entitySchemaWithMappedField)
                                                    .setData(Map.of(c.getId(), newRecords));
                                            Map<String, String> externalIdToSyncariId = new HashMap<>();
                                            newRecords.forEach(newRecord -> externalIdToSyncariId.put(newRecord.getId(), newRecord.getSyncariEntityId()));
                                            List<EntityData> byIds = dataServiceFactory.getDataService(c.getMetadata()).getByIds(syncRequest);
                                            //set source and syncariId
                                            byIds.forEach(r -> r.addValue("_source", c.getName()).setSyncariEntityId(externalIdToSyncariId.get(r.getId())));
                                            batch.addNewRecords(externalEntityDefinition, byIds);
                                        }
                                );
                                graphContext.clearConnectedRecords();
                                connectedRecordsProcessing.close();
                            }
                        if (toRemove.size() > 0) {
                            log.info("Marking stagedBatchRecords as deleted for stagedBatchRecordIds {} ", 
                                toRemove.stream().map(x -> x.getId()).collect(Collectors.toList()));
                        }
                        toRemove.forEach(r->r.setDeleted(true));
                        batch.update(toRemove);
                        updateUnresolvedReferences(toRemove);
                        batch.update(toUpdate);
                        log.info("ExecuteEntityPipeline, {} marked deleted, {} updated in Batch {}, external entity {} connector {}",toRemove.size(),toUpdate.size(),batch.getCurrentBatchId(), entityDefintion.getApiName(), connector.getName());
                        int totalProcessed = toRemove.size() + toUpdate.size();
                        Instant lastUpdatedRecordTime = CollectionUtils.isNotEmpty(updatedRecords) ? Instant.ofEpochMilli(updatedRecords.get(updatedRecords.size()-1).getEntityData().getLastModified()) : Instant.now();
                        EntitySyncStatusMetric syncStatusMetricL = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityDefintion.getApiName(), lastUpdatedRecordTime,
                                (float)timer.getTimeTakenUntilNow(), totalProcessed,toUpdate.size(), toRemove.size(),0);
                        long totalDurationtillNowL = Instant.now().toEpochMilli() - context.getSyncStartTime();
                        syncDetailMetricService.updateEPSyncDetailMetric(coreEntity.getId(), syncStatusMetricL, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_ENTITY_PIPELINE, batch.getCurrentBatchId(), (float)totalDurationtillNowL);
                        removeCount.addAndGet(toRemove.size());
                        updateCount.addAndGet(toUpdate.size());
                        toRemove.clear();
                        toUpdate.clear();
                    }
            );
            finalizeBatchActions(entityGraph,graphContext);
            totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
            syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityDefintion.getApiName(), null,
                    (float)timer.getTimeTakenUntilNow(),0, 0,0);
            updateSyncDetailMetricDuration(syncStatusMetric, coreEntity.getId(),batch.getCurrentBatchId(),totalDurationtillNow);

            timer.close();
        });
        log.info("StageCompleted: ExecuteEntityPipeline, {} removed and {} updated in Batch {}, entity {} ",removeCount.get(),updateCount.get(),batch.getCurrentBatchId(), batch.getSyncariEntityName());
        return graphContext;
    }

    private void addConnectorContext(String connectorName, Map<String, Object> entityDataMap, GraphContext executionContext) {
        if (connectorName.equals("syncari")) {
            if (executionContext.containsKey("syncari") && executionContext.get("syncari") instanceof Map) {
                ((Map<String, Object>) executionContext.get("syncari")).putAll(entityDataMap);
                return;
            }
        }
        executionContext.put(connectorName, entityDataMap);
    }

    private boolean checkRecordDestinationProcessed(EntityDefinition syncariEntity, MappingGraph entityGraph, StagedBatchRecord record, EntityData existingRecord, GraphContext graphContext) {
        return entityGraph.getConnectedSinks().filter(node -> {
            EntitySinkNodeConfig sinkNodeConfig = node.getTypedConfiguration();
            var sinkEntity = sinkNodeConfig.getEntityDefinition();
            if (!record.getExternalEntityDefinitionId().equals(sinkEntity.getId()) || record.isNew()) {
                return false;
            }
           Optional<SyncDetail> watermark = (Optional<SyncDetail>) graphContext.computeIfAbsent(String.format("%s:%s", syncariEntity.getApiName(), sinkNodeConfig.getEntityDefinition().getId()),
                    (key) -> watermarkService.getDownstreamWatermark(syncariEntity.getApiName(), sinkNodeConfig.getEntityDefinition()));
            return watermark.isPresent() && watermark.get().getWatermark().getEnd()  < existingRecord.getSyncariTimestamp();
        }).findFirst().isPresent();
    }

    private void updateSyncDetailMetricDuration(EntitySyncStatusMetric syncStatusMetric, String coreEntitId, String currentBatchId, long totalDuration){
        syncDetailMetricService.updateEPSyncDetailMetric(coreEntitId, syncStatusMetric, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_ENTITY_PIPELINE, currentBatchId, (float)totalDuration);

    }

    private void updateUnresolvedReferences(List<StagedBatchRecord> toRemove) {
        List<UnresolvedReference> unresolvedReferences = toRemove.stream().map(stagedBatchRecord -> {
            UnresolvedReference unresolvedReference = new UnresolvedReference();
            unresolvedReference.setExternalRefEntityName(stagedBatchRecord.getEntityData().getName());
            unresolvedReference.setExternalRefRecordId(stagedBatchRecord.getExternalRecordId());
            unresolvedReference.setConnectorId(stagedBatchRecord.getEntityData().getConnectorId());
            return unresolvedReference;
        }).collect(Collectors.toList());
        unresolvedReferenceRepo.markUnresolvable(unresolvedReferences);
    }

    private void finalizeBatchActions(MappingGraph graph, GraphContext context) {
        List<MappingNode> batchActionNodes = context.getBatchActionContext().getTopoSortedBatchActionNodes(graph);
        context.getBatchActionContext().enableRunActions();
        batchActionNodes.forEach(actionNode->{
            try {
                //Stop execution as soon as the action is executed
                evaluator.evaluate(actionNode, graph, context, n -> n!=actionNode, new HashSet<String>());
            } catch (TerminateExecutionPathException e) {
                log.warn("Batch Action Execution terminated due to errors " + actionNode.getName() + " in graph "+graph.getName(), e);
            }
        });
        context.getBatchActionContext().clearBatchContext();
    }

    private void runSourceActions(MappingGraph graph, GraphContext context) {
        Stream<MappingNode> actionNodes = getSourceConnectedActions(graph);
        actionNodes.forEach(actionNode -> {
            try {
                evaluator.evaluate(actionNode, graph, context, n -> ACTION_TERMINALS.contains(n.getType()), new HashSet<String>());
            }catch (TerminateExecutionPathException e){
                log.warn("Action Execution terminated due to errors", e);
            }
        });
    }

    private Stream<MappingNode> getSourceConnectedActions(MappingGraph graph) {
        return graph.getActions().filter(action -> graph.getOutboundEdges(action).isEmpty() &&
                graph.pathToNodeMatches(action, n->ACTION_TERMINALS.contains(n.getType()))
                //exclude actions connected to core
                && !graph.pathToNodeMatches(action, n->OTHER_TERMINALS.contains(n.getType()))
        );
    }

    protected List<StagedBatchRecord> attachSyncariIds(CurrentBatch entityBatch, EntityDefinition entityDefinition, Connector connector, List<StagedBatchRecord> records) {
        log.info("Attaching syncariIds in batch {} , entity {} for connector {}, total records {}",entityBatch.getCurrentBatchId(),entityDefinition.getApiName(),connector.getName(),records.size());
        var externalIds = records.stream().map(r -> r.getEntityData().getId()).collect(Collectors.toList());
        var mappings = idMappingRepo.findByExternalIds(entityBatch.getSyncariEntityName(), entityDefinition.getConnectorId(), entityDefinition.getId(), externalIds);

        var idMappings = new HashMap<String, String>();
        mappings.forEach(m -> {
            //we need to look at all mappings, and not just connected ones
            m.getAllMappings(entityDefinition.getConnectorId(), entityDefinition.getId()).forEach(v -> {
                idMappings.put(v.getEntityId(), m.getSyncariId());
            });
        });
        log.info("Found {} syncariIds in batch {} , entity {} for connector {}, total records {}",mappings.size(),entityBatch.getCurrentBatchId(),entityDefinition.getApiName(),connector.getName(),records.size());

        Map<String, AttributeDefinition> apiNameToAttrMap = entityDefinition.getApiNameLowerCasedToAttributes();
        records.forEach(entity -> {
            String recordedSyncariId = entityBatch.getSyncariId(entity.getEntityData().getId(), entity.getExternalEntityDefinitionId());

            String syncariId = idMappings.get(entity.getEntityData().getId());
            boolean isNew = syncariId == null;
            entity.getEntityData().setSyncariEntityId(isNew ? (recordedSyncariId==null ? generateId() : recordedSyncariId) : syncariId);
            entity.setEntityData(helper.fixDatatypes(apiNameToAttrMap, entity.getEntityData()));
            entity.setSyncariId(entity.getEntityData().getSyncariEntityId());
            entity.setNew(isNew);
            entityBatch.setSyncariId(entity.getEntityData().getId(),entity.getExternalEntityDefinitionId(), entity.getSyncariId());
            entity.getEntityData().addValue("_source",connector.getName());
        });
        return entityBatch.update(records);
    }

    private String generateId() {
        return new ObjectId().toHexString();
    }

}

