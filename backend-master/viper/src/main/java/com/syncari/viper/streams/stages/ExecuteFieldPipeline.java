package com.syncari.viper.streams.stages;

import com.google.common.collect.Lists;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.SyncResponse;
import com.syncari.core.dfiv2.DFIConstants;
import com.syncari.core.dfiv2.DFIResultManager;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.datatype.ChildType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.EntitySyncStatusMetric;
import com.syncari.core.model.misc.EntitySynchStatusMetricSummary;
import com.syncari.core.model.misc.ExternalValue;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.pipeline.*;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.core.service.*;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;
import com.syncari.utils.I18n;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import com.syncari.utils.Timers;
import com.syncari.viper.DFIRuleExecutor;
import com.syncari.viper.ViperContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class ExecuteFieldPipeline {

    @Autowired
    DFIRuleExecutor dfiRuleExecutor;

    @Autowired
    DFIExecutorService dfiExecutorService;

    @Autowired
    DataQualityService dataQualityService;

    private static final String SYNCARI_ID = "syncariId";
    private static final int UNRESOLVED_REFERENCE_BATCH_SIZE = 500;
    private static final int FLUSH_SIZE = 500;
    private static final int SYNC_METRIC_BATCH_SIZE = 500;
    private static final String AUTHORITY_SOURCE_ATTRIBUTE = "authority_source_attribute";
    private static final Set<MappingNodeType> ACTION_TERMINALS = Set.of(
            MappingNodeType.CORE_ATTRIBUTE,
            MappingNodeType.ENTITY_SOURCE,
            MappingNodeType.ATTRIBUTE_SOURCE,
            MappingNodeType.CORE_ENTITY);

    protected ConnectorService connectorService;
    protected MappingGraphService graphService;
    protected PipelineEvaluator evaluator;
    protected EntityRepo entityRepo;
    protected SchemaService schemaService;
    protected AttributeRepo attributeProxyRepo;
    protected TransactionLogService transactionLogService;
    protected RecordMergeService recordMergeService;
    protected IdMappingRepo idMappingRepo;
    protected UnresolvedReferenceRepo unresolvedReferenceRepo;
    protected EventStore eventStore;
    protected EntityRepoService repoService;
    protected DatastoreService datastoreService;
    protected RequeueService requeueService;
    protected SyncDetailMetricService syncDetailMetricService;
    protected FeatureService featureService;
    protected NotificationService notificationService;
    protected  PipelineUtil pipelineUtil;

    @Autowired
    public ExecuteFieldPipeline(ConnectorService connectorService, EntityRepo entityRepo,
                                MappingGraphService mappingGraphService, PipelineEvaluator pipelineEvaluator,
                                SchemaService schemaService, AttributeRepo attributeProxyRepo, EventStore eventStore,
                                RecordMergeService recordMergeService,
                                IdMappingRepo idMappingRepo, UnresolvedReferenceRepo unresolvedReferenceRepo,
                                DatastoreService datastoreService, EntityRepoService entityRepoService, RequeueService requeueService, TransactionLogService transactionLogService,
                                SyncDetailMetricService syncDetailMetricService, FeatureService featureService, PipelineUtil pipelineUtil,NotificationService notificationService) {
        this.connectorService = connectorService;
        this.graphService = mappingGraphService;
        this.evaluator = pipelineEvaluator;
        this.entityRepo = entityRepo;
        this.schemaService = schemaService;
        this.attributeProxyRepo = attributeProxyRepo;
        this.recordMergeService = recordMergeService;
        this.idMappingRepo = idMappingRepo;
        this.unresolvedReferenceRepo = unresolvedReferenceRepo;
        this.eventStore = eventStore;
        this.datastoreService = datastoreService;
        this.repoService = entityRepoService;
        this.requeueService = requeueService;
        this.transactionLogService = transactionLogService;
        this.syncDetailMetricService = syncDetailMetricService;
        this.featureService = featureService;
        this.notificationService = notificationService;
        this.pipelineUtil = pipelineUtil;
    }

    private void executeDfiRules(Map<String, AttributeDefinition> attrMap, Map<String, String> categoryIdtoNameMap,
                                 GraphContext graphContext, DFIResultManager dfiMgr,List<DataQualityRule> configuredRecordRules,Map<String, List<DataQualityRule>> attributeRulesMap) {
        String currRecordId = graphContext.getCurrentSyncariId();
        dfiMgr.addResults(dfiRuleExecutor.executeDFIRecordRules(currRecordId, graphContext, categoryIdtoNameMap,configuredRecordRules));
        for (String attrId : attrMap.keySet()) {
            AttributeDefinition attr = attrMap.get(attrId);
            List<DataQualityRule> attrRules = attributeRulesMap.getOrDefault(attrId, Collections.emptyList());
            dfiMgr.addResults(dfiRuleExecutor.executeDFIFieldRules(currRecordId, attr, graphContext, categoryIdtoNameMap, attrRules));
        }
    }

    public GraphContext execute(ViperContext context, GraphContext graphContext) {
        var batch = graphContext.getCurrentBatch();
        var entityGraph = graphContext.getGraph();

        String msg = String.format("Stage: ExecutePipelineAndSave for graph %s, batch %s", entityGraph.getName(),batch.getCurrentBatchId());
        Timer executeActionCheck = new Timer(300000, msg, log);
        log.info(msg);
        BatchedOperations batchedOperations = new BatchedOperations();
        BatchActionContext batchActionContext = new BatchActionContext();
        BatchActionContext attributeBatchActionContext = new BatchActionContext();
        var attributeGraphs = graphContext.isSimulationMode()
                ? graphContext.getTestContext().getAttributeGraphs()
                : graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId());

        graphContext.cache("attributeGraphs_" + entityGraph.getId(), attributeGraphs);

        Map<AttributeDefinition, MappingGraph> attributeDAGs = attributeGraphs.stream().collect(
                Collectors.toMap(g -> graphContext.cache("attributes_" + g.getTargetId(), () -> attributeProxyRepo.findById(g.getTargetId())).orElseThrow(), g -> g));


        var syncariEntityDef = schemaService.getEntity(entityGraph.getTargetId());
        var entityName = syncariEntityDef.getApiName();
        //TODO: Batch entities and save the batch
        Map<String, Connector> connectorMap = new HashMap<>();
        log.debug("Applying pipeline to {}", entityGraph.getName());
        Iterator<RecordsBySyncariId> recordsBySyncariIdIterator = batch.recordsBySyncariIdIterator();
        //TODO: Throwaway for enrich functions
        Stream<EntityDefinition> entityDefinitions = entityGraph.getConnectedSources().map(s -> schemaService.getEntity(s.getConfiguration().getConfigMap().get("entityDefinition").toString()));
        Map<String, List<AttributeDefinition>> entityToAttributes = new HashMap<>();
        StringBuilder allConnectorEntityNames = new StringBuilder();
        entityDefinitions.forEach(e ->{
                entityToAttributes.put(e.getId(), e.getAttributes());
                allConnectorEntityNames.append(e.getApiName());
        });

        List<TransactionLog> txBatch = new ArrayList<>();
        List<EntityData> entitiesBatch = new ArrayList<>();

        List<IdMapping> idMappingsBatch = new ArrayList<>();
        int newIdMappings=0;
        Set<String> syncCycleLoserIds = new HashSet<>();
        //TODO: Not very scalable.Need to make the entitysource more intelligent
        long recordsProcessed = 0l;
        long totalRecordUpdates = 0l;
        int updates = 0;int creates = 0;int merges = 0;int deletes = 0;
        Set<String> connectorNames = new HashSet<>();
        Set<String> connectorIds = new HashSet<>();
        List<RequeueRequest> requeuedRecordsToDelete = new ArrayList<>();
        var lastModifiedTS = 0l;Long timetakenForMetricNotBatch = executeActionCheck.getTimeTakenUntilNow();
        boolean isRferencedByOtherEntities = !schemaService.getReferringAttributes(syncariEntityDef).isEmpty();
        final Map<String, List<RuleAssignment>> dfiRuleMap = repoService.getRulesForEntityByField(syncariEntityDef.getApiName());
        Timer iteratorActionCheck = new Timer(300000, "Pipeline Execute iteratorActionCheck", log);

        Map<String, EntityDefinition> entityDefCache = new HashMap<>();
        Map<String, Connector> connectorCache = new HashMap<>();

        var resolvedIds = resolveFKs(attributeDAGs, graphContext);

        var optionalEntityId = graphContext.getGraph().getCoreNode().getEntityDefinitionId();
        Map<String, AttributeDefinition> attrMap = new HashMap<>();
        if (optionalEntityId.isPresent()) {
            attrMap = schemaService.getEntity(optionalEntityId.get()).getAttributes().stream().collect(Collectors.toMap(AttributeDefinition::getId, a -> a));
        } else {
            log.error("Enity not found for graph : "+graphContext.getGraph().getId());
        }
        boolean isDFIProvisioned = featureService.isEnabled(Features.DfiV2Provisioning);
        boolean isDFIEnabledForPipeline = graphContext.getGraph().getSettings() != null && graphContext.getGraph().getSettings().isDataQuality();
        boolean canExecuteDFI = optionalEntityId.isPresent() && isDFIEnabledForPipeline && isDFIProvisioned;
        Map<String, String> categoryIdtoNameMap = new HashMap<>();
        String entityId = optionalEntityId.orElse("");
        DFIResultManager dfiMgr = new DFIResultManager(entityId, StringUtils.isBlank(entityId) ? "" : schemaService.getEntity(entityId).getApiName());
        // Prefetch dfi rules
        List<DataQualityRule> configuredRecordRules = new ArrayList<>();
        Map<String, List<DataQualityRule>> configuredAttributeRules = new HashMap<>();


        if (canExecuteDFI){
            // Fetch all rules once
            List<DataQualityRule> allGraphRules = dataQualityService.getAllRules(graphContext.getGraph().getId());

            // Filter record rules in memory
            configuredRecordRules.addAll(dataQualityService.getRecordRules(allGraphRules));

            // Filter attribute rules in memory
            attrMap.keySet().forEach(attrId -> {
                List<DataQualityRule> rules = dataQualityService.getRulesByAttribute(attrId, allGraphRules);
                configuredAttributeRules.put(attrId, rules != null ? rules : Collections.emptyList());
            });
        }

        while (recordsBySyncariIdIterator.hasNext()) {
            long createdAt = 0l;
            timetakenForMetricNotBatch += iteratorActionCheck.getTimeTakenUntilNow();
            iteratorActionCheck.reset();
            RecordsBySyncariId records = recordsBySyncariIdIterator.next();
            //allows to refer to fields like zendesk.account.name
            var allLastModifiedTS = new ArrayList<Long>();
            var isDeleted = false;

            Map<String, String> stagedBatchRecordsSyncariIdById = new LinkedHashMap<>();
            for (StagedBatchRecord record : records.getRecords()) {
                stagedBatchRecordsSyncariIdById.put(record.getId(), record.getSyncariId());
            }
            log.debug("Processing StagedBatch with mapping stagedBatchRecordId::syncariId {} ", stagedBatchRecordsSyncariIdById);

            // If this record was already a loser record (merged into an earlier record), skip processing.
            if (syncCycleLoserIds.contains(records.getSyncariId())) {
                log.info("Found loser of a previous merge in the sync cycle, Skipped processing record {}.", stagedBatchRecordsSyncariIdById);
                graphContext.clearContext();
                iteratorActionCheck.close();
                recordsProcessed += records.getRecords().size();
                totalRecordUpdates += records.getRecords().size();
                continue;
            }

            Optional<IdMapping> existingIdMapping = records.getIdMapping();
            boolean idMappingAdjusted = false;
            RecordsBySyncariId copyOfRecordsById = records.copy();
            Map<String, IdMappingResult> mappingAdjusted = new HashMap<>();
            for (StagedBatchRecord record : records.getRecords()) {
                if (StringUtils.isBlank(record.getEntityData().getId())){
                    // look at logged staged back record external id would be null from Synapse
                    log.error("Skipping StagedBatch Record Id {} for Syncari Id {} because external record id is null, which should not be null from Synapse", record.getId(), records.getSyncariId());
                    copyOfRecordsById.getRecords().remove(record);
                    continue;
                }
                log.debug("Processing StagedBatch Record Id {} for Syncari Id {}", record.getId(), records.getSyncariId());
                createdAt = Math.max(createdAt, record.getEntityData().getCreatedAt());
                allLastModifiedTS.add(record.getEntityData().getLastModified());
                // source synapse for this record
                String synapseName = toApiName(record.getEntityData().getValue("_source").toString());
                var entityDataMap = new HashMap<String, Object>();
                entityDataMap.put(record.getEntityData().getName(), record.getEntityData().getValues());
                addConnectorContext(record.getEntityData().getValue("_source").toString(), entityDataMap, graphContext);
                //TODO: get rid of this after fully moving to new tokenresolver
                entityDataMap.put(toApiName(record.getEntityData().getName()), record.getEntityData().getValues());
                addConnectorContext(synapseName, entityDataMap, graphContext);

                List<AttributeDefinition> attributes = entityToAttributes.getOrDefault(record.getExternalEntityDefinitionId(), List.of());
                //Add attribute id vs value in graphContext, for functions to be able to look up values based on attribute ids
                attributes.forEach(attribute -> {
                    graphContext.put("field_" + attribute.getId(), record.getEntityData().getValue(attribute.getApiName()));
                });
                var connector = graphContext.cache(record.getEntityData().getConnectorId(), () -> connectorService.get(record.getEntityData().getConnectorId()));
                connector = connectorService.refreshAuthentication(connector);
                Optional<IdMappingResult> idMappingResult = adjustIdMapping(batch, syncariEntityDef, connector, existingIdMapping, record);
                idMappingAdjusted = idMappingAdjusted || idMappingResult.isPresent();
                if (idMappingResult.isPresent()) {
                    mappingAdjusted.put(idMappingResult.get().record.getSyncariId(), idMappingResult.get());
                }
                if (record.isRequeued()) {
                    requeuedRecordsToDelete.add(new RequeueRequest().setEntityDefinitionId(record.getExternalEntityDefinitionId()).setRecordId(record.getExternalRecordId())
                            .setGraphId(graphContext.getGraph().getId()));
                }
            }
            lastModifiedTS = allLastModifiedTS.stream().max(Long::compareTo).orElse(System.currentTimeMillis());
            //TODO:normalize common metadata fields - Id, createdAt, updatedAt, createdBy, updatedBy, system name, entity nam
            //
            graphContext.put(SYNCARI_ID, copyOfRecordsById.getSyncariId());
            graphContext.put("entities", copyOfRecordsById.getRecords());
            graphContext.loadSynapseConfigFromCache();
            
            isDeleted = isRecordDeleted(copyOfRecordsById.getRecords(), existingIdMapping);
            //also persist the new idmapping state
            final boolean requiresUpdate = idMappingAdjusted; //no mutables in lambdas :(
            existingIdMapping.ifPresent(idMapping-> {
                if(requiresUpdate) {
                    idMappingRepo.save(idMapping);
                }
            });

            var fieldGraphsParams = new FieldsGraphRequest()
                    .setEntityName(entityName)
                    .setRecords(copyOfRecordsById)
                    .setGraphContext(graphContext)
                    .setAttributeDAGs(attributeDAGs)
                    .setSyncariEntityDef(syncariEntityDef)
                    .setAttributeBatchActionContext(attributeBatchActionContext)
                    .setBatchedOperations(batchedOperations)
                    .setResolvedIds(resolvedIds)
                    .setEntityDefCache(entityDefCache)
                    .setConnectorCache(connectorCache);

            var changes = isDeleted ? createDeletedEntiy(syncariEntityDef, records, graphContext, lastModifiedTS) :
                    createSyncariEntityWithGraph(fieldGraphsParams);
            var transactionLog = changes.getTransactionLog();
            transactionLog.setErrors(graphContext.getErrors().getOrDefault(transactionLog.getSyncariId(), new ArrayList<>()));
            var entity = changes.getChanges();
            // copy back attachRecordData if any
            var attachRecordDataMaybe = records.getRecords().stream()
                    .filter(r -> !r.getEntityData().getAttachRecordData().isEmpty())
                    .map(r -> r.getEntityData().getAttachRecordData()).findAny();
            if(attachRecordDataMaybe.isPresent()){
                entity.addValue("attachRecordData", attachRecordDataMaybe.get());
            }
            entity.setDeleted(isDeleted);
            entity.setLastModified(lastModifiedTS);
            entity.setCreatedAt(createdAt);
            for (Map.Entry<String, IdMappingResult> entry : mappingAdjusted.entrySet()) {
                IdMappingResult result = entry.getValue();
                if(result.operation == IdMappingOperation.deleted || result.operation == IdMappingOperation.disconnected) {
                    repoService.disconnectExternalId(syncariEntityDef, entity, result.record.getExternalEntityDefinitionId(), Optional.of(transactionLog), Optional.of(result.record.getEntityData()));
                }
            }
            copyOfRecordsById.getRecords().forEach(stagedRecord -> {
                var connector = graphContext.cache(stagedRecord.getEntityData().getConnectorId(), () ->connectorService.get(stagedRecord.getEntityData().getConnectorId()));
                connector = connectorService.refreshAuthentication(connector);
                connectorMap.put(connector.getId(), connector);
                connectorNames.add(connector.getName());
                connectorIds.add(connector.getId());
                transactionLog.addSource(stagedRecord.getEntityData().getConnectorId(), connector.getName(), stagedRecord.getExternalEntityDefinitionId(),
                        stagedRecord.getEntityData().getId(), stagedRecord.getEntityData().getLastModified());
            });

            Timer mergeOperationCheck = new Timer(1000, "MergeOperationCheck", log);
            //Save Tlog  ONLY if there are changes present or merge can happen
            // But save entities regardless, because we need to see if the entity values in syncari are different from
            // the incoming values, downstream in SaveToSink.
            var mergeOpSkipWhen = getMergeOperation(entityGraph, syncariEntityDef, entity, graphContext, transactionLog, records.getExistingRecord(), entitiesBatch);
            if(mergeOpSkipWhen.isPresent() && mergeOpSkipWhen.get().hasSkippedRecords()) {
              addSkipWhenTransaction(txBatch, graphContext, batch, syncariEntityDef, mergeOpSkipWhen.get());
            }
            Optional<MergeOperation> mergeOperation =
                mergeOpSkipWhen.isPresent() && !mergeOpSkipWhen.get().isSkipOnly() ? mergeOpSkipWhen
                    : Optional.empty();
            if (!transactionLog.getChanges().isEmpty() || mergeOperation.map(m->m.hasLosers()).orElse(false) || !transactionLog.getErrors().isEmpty()) {
                //Generate Id Mapping for new entities. Has to happen before merge
                newIdMappings+=  upsertIdMappings(batch, entityName, copyOfRecordsById, idMappingsBatch, mergeOperation, entity, syncariEntityDef, Optional.of(transactionLog));
                log.debug("newIdMappings: {} ", newIdMappings);
                //Update unresolved references first. This way, if there is a merge and the current record loses out
                //merge will take care of fixing loser references correctly
                if(isRferencedByOtherEntities) {
                    updateResolvedReferences(batch, records);
                }
                //Dedupe
                var entityAndMergeTransaction = applyMerge(batch, syncariEntityDef, entity,mergeOperation, entitiesBatch, graphContext);
                log.debug("done ApplyMerge: {} ", entityAndMergeTransaction);
                entity = entityAndMergeTransaction.x;
                entityAndMergeTransaction.y.ifPresent(mergeTransaction -> txBatch.add(mergeTransaction));
                
                //Add all fields of entity to context, instead of just updates
                //This is needed for token resolutions
                //Run actions only if there are changes
                graphContext.setBatchActionContext(batchActionContext);

                //Merge changes transaction semantics. The txn is relevant only if it belongs to the
                //merge winner. Discard otherwise
                var savedTransaction = saveTransactionLog(batch, transactionLog, entity);
                log.debug("Done savedTransaction {} ", savedTransaction);
                savedTransaction.ifPresent(saved -> txBatch.add(saved));

                repoService.computeScore(List.of(entity), syncariEntityDef.getApiName(),dfiRuleMap);
                log.debug("Done computeScore {} ");
                entitiesBatch.add(entity);
                log.debug("Added entity to in memory batch {}  values {}", entity.getName(), entity);

                if(!featureService.isEnabled(Features.SinksideActions, true)) {
                    // Check if any action is updateSyncariRecords and flush before running
                    if (shouldFlushBeforeActions(entityGraph, syncariEntityDef)) {
                        flushEntityBatch(syncariEntityDef, entitiesBatch, idMappingsBatch, txBatch);
                    }
                    runActions(entityGraph, entity,syncariEntityDef, graphContext);
                }
//                graphContext.clearContext();


                // Keep track of loser syncariIds in this sync cycle to ignore them from being applied.
                mergeOperation.ifPresent(m->syncCycleLoserIds.addAll(mergeOperation.get().getLoserIds()));

//                if(entityBatch.size()>= FLUSH_SIZE) {
//                    entityRepo.saveAll(entityBatch);
//                    log.info("Saved {} entities.",entityBatch.size());
//                    entityBatch.clear();
//                }
            }else{
                //just save the entity to update timestamps. Needed to detect changes against incoming source values
                // An example -> Previous cycle updated syncari & salesforce contact first name to uppercase.
                //Someone changes the contact back to lower case in salesforce.
                //But in this cycle, the result of the pipeline is not different from what's in syncari (both are uppercase)
                //so transactionLog does not capture the change. But we still need to update salesforce with whats in
                //syncari. So the save below is a market for downstream SaveToSink to check if updates need to be sent
                //to salesforce (or any other synapse).
                //If this is a deleted entity OR we don't know about this entity yet, we can ignore it safely
                if(!(entity.isDeleted() || changes.getExisting().isEmpty())) {
                    // Even when there are no changes to the entity, we would want to update the resolved references in order to acknowledge that
                    // the references were processed.
                    updateResolvedReferences(batch,  copyOfRecordsById);
/*                    if (!featureService.isEnabled(Features.EntityBatching, true)) {
                        entity = entityRepo.save(syncariEntityDef,entity);
                    }*/
                    entitiesBatch.add(entity);
                    log.debug("Saved entity {}  values {}", entity.getName(), entity);
                }
                log.debug("Completed entityRepo.save for entity id/name {}/{} ", entity.getId(), entity.getName());
            }

            if(entitiesBatch.size()>= FLUSH_SIZE || txBatch.size() >= FLUSH_SIZE) {

                // if entity batch enabled, save entity batch and idMapping
                if (!entitiesBatch.isEmpty()) {
                    entityRepo.saveEntityBatch(syncariEntityDef, entitiesBatch, idMappingsBatch);
                }

                log.debug("Saving {} transaction logs for batch {}", txBatch.size(), batch.getCurrentBatchId());

                if (!txBatch.isEmpty()) {
                    var savedTransactions = transactionLogService.log(txBatch);
                    // TODO: remove this once the
                    repoService.updateLastTransactionId(syncariEntityDef, savedTransactions, entitiesBatch);
                    eventStore.insertTransactionLogs(txBatch);
                    txBatch.clear();
                }
                entitiesBatch.clear();
                idMappingsBatch.clear();
            }

            mergeOperationCheck.close();
            if (canExecuteDFI) {
                try {
                    GraphContext dfiContext = graphContext.copy();
                    dfiContext.setCurrentSyncariId(graphContext.getCurrentSyncariId());
                    for (StagedBatchRecord record : (List<StagedBatchRecord>)graphContext.get("entities")) {
                        List<AttributeDefinition> attributes = entityToAttributes.getOrDefault(record.getExternalEntityDefinitionId(), List.of());
                        //Add attribute id vs value in graphContext, for functions to be able to look up values based on attribute ids
                        attributes.forEach(attribute -> {
                            dfiContext.put("field_" + attribute.getId(), record.getEntityData().getValue(attribute.getApiName()));
                            log.debug("Field with id {} and value {}", "field_" + attribute.getId(), record.getEntityData().getValue(attribute.getApiName()));
                        });
                    }
                    Map<String, Object> entityValues = entity.getValues();
                    final String entityDefName = entity.getName();
                    Optional<EntityDefinition> entityDefinition = Optional.ofNullable(syncariEntityDef);
                    entityDefinition.ifPresentOrElse(entityDef -> {
                        Map<String, String> syncariAttribMap = new HashMap<>();
                        entityDef.getAttributes().forEach(a -> {
                            syncariAttribMap.put(a.getApiName(), a.getId());
                        });
                        entityValues.keySet().forEach(k -> {
                            String id = syncariAttribMap.get(k);
                            if (null != id){
                                dfiContext.put("field_" + id, entityValues.get(k));
                            }else{
                                log.info("Id is null for key {}", k);
                            }
                        });
                    },()-> {
                        log.info("Syncari Entity Def is null for name {}", entityDefName);
                    });
                    executeDfiRules(attrMap, categoryIdtoNameMap, dfiContext, dfiMgr, configuredRecordRules, configuredAttributeRules);
                } catch (Exception e) {
                    log.error("Error occurred while executing DFI results. Error : ", e);
                }
            }

            graphContext.clearContext();

            log.debug("Processed {} record {} in {}ms.", entity.getName(), entity.getId(), iteratorActionCheck.getTimeTakenUntilNow());

            recordsProcessed += copyOfRecordsById.getRecords().size();
            totalRecordUpdates += copyOfRecordsById.getRecords().size();
            if(requeuedRecordsToDelete.size() > 100){
                requeueService.cleanupProcessedRecords(requeuedRecordsToDelete);
                requeuedRecordsToDelete.clear();
            }

            if (isDeleted){
                deletes++;
            }else if (entity.isNew()){
                creates++;
            }else {
                updates++;
            }
            if (mergeOperation.isPresent() &&  (null != mergeOperation.get().getWinningRecord())){
                merges++;
            }
            timetakenForMetricNotBatch += iteratorActionCheck.getTimeTakenUntilNow();
            if (totalRecordUpdates >= SYNC_METRIC_BATCH_SIZE){
                Instant lastRecordModifiedTime = lastModifiedTS > 0 ? Instant.ofEpochMilli(lastModifiedTS) : Instant.now();
                EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(connectorIds.toString(),StringUtils.join(connectorNames,","),allConnectorEntityNames.toString(), lastRecordModifiedTime,(float)timetakenForMetricNotBatch,
                        (int)totalRecordUpdates, 0,0, deletes, creates, merges, updates, ChronoUnit.MILLIS);
                long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();

                syncDetailMetricService.updateSyncDetailMetric(syncariEntityDef.getId(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_FIELD_PIPELINE, batch.getCurrentBatchId(), (float)totalDurationtillNow);
                totalRecordUpdates = 0l;
                updates=0;deletes=0;creates=0;merges=0;timetakenForMetricNotBatch=0l;
            }
            iteratorActionCheck.reset();
        }
        if (canExecuteDFI) {
            try {
                dfiExecutorService.sendDFIResultNotification(dfiMgr);
            } catch (Exception e) {
                log.error("Error occurred while pushing DFI results to pub sub. Error : ", e);
            }
        }
        iteratorActionCheck.reset();
        if (!batchedOperations.getUnresolvedReferences().isEmpty()) {
            unresolvedReferenceRepo.upsertUnResolved(batchedOperations.getUnresolvedReferences());
            batchedOperations.getUnresolvedReferences().clear();
        }

        if(!requeuedRecordsToDelete.isEmpty()){
            requeueService.cleanupProcessedRecords(requeuedRecordsToDelete);
        }
        log.debug("{} stage:GenerateIdMapping, component:viper ,newRecords:{} and iterationTimer is {}", EventTypes.PIPELINE_RUNTIME, newIdMappings, timetakenForMetricNotBatch);
        if(!entitiesBatch.isEmpty() || !txBatch.isEmpty()) {

            if (!entitiesBatch.isEmpty()) {
                log.debug("Saving entities batch for {}", syncariEntityDef);
                entityRepo.saveEntityBatch(syncariEntityDef, entitiesBatch, idMappingsBatch);
            }
            if (!txBatch.isEmpty()) {
                log.debug("Saving {} transaction logs for batch {}", txBatch.size(), batch.getCurrentBatchId());
                var savedTransactions = transactionLogService.log(txBatch);
                repoService.updateLastTransactionId(syncariEntityDef, savedTransactions, entitiesBatch);
                eventStore.insertTransactionLogs(txBatch);
                txBatch.clear();
            }
            entitiesBatch.clear();
            idMappingsBatch.clear();
        }
        //handle unresolved refs AFTER the pipelines are run. This is to avoid unintentional FK overrides
        //By doing this, we don't inadvertantly overwrite unresolved via pipeline
        log.debug("Handle unresolved FKs");
        handleExistingUnresolvedFk(batch, syncariEntityDef);
        log.debug("Run attribute batch actions");
        runAttributeBatchActions(attributeDAGs, graphContext, attributeBatchActionContext);
        log.debug("Run batch actions");
        runBatchActions(entityGraph, graphContext, batchActionContext);
        syncDetailMetricService.updateSyncErrorMetric(syncariEntityDef.getId(), batch.getCurrentBatchId(),
                pipelineUtil.getEntitySyncErrorMetrics(graphContext.getErrors(), graphContext.getNodeStatusMetrics()).collect(Collectors.toList()));

        log.info("StageCompleted: ExecutePipelineAndSave for graph {}, batch {}, records {} in {} ms.",entityGraph.getName(),
            batch.getCurrentBatchId(), recordsProcessed, executeActionCheck.getTimeTakenUntilNow());
        timetakenForMetricNotBatch += iteratorActionCheck.getTimeTakenUntilNow();
        if (totalRecordUpdates <= SYNC_METRIC_BATCH_SIZE){
            Instant lastRecordModifiedTime = lastModifiedTS > 0 ? Instant.ofEpochMilli(lastModifiedTS) : Instant.now();
            EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(connectorIds.toString(),StringUtils.join(connectorNames,", "),allConnectorEntityNames.toString(), lastRecordModifiedTime,(float)timetakenForMetricNotBatch,
                    (int)totalRecordUpdates, 0,0, deletes, creates, merges, updates, ChronoUnit.MILLIS);
            long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
            syncDetailMetricService.updateSyncDetailMetric(syncariEntityDef.getId(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_FIELD_PIPELINE, batch.getCurrentBatchId(), (float)totalDurationtillNow);
        }
        iteratorActionCheck.close();
        if (featureService.isEnabled(Features.Datastore)){
            Timer datastoreDumpTimer = new Timer(300000, msg, log);
            // Pump data to datastore only if active datastore matches the one in graphContext
            Optional<Connector> activeDatastore = datastoreService.findActiveDatastore();
            int recordsDumped=0;
            long dbLastModifiedRecordTime = lastModifiedTS;
            if(activeDatastore.isPresent()) {
                if (graphContext.getDatastore().isPresent() && activeDatastore.equals(graphContext.getDatastore())) {
                    List<SyncResponse> dumpResponse = datastoreService.execute(syncariEntityDef, recordsProcessed);
                    if (CollectionUtils.isNotEmpty(dumpResponse)){
                        for (SyncResponse d : dumpResponse){
                            if (CollectionUtils.isNotEmpty(d.getResults())){
                                recordsDumped += d.getResults().size();
                            }
                        }
                        Optional<DatastoreWatermark> datastoreWatermark = this.datastoreService.getWatermarkService().getDatastoreWatermark(syncariEntityDef.getId());
                        dbLastModifiedRecordTime = datastoreWatermark.get().getWatermark().getStart();
                    }
                } else {
                    log.warn("Active Datastore does not match with datastore set for the sync cycle. Skipping Datastore sync");
                    log.warn("Active Datastore: {}. Datastore set in sync cycle: {}",
                            activeDatastore.get().getId(), graphContext.getDatastore().orElse(new Connector()).getId());
                }
                Instant lastRecordModifiedTime = dbLastModifiedRecordTime > 0 ? Instant.ofEpochMilli(dbLastModifiedRecordTime) : Instant.now();
                EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(connectorIds.toString(), StringUtils.join(connectorNames, ", "), allConnectorEntityNames.toString(), lastRecordModifiedTime, (float) datastoreDumpTimer.getTimeTakenUntilNow(),
                        recordsDumped, 0, 0, deletes, creates, merges, updates, ChronoUnit.MILLIS);
                long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
                syncDetailMetricService.updateSyncDetailMetric(syncariEntityDef.getId(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.PROCESSING_DATASTORE_WRITES, batch.getCurrentBatchId(), (float) totalDurationtillNow);
                datastoreDumpTimer.close();
            } else {
                log.info("No Active Datastore Found. Skipping datastore sync");
            }
        }
        executeActionCheck.close();
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

    private void addSkipWhenTransaction(List<TransactionLog> txBatch, GraphContext graphContext, CurrentBatch batch, EntityDefinition syncariEntityDef, MergeOperation skipWhenOperation) {
      String syncariId = skipWhenOperation.getRecords().get(0).getSyncariEntityId();
      if (txBatch.stream().filter(tx -> syncariId.equals(tx.getSyncariId()) && tx.getOperation()==Operation.merge_skip).findAny().isPresent()) {
        log.debug("{} already present in skip when transaction, hence skipping", syncariId);
        return;
      }
      Connector  connector = graphContext.cache( "syncariConnector", ()->connectorService.getSyncariConnector());
      var tx = new TransactionLog()
      .setBatchId(batch.getCurrentBatchId())
      .setEntityName(syncariEntityDef.getApiName())
      .setEntityId(syncariEntityDef.getId())
      .setAdditionalInfo(Map.of("mergeSkipDetails", skipWhenOperation))
      .setOperation(Operation.merge_skip)
      .setSyncariId(syncariId);
      tx.setId(ObjectId.get().toHexString());
      tx.setCreatedAt(new Date());
      tx.setCreatedBy(Optional.ofNullable(SyncariContext.getUser()).map(u->u.getId()).orElse(null));
      tx.addSource(connector.getId(), connector.getName(), syncariEntityDef.getId(), syncariId, System.currentTimeMillis());
      txBatch.add(tx);
    }


    private HashMap<String, Map<String, String>> resolveFKs(Map<AttributeDefinition, MappingGraph> attributeDAGs, GraphContext graphContext) {
        String msg = "ExecutePipelineAndSave - resolveFKs";
        Timer resolveFKsCheck = new Timer(30000, msg, log);
        Map<String, String> referenceToMap = attributeDAGs.keySet().stream().filter(AttributeDefinition::isReference).collect(Collectors.toMap(AttributeDefinition::getApiName, AttributeDefinition::getReferenceTo));

        var batch = graphContext.getCurrentBatch();
        var recordIterator = batch.recordsBySyncariIdIterator();
        var recordMap = new HashMap<String, Set<String>>();
        var entityDefCache = new HashMap<String, EntityDefinition>();
        while (recordIterator.hasNext() && !referenceToMap.isEmpty()) {
            var records = recordIterator.next();
            records.getRecords().forEach(record -> {
                var values = record.getEntityData().getValues();
                for(Map.Entry entry: values.entrySet()){
                    if(referenceToMap.containsKey(entry.getKey())) {
                        String referenceTo = referenceToMap.get(entry.getKey());
                        var externalEntityDef = findExternalEntityById(entityDefCache, record.getExternalEntityDefinitionId());
                        String connectorId = externalEntityDef.getConnectorId();
                        Object value = entry.getValue();
                        String key = referenceTo + "#" + connectorId;
                        if(recordMap.containsKey(key)) {
                            if(value instanceof String) {
                                recordMap.get(key).add((String) value);
                            } else if(value instanceof List) {
                                List<String> list = (List<String>) value;
                                recordMap.get(key).addAll(list);
                            }
                        }
                        else {
                            if(value instanceof String) {
                                recordMap.put(key, new HashSet<>(List.of((String) value)));
                            } else if(value instanceof List) {
                                List<String> list = (List<String>) value;
                                recordMap.put(key, new HashSet<>(list));
                            }
                        }
                    }
                }
            });
        }

        var resolvedIds = new HashMap<String, Map<String, String>>();
        for(String entityAndConnector: recordMap.keySet()) {
            String[] parts = entityAndConnector.split("#");
            String referenceTo = parts[0];
            String connectorId = parts[1];
            var ids = recordMap.get(entityAndConnector);
            var idMappings = idMappingRepo.findByExternalIds(referenceTo, connectorId, ids);
            idMappings.forEach(idMapping -> {
                var mappings = idMapping.getMappings();
                for(IdMapping.Mapping mapping: mappings) {
                    if(ids.contains(mapping.getEntityId())) {
                        if(resolvedIds.containsKey(entityAndConnector)) {
                            resolvedIds.get(entityAndConnector).put(mapping.getEntityId(), idMapping.getSyncariId());
                        }
                        else {
                            resolvedIds.put(entityAndConnector, new HashMap<>(Map.of(mapping.getEntityId(), idMapping.getSyncariId())));
                        }
                    }
                }
            });
        }
        log.debug("Completed resolving {} foreign keys in batch {}", resolvedIds.size(), batch.getCurrentBatchId());
        resolveFKsCheck.close();
        return resolvedIds;
    }

    protected boolean isRecordDeleted(List<StagedBatchRecord> records, Optional<IdMapping> existingIdMapping) {
        final boolean allIncomingRecordsDeleted = records.stream().allMatch(r -> r.getEntityData().isDeleted());
        //if all idmappings are marked disconnected, we can mark the entire record as deleted
        final boolean isDeleted = existingIdMapping.map(r->!r.hasConnectedMappings()).orElse(allIncomingRecordsDeleted);
        if (allIncomingRecordsDeleted || existingIdMapping.map(r->!r.hasConnectedMappings()).isPresent()) {
            log.debug("allIncomingRecordsDeleted {}, Record is marked as isDeleted: {} ", allIncomingRecordsDeleted, isDeleted);
        }
        return isDeleted;
    }

    private Optional<IdMappingResult> adjustIdMapping(CurrentBatch batch, EntityDefinition syncariEntityDef, Connector connector, Optional<IdMapping> existing,  StagedBatchRecord record) {
        if(record.getEntityData().isDeleted()){
            //delete unresolved refs waiting for this external record
            unresolvedReferenceRepo.deleteUnResolvedReferencesBy(record.getEntityData().getConnectorId(), record.getEntityData().getName(),List.of(record.getId()));
            //and disconnect the record from syncari record.This marks the mapping as deleted
            if(existing.isPresent()) {
                Optional<IdMapping.Mapping> mapping = existing.get().findMapping(record.getEntityData().getConnectorId(), record.getExternalEntityDefinitionId(), record.getExternalRecordId());
                if(mapping.isPresent() && !mapping.get().isDisconnected()) {
                    existing.get().disconnectMapping(record.getEntityData().getConnectorId(), record.getExternalEntityDefinitionId(), record.getExternalRecordId());
                    logMappingDisconnectTxn(syncariEntityDef, connector, record.getExternalEntityDefinitionId(), List.of(record.getEntityData()),Operation.disconnect,Map.of(), batch.getCurrentBatchId());
                    return Optional.of(new IdMappingResult(batch, syncariEntityDef, connector, existing, record, IdMappingOperation.deleted));
                }
            }

        }else if(isDisconnected(existing, record)) {
            //reconnect record, if it was resurrected in the end system and we had a record before of disconnect
            existing.ifPresent(e->e.reconnectMapping(record.getEntityData().getConnectorId(), record.getExternalEntityDefinitionId(), record.getExternalRecordId()));
            return Optional.of(new IdMappingResult(batch, syncariEntityDef, connector, existing, record, IdMappingOperation.connect));
        }else{
           //no changes to id mapping
            return Optional.empty();
        }
        return Optional.empty();
    }

    private void logMappingDisconnectTxn(EntityDefinition def, Connector connector, String externalEntityDefinitionId, List<EntityData> data, Operation operation, Map<String, Object> additionalInfo, String batchId) {
        List<TransactionLog> txnLogs = new ArrayList<TransactionLog>();
        for (EntityData d : data) {
            TransactionLog txnLog = new TransactionLog();
            txnLog.setNew(false);
            txnLog.setBatchId(batchId);
            txnLog.setEntityName(def.getApiName());
            txnLog.setOperation(operation);
            txnLog.setAdditionalInfo(additionalInfo);
            txnLog.setSyncariId(d.getSyncariEntityId());
            txnLog.addSource(connector.getId(), connector.getName(), externalEntityDefinitionId, d.getId(), System.currentTimeMillis());
            txnLogs.add(txnLog);
            log.debug("Successfully logged txn for entity {} with id {}", d.getName(), d.getId());
        }
        transactionLogService.log(txnLogs);
    }


    protected void runAttributeBatchActions(Map<AttributeDefinition, MappingGraph> attributeGraphs, GraphContext context, BatchActionContext batchActionContext) {
        batchActionContext.enableRunActions();
        attributeGraphs.forEach((attribute, graph) -> {
            var actionNodes = batchActionContext.getTopoSortedBatchActionNodes(graph);
            actionNodes.forEach(actionNode -> {
                try {
                    final GraphContext subContext = context.createSubContext(graph);
                    subContext.setBatchActionContext(batchActionContext);
                    evaluator.evaluate(actionNode, graph, subContext, n -> n != actionNode, new HashSet<String>());
                } catch (TerminateExecutionPathException e) {
                    log.warn("Batch Action Execution terminated due to errors " + actionNode.getName() + " in graph " + graph.getName(), e);
                }
            });
        });
        context.setBatchActionContext(null);

    }

    private void runBatchActions(MappingGraph graph, GraphContext context, BatchActionContext batchActionContext) {
        batchActionContext.enableRunActions();
        context.setBatchActionContext(batchActionContext);
        List<MappingNode> actionNodes = batchActionContext.getTopoSortedBatchActionNodes(graph);
        actionNodes.forEach(actionNode -> {
            try {
                //Stop execution as soon as the action is executed
                evaluator.evaluate(actionNode, graph, context, n -> n!=actionNode, new HashSet<String>());
            } catch (TerminateExecutionPathException e) {
                log.warn("Batch Action Execution terminated due to errors " + actionNode.getName() + " in graph "+graph.getName(), e);
            }
        });
        context.setBatchActionContext(null);
    }
    @Deprecated
    protected void deleteIdMapping(List<StagedBatchRecord> records, String entityName) {
        //protected method. Guard conditions must also be inside the method
        if(records.isEmpty()) {
            return;
        }
        List<IdMapping> mappings = new ArrayList<>();
        var syncariIds =records.stream().map(r->r.getSyncariId()).collect(Collectors.toSet());
        //Single DB call, maps syncariId to IdMapping object
        Map<String, IdMapping> idMappings = idMappingRepo.findBySyncariIds(entityName, new ArrayList<>(syncariIds)).stream().collect(Collectors.toMap(i -> i.getSyncariId(),i->i));
        List<IdMapping> toDelete = new ArrayList<>();
        records.forEach(record -> {
            var idMapping = idMappings.get(record.getSyncariId());
            if(idMapping!=null){
                idMapping.removeMapping(record.getEntityData().getConnectorId(),
                        record.getExternalEntityDefinitionId(), record.getExternalRecordId()
                );
                if(idMapping.getMappings().isEmpty()){
                    toDelete.add(idMapping);
                }else {
                    mappings.add(idMapping);
                }
            }
        });
        //depending on the synapse, we may have multiple records with the same id, in the same batch
        List<IdMapping> nonEmptyMappings = mappings.stream().filter(r -> !r.getMappings().isEmpty()).collect(Collectors.toList());
        idMappingRepo.saveAll(nonEmptyMappings);
        if(!toDelete.isEmpty()){
            idMappingRepo.deleteAll(toDelete);
        }
    }

    protected int upsertIdMappings(CurrentBatch batch, String entityName, RecordsBySyncariId records, List<IdMapping> idMappingBatch,
                                   Optional<MergeOperation> mergeOperation, EntityData entity, EntityDefinition syncariDef,
                                   Optional<TransactionLog> txnLog) {
        int newIdMappings =0;
        final IdMapping idMapping = records.getIdMapping().orElseGet(()->new IdMapping()
                .setSyncariId(records.getSyncariId()).setEntityName(entityName));
        boolean needsUpdate = false;

        for (StagedBatchRecord record : records.getRecords()) {
            if (record.isNew()) {
                needsUpdate = true;
                String connectorId = batch.lookupConnectorIdByBatchId(record.getStagedBatchId()).getConnectorId();
                idMapping.addMapping(connectorId, record
                        .getEntityData().getId(), record.getExternalEntityDefinitionId());
                // for every new record set the external id values in Syncari
                newIdMappings++;
            }
            if (!record.isDeleted()) {
                repoService.connectExternalId(syncariDef, entity, record.getExternalEntityDefinitionId(), txnLog, record.getEntityData().getId());
            }
        }
        if(idMapping.hasConnectedMappings() && needsUpdate) {
                idMappingBatch.add(idMapping);
        }

        // save winner and loser id idMappings in the batch
        mergeOperation.ifPresent(mergOp -> {
            var losers = mergOp.getLoserIds();
            var mergeIdMappings = idMappingBatch.stream().filter(mapping -> mapping.getSyncariId().equals(mergOp.getWinningRecord().getId())
                    || losers.contains(mapping.getSyncariId())).collect(Collectors.toList());

            idMappingRepo.upsert(mergeIdMappings);
            idMappingBatch.removeIf(d -> mergeIdMappings.contains(d));
        });
        // update unresolved records for this batch with the syncari ids
        return newIdMappings;
    }

    private boolean isDisconnected(Optional<IdMapping> existing, StagedBatchRecord record) {
        return existing.stream().anyMatch(e->e.findDisconnected(
                        record.getEntityData().getConnectorId(), record.getExternalEntityDefinitionId(), record.getExternalRecordId()).isPresent());
    }

    private void updateResolvedReferences(CurrentBatch batch, RecordsBySyncariId records) {
        List<UnresolvedReference> lookupList = new ArrayList<>();
        for (StagedBatchRecord record : records.getRecords()) {
                String connectorId = batch.lookupConnectorIdByBatchId(record.getStagedBatchId()).getConnectorId();
                UnresolvedReference unresolvedReference = new UnresolvedReference(connectorId,
                        record.getEntityData().getName(), record.getEntityData().getId());
                unresolvedReference.setResolvedSyncariValue(record.getSyncariId());
                unresolvedReference.setReferredSyncariEntity(batch.getSyncariEntityName());
                lookupList.add(unresolvedReference);
        }
        // update unresolved records for this batch with the syncari ids
        unresolvedReferenceRepo.updateSyncariValues(lookupList);
    }

    private Optional<TransactionLog> saveTransactionLog(CurrentBatch batch, TransactionLog transactionLog, EntityData entity) {
        boolean isReportOnly = transactionLog.getOperation()==Operation.merge_report_only;
        //When we are in merge-report mode, we may end up adding spurious empty update txns on the winner
        //do the empty check against both changes and errors
        if(entity.getSyncariEntityId().equals(transactionLog.getSyncariId()) && !isReportOnly &&
                transactionLog.hasData() ) {
            transactionLog.setId(ObjectId.get().toHexString());
            transactionLog.setBatchId(batch.getCurrentBatchId());
            transactionLog.setNew(entity.isNew());
            //presence of id does not trigger createdAt/createdBy
            transactionLog.setCreatedAt(new Date());
            transactionLog.setCreatedBy(Optional.ofNullable(SyncariContext.getUser()).map(u->u.getId()).orElse(null));
            return Optional.of(transactionLog);
        }
        return Optional.empty();
    }

    private Pair<EntityData, Optional<TransactionLog>> applyMerge(CurrentBatch batch, EntityDefinition syncariEntityDef, EntityData entity,
                                                                  Optional<MergeOperation> possibleMergeOperation, List<EntityData> entitiesBatch, GraphContext graphContext) {
        Connector  connector = graphContext.cache( "syncariConnector", ()->connectorService.getSyncariConnector());

        // if we have a merge operation, we need to update the entity with the merge result

        var mergeResult = possibleMergeOperation.map(mergeOperation -> {
            EntityData winner = mergeOperation.getWinningRecord();
            TransactionLog mergeTransaction = null;
            if(mergeOperation.hasLosers()) {
                mergeTransaction = new TransactionLog()
                        .setBatchId(batch.getCurrentBatchId())
                        .setEntityName(syncariEntityDef.getApiName())
                        .setEntityId(syncariEntityDef.getId())
                        .setAdditionalInfo(Map.of("mergeDetails", mergeOperation))
                        .setSyncariId(winner.getSyncariEntityId())
                        .addSource(connector.getId(), connector.getName(), syncariEntityDef.getId(), winner.getId(), System.currentTimeMillis());
                mergeTransaction.setId(ObjectId.get().toHexString());
                mergeTransaction.setCreatedAt(new Date());
                mergeTransaction.setCreatedBy(Optional.ofNullable(SyncariContext.getUser()).map(u->u.getId()).orElse(null));
                int totalDupes = mergeOperation.getTotalDupes();
                if (mergeOperation.isReportOnly() || mergeOperation.hasMoreDupesThanMaxAllowedDupes()) {
                    mergeTransaction.setOperation(Operation.merge_report_only);
                    // return entity as winner for report only mode
                    // for report only mode save the winner and losers if they are in batch, this keeps the behavior same between report only and merge
                    var losersInBatch = entitiesBatch.stream().filter(e -> mergeOperation.getLoserIds().contains(e.getId())).collect(Collectors.toList());
                    log.info("Saving Losers {}", losersInBatch.size());
                    entityRepo.saveAll(syncariEntityDef, losersInBatch);
                    var winnerInBatch = entitiesBatch.stream().filter(e -> e.getId().equals(mergeOperation.getWinningRecord().getId())).findFirst();
                    if (!winnerInBatch.isEmpty()) {
                        entityRepo.save(syncariEntityDef, winnerInBatch.get());
                    }
                    winner = entity;
                    if (mergeOperation.hasMoreDupesThanMaxAllowedDupes() && (!mergeOperation.isReportOnly())){
                        String body = String.format(I18n.i18n("max_dupes_body"), mergeOperation.getEntity().getDisplayName(), winner.getId(), totalDupes, mergeOperation.getMaxAllowedDupes());
                        notificationService.broadcast(String.format(I18n.i18n("max_dupes_subject"), totalDupes, mergeOperation.getEntity().getDisplayName()), body, NotificationType.ANNOUNCEMENT);
                    }
                }else{
                    mergeTransaction.setOperation(Operation.merge);
                    recordMergeService.apply(mergeOperation, graphContext);
                    log.debug("Done recordMergeService");
                }
            }
            return Pair.of(winner,Optional.ofNullable(mergeTransaction));
        }).orElseGet(() -> Pair.of(entity,Optional.empty()));

        possibleMergeOperation.map(mergeOperation -> mergeOperation.getLoserIds()).ifPresent(loserIds->{
            //remove the losers from the batch
            entitiesBatch.removeIf(e->loserIds.contains(e.getSyncariEntityId()));
        });

        // if entityBatch has winner then remove old value from batch here, let it be added later in the process
        possibleMergeOperation.ifPresent(mergeOperation -> {
            EntityData winner = mergeResult.getX();
            entitiesBatch.removeIf(e -> e.getId().equals(winner.getId()));
        });

        return mergeResult;
    }

    private Optional<MergeOperation> getMergeOperation(MappingGraph entityGraph, EntityDefinition syncariEntityDef, EntityData entity, GraphContext graphContext,
                                                       TransactionLog log, Optional<EntityData> existingRecord, List<EntityData> entitiesBatch) {
        CoreEntityNodeConfig configuration = (CoreEntityNodeConfig) entityGraph.getCoreNode().getConfiguration();
        DedupeConfig dedupeConfig = configuration.getDedupeConfig();
        AdvancedDedupeConfig advancedDedupeConfig = configuration.getAdvancedDedupeConfig();
        Optional<MergeOperation> possibleMergeOperation = Optional.empty();
        if (advancedDedupeConfig!=null){
            entity.setDedupeHash(advancedDedupeConfig.getDedupeHash());
            possibleMergeOperation = recordMergeService.advancedDedupeMerge(advancedDedupeConfig,entity, syncariEntityDef,graphContext, log, existingRecord, entitiesBatch);
        }
        return possibleMergeOperation;
    }

    private String toApiName(String value) {
        return PipelineHelper.toApiName(value);
    }

    private Change createDeletedEntiy(EntityDefinition def, RecordsBySyncariId records, GraphContext graphContext, Long lastModifiedTS) {
        final String syncariId = records.getSyncariId();
        EntityData entityData = new EntityData(def.getApiName()).setSyncariEntityId(syncariId).setId(syncariId);
        entityData.setDeleted(true);
        Optional<EntityData> existing = records.getExistingRecord();
        var isExistingDeleted = existing.map(e -> e.isDeleted()).orElse(false);
        var txLog = new TransactionLog().setBatchId(graphContext.getCurrentBatch().getCurrentBatchId())
                .setEntityName(def.getApiName())
                .setEntityId(def.getId())
                .setOperation(Operation.delete)
                .setNew(false)
                .setSyncariId(syncariId)
                .setOccurredAt(lastModifiedTS);
        //An existing undeleted record was deleted in an external system
        //This is the only case where we do a delete on our side
        //Other cases are:
        // Already deleted existing record in syncari (via merge, so the resulting deletes in end system from previous cycle
        // are coming back to us. we ignore them)
        // Records soft deleted in end systems before they are connected to syncari. We don't care about them
        if (existing.isPresent() && !isExistingDeleted) {
            txLog.addChange(new FieldChange().setApiName("isDeleted").setFieldId("isDeleted").setTimestamp(lastModifiedTS).setOldValue(false).setNewValue(true));
        }
        return new Change(txLog, entityData, existing);
    }

    private void runActions(MappingGraph graph, EntityData entityData, EntityDefinition syncariEntityDef, GraphContext context) {
        Timer attributeActionsCheck = new Timer(50,
            "runActions pipeline evaluation for entity " + syncariEntityDef.getApiName(), log);
        var actionNodes = getCoreConnectedActions( graph);
        MappingNode coreNode = graph.getCoreNode();
        syncariEntityDef.getAttributes().forEach(a->{
            if(entityData.getValues().containsKey(a.getApiName())) {
                context.put("field_" + a.getId(), entityData.getValue(a.getApiName()));
            }
        });
        context.put(syncariEntityDef.getApiName(),entityData.getValues());
        context.put("output_"+ coreNode.getId(),Pair.of(new FunctionResult(entityData,ObjectType.VALUE),coreNode));
        context.put("record", entityData);
        context.put("previous", entityData);
        context.put(PipelineHelper.INCOMING_CHANGE_FIELD,entityData.isNew() ? "insert":"update");
        context.setCurrentSyncariId(entityData.getSyncariEntityId());
        actionNodes.forEach(actionNode -> {
            String alertAction = String.format("runActions evaluation. Details: actionNode: %s, Graph: %s", 
                actionNode.getApiName(), graph.getName());
            try (Timer check = new Timer(50, alertAction, log)) {
                evaluator.evaluate(actionNode, graph, context, n -> ACTION_TERMINALS.contains(n.getType()), new HashSet<String>());
            } catch (TerminateExecutionPathException e) {
				context.addError(entityData.getSyncariEntityId(), 
						new NodeError().setError(e.getMessage()).setErrorDetails(ExceptionUtils.getStackTrace(e))
								.setNodeId(actionNode.getId()).setNodeName(actionNode.getApiName()));
                log.warn("Action Execution terminated due to errors", e);
            }
        });
        attributeActionsCheck.close();
    }

    private void runAttributeActions(MappingGraph graph,AttributeDefinition coreAttribute, Object attributeValue,EntityData entityData, EntityDefinition syncariEntityDef, GraphContext context) {
        Timer attributeActionsCheck = new Timer(50,
            "runAttributeActions pipeline evaluation for attribute " + coreAttribute.getApiName(), log);
        var actionNodes = getCoreConnectedActions( graph);
        attributeActionsCheck.timedAt(50);
        MappingNode coreNode = graph.getCoreNode();
        syncariEntityDef.getAttributes().forEach(a->{
            if(entityData.getValues().containsKey(a.getApiName())) {
                context.put("field_" + a.getId(), entityData.getValue(a.getApiName()));
            }
        });
        attributeActionsCheck.timedAt(50, "Enrich context with entityData values");
        context.put(syncariEntityDef.getApiName(),entityData.getValues());
        context.put("output_"+ coreNode.getId(),Pair.of(new FunctionResult(attributeValue,coreAttribute.getDataType()),coreNode));
        context.put("record", entityData);
        context.put(PipelineHelper.INCOMING_CHANGE_FIELD,entityData.isNew() ? "insert":"update");
        context.setCurrentSyncariId(entityData.getSyncariEntityId());
        actionNodes.forEach(actionNode -> {
            String alertAction = String.format("runAttributeActions Pipeline evaluation. Details: actionNode: %s, Graph: %s", 
                actionNode.getApiName(), graph.getName());
            // Individual function evaluations are supposed to be faster.
            try (Timer check = new Timer(50, alertAction, log)) {
                evaluator.evaluate(actionNode, graph, context, n -> ACTION_TERMINALS.contains(n.getType()), new HashSet<String>());
            }catch (TerminateExecutionPathException e){
                log.warn("Action Execution terminated due to errors", e);
            }
        });
        attributeActionsCheck.close();
    }

    private void runSourceConnectedAttributeActions(MappingGraph graph, GraphContext context) {
        var actionNodes = getSourceConnectedActions( graph);
        actionNodes.forEach(actionNode -> {
            try {
                evaluator.evaluate(actionNode, graph, context, n -> ACTION_TERMINALS.contains(n.getType()), new HashSet<String>());
            }catch (TerminateExecutionPathException e){
                log.warn("Action Execution terminated due to errors", e);
            }
        });
    }

    /**
     * Find only standalone actions, without outbound edges , and not connected to a destination
     * @param graph
     * @return
     */
    private Stream<MappingNode> getCoreConnectedActions(MappingGraph graph) {
        Stream<MappingNode> actions = graph.getActions();
        var terminalActions = actions.filter(a -> graph.getOutboundEdges(a).isEmpty() &&
                graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.CORE_ATTRIBUTE || node.getType()== MappingNodeType.CORE_ENTITY) &&
                !graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.ATTRIBUTE_SINK || node.getType()== MappingNodeType.ENTITY_SINK)
        );

        var loopNodes = graph.getLoopNodes();
        var terminalLoopNodes = loopNodes.filter(a -> graph.getOutboundEdges(a).size() == 1 &&
                graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.CORE_ATTRIBUTE || node.getType()== MappingNodeType.CORE_ENTITY) &&
                !graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.ATTRIBUTE_SINK || node.getType()== MappingNodeType.ENTITY_SINK)
        );
        return Stream.concat(terminalActions, terminalLoopNodes);
    }



    private Stream<MappingNode> getSourceConnectedActions(MappingGraph graph) {
        Stream<MappingNode> actions = graph.getActions();
        var terminalActions = actions.filter(a -> graph.getOutboundEdges(a).isEmpty() &&
                graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.ATTRIBUTE_SOURCE || node.getType()== MappingNodeType.ENTITY_SOURCE)
                //exclude actions connected to core nodes - they are handled separately. We have to expliicitly exclude them,
                //because all actions connected to core node are also connected to sources
                && !graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.CORE_ATTRIBUTE || node.getType()== MappingNodeType.CORE_ENTITY)
        );

        var loopNodes = graph.getLoopNodes();
        var terminalLoopNodes = loopNodes.filter(a -> graph.getOutboundEdges(a).size() == 1 &&
                graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.ATTRIBUTE_SOURCE || node.getType()== MappingNodeType.ENTITY_SOURCE)
                //exclude actions connected to core nodes - they are handled separately. We have to expliicitly exclude them,
                //because all actions connected to core node are also connected to sources
                && !graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.CORE_ATTRIBUTE || node.getType()== MappingNodeType.CORE_ENTITY)
        );
        return Stream.concat(terminalActions, terminalLoopNodes);
    }

    Change createSyncariEntityWithGraph(FieldsGraphRequest fieldsGraphRequest) {

        String entityName = fieldsGraphRequest.getEntityName();
        EntityDefinition syncariEntityDef = fieldsGraphRequest.getSyncariEntityDef();
        RecordsBySyncariId records = fieldsGraphRequest.getRecords();
        GraphContext currentContext = fieldsGraphRequest.getGraphContext();
        var fieldDAGs = fieldsGraphRequest.getAttributeDAGs();
        var batchActionContext = fieldsGraphRequest.getAttributeBatchActionContext();
        var batchedOperations = fieldsGraphRequest.getBatchedOperations();
        var resolvedIds = fieldsGraphRequest.getResolvedIds();
        var entityDefCache = fieldsGraphRequest.getEntityDefCache();
        var connectorCache = fieldsGraphRequest.getConnectorCache();
        Timers efpTimer = new Timers(log);

        Timer methodCheck = new Timer(5000, String.format("createSyncariEntityWithGraph{%s}", entityName), log);
        log.debug("Applying all field pipelines for entity {}", entityName);

        String syncariId = records.getSyncariId();
        efpTimer.start("createSyncariEntityWithGraph_" + syncariId);
        var syncariEntity = new EntityData(entityName).setSyncariEntityId(syncariId);
        syncariEntity.setId(syncariId);
        currentContext.setCurrentSyncariId(syncariId);
        Optional<EntityData> existing = records.getExistingRecord();
        var existingSyncariEntity = existing.orElse(syncariEntity);
        existingSyncariEntity.setIgnoreFieldChanges(Set.of("SystemModstamp", "LastModifiedDate"));
        existing.ifPresentOrElse(e -> {
            log.debug("Found existing syncari entity {} with id  {}", e.getName(), e.getSyncariEntityId());
            currentContext.put("record",e);
        }, () -> currentContext.put("record", existingSyncariEntity));
        methodCheck.timedAt(10, "Delay in finding Entity " + entityName + ": Record with Syncari Id " + syncariId);
        if (existing.isEmpty()) {
            syncariEntity.setNew(true);
            syncariEntity.setSyncariCreatedAt(Instant.now().toEpochMilli());
        }
        var ts = System.currentTimeMillis();
        //TODO: Deal with convert/delete
		var transactionLog = new TransactionLog().setSyncariId(syncariId).setEntityName(entityName)
				.setEntityId(syncariEntityDef.getId())
				.setOperation(existing.isPresent() ? Operation.update : Operation.create)
				.setErrors(fieldsGraphRequest.getGraphContext().getErrors().getOrDefault(syncariId, new ArrayList<>()));
        //apiName to entityeDef Cache. have to use hacks like this until services become cache aware
        Map<String, EntityDefinition> syncariEntityCache = new HashMap<>();
        Timer forEachDAGCheck = new Timer(3000, "Foreach DAG Check", log);
        String graphId = fieldsGraphRequest.getGraphContext().getGraph().getId();

        fieldDAGs.forEach((syncariAttribute, dag) -> {
            efpTimer.start("fp_" + syncariAttribute.getApiName());
            log.debug("Applying field pipeline for attribute {}, dag id {} with {} nodes", syncariAttribute.getApiName(), dag.getId(), dag.getNodes().size());
            var childContext = currentContext.createSubContext(dag);
            existing.ifPresent(e -> {
                childContext.put("existing", e);
            });
            childContext.setBatchActionContext(batchActionContext);
            childContext.setSyncariRecord(syncariEntity);
            childContext.put(PipelineHelper.INCOMING_CHANGE_FIELD, syncariEntity.isNew() ? "insert" : "update");
            childContext.setCurrentSyncariId(syncariId);
            Stream<MappingNode> sources = dag.getSources();
            MappingNode coreNode = dag.getCoreNode();
            CoreAttributeNodeConfig coreConfig =coreNode.getTypedConfiguration();
            Stream<String> resultIds = dag.getInboundEdges(coreNode).stream().map(edge -> "output_" + edge.getSourceStage().getId());
            //Seed context with source values
            List<Object> inputs = new ArrayList<>();
            List<AttributeValue> candidates = new ArrayList<>();
            //setup syncari field values for filters

            existing.ifPresent(syncariRecord ->
                    syncariEntityDef.getActiveAttributes().forEach(attribute -> {
                        if (syncariRecord.has(attribute.getApiName())) {
                            childContext.put("field_" + attribute.getId(), syncariRecord.getValue(attribute.getApiName()));
                        }
                    })
            );

            Timer sourceNodeCheck = new Timer(5000,
                String.format("Source node check for entity %s attribute %s", entityName, syncariAttribute.getApiName()), log);
            efpTimer.start("da_" + syncariAttribute.getApiName());
            sources.forEach(node -> {
                AttributeSourceNodeConfig config = (AttributeSourceNodeConfig) node.getConfiguration();
                AttributeDefinition externalAttribute = config.getAttributeDefinition();

                EntityDefinition externalEntity = childContext.cache(externalAttribute.getEntityId(), () -> schemaService.getEntity(externalAttribute.getEntityId()));
                entityDefCache.put(externalAttribute.getEntityId(), externalEntity);
                Connector connector = childContext.cache(externalEntity.getConnectorId(), () -> connectorService.get(externalEntity.getConnectorId()));
                connector = connectorService.refreshAuthentication(connector);
                connectorCache.put(connector.getId(), connector);


                //Presence of a value vs absence is super-important. Set the valye ONLY if key is present
                String attributeKey = "field_" + externalAttribute.getId();
                if (childContext.containsKey(attributeKey)) {
                    Object value = childContext.get(attributeKey);
                    //TODO this can be batched to boost performance
                    candidates.add(new AttributeValue(value, externalAttribute, connector.getId(), node));
                    inputs.add(value);
                }else{
                    log.debug("Could not find source value for DAG {} with connector  {}, entity {} and attribute {}",
                            dag.getName(),connector.getName(),externalEntity.getApiName(), externalAttribute.getApiName());
                    var result = new FunctionResult(FilterFailedResult.VALUE, externalAttribute.getDataType());
                    childContext.put("output_" + node.getId(), Pair.of(result, node));
                }
            });
            sourceNodeCheck.close();
            efpTimer.end("da_" + syncariAttribute.getApiName());

            Optional<AttributeValue> authority = getAuthoritativeValue(records, coreConfig, candidates);
            authority.ifPresentOrElse(
                    a -> {
                        childContext.put(AUTHORITY_SOURCE_ATTRIBUTE, a);
                        var result = new FunctionResult(a.getValue(), a.attribute.getDataType());
                        childContext.put("output_" + a.node.getId(), Pair.of(result, a.node));
                        childContext.captureTestOutputForNode(result, a.node, null);
                        //set output to failed for the rest of them
                        candidates.forEach(candidate-> {
                            //reference equality check on the candidate
                            if(candidate!=a){
                                childContext.put("output_" + candidate.node.getId(), Pair.of(new FunctionResult(FilterFailedResult.VALUE, candidate.attribute.getDataType()), candidate.node));
                                childContext.captureTestOutputForNode(new FunctionResult(FilterFailedResult.VALUE, candidate.attribute.getDataType()), a.node, null);
                            }
                        });
                    },
                    () -> {
                        log.debug("No Authoritative source value found for attribute {}, record {} ", syncariAttribute.getApiName(),existingSyncariEntity.getId());
                    }
            );
            //evaluate all paths to core node
            Timer coreNodeCheck = new Timer(5000,
                String.format("Core node check for entity %s attribute %s", entityName, syncariAttribute.getApiName()), log);
            try  {
                efpTimer.start("eval_" + syncariAttribute.getApiName());

                evaluator.evaluate(coreNode, dag, childContext, n -> n.getType() == MappingNodeType.ATTRIBUTE_SOURCE, new HashSet<String>());

                efpTimer.end("eval_" + syncariAttribute.getApiName());
                List<Pair<FunctionResult, MappingNode>> results = resultIds.flatMap(resultId -> Optional.ofNullable((Pair<FunctionResult, MappingNode>) childContext.get(resultId)).stream()).collect(Collectors.toList());

                //TODO: Apply Conflict Resolution, but pick the first value for now
                //Also skip unmatching branches
                List<Pair<FunctionResult, MappingNode>> successfulResults = results.stream().filter(r -> !FilterFailedResult.isFailedFilter(r.x.getResult())).collect(Collectors.toList());
                List<Pair<FunctionResult, MappingNode>> nonnullResults = successfulResults.stream().filter(r -> r.x.typedValue() != null).collect(Collectors.toList());
                var maybeResult = nonnullResults.isEmpty() ? successfulResults.stream().findFirst() : nonnullResults.stream().findFirst();
                //If attribute is of child type,
                if(syncariAttribute.isChild()){
                    Object children = getChildRecords(syncariAttribute, results);
                    maybeResult = maybeResult.map(r-> Pair.of(new FunctionResult(children, ChildType.VALUE),r.y));
                }

                log.debug("Applied field pipeline for entity {} id {} attribute {} and got result {} from all results {}", entityName, syncariId,syncariAttribute.getApiName(), maybeResult, results, childContext.entrySet());
                    maybeResult.stream().forEach(result -> {
                        FunctionResult finalResult = result.x;
                        if(coreConfig.isRejectEmptyString() && finalResult.isBlank()) {
                            log.debug("Found empty string value for {} on entity {}. Skipping it",coreNode.getName(),entityName);
                            return;
                        }
                        else if(coreConfig.isRejectEmptyValue() && finalResult.isNull()){
                            log.debug("Found null value for {} on entity {}. Skipping it",coreNode.getName(),entityName);
                            return;
                        }
                        //If no exisiting entity is present, its a new record and we have to record everything
                        Object convertedValue = checkForEmptyString(finalResult) ? finalResult.getResult() : syncariAttribute.convert(finalResult.getResult());
                        if (syncariAttribute.isReference() && convertedValue != null && !isSyncariId(syncariAttribute, convertedValue,syncariEntityCache)) {
                            efpTimer.start("fk_" + syncariAttribute.getApiName());

                            final Object tempValue = convertedValue;

                            final Object resolvedValue = childContext.getValueOpt(AUTHORITY_SOURCE_ATTRIBUTE, AttributeValue.class).map(a -> {
                                final Object value;
                                EntityDefinition referredExternalEntity = findExternalEntityByName(entityDefCache, a.getConnectorId(), a.getAttribute().getReferenceTo());
                                ResolvedReference maybeResolved = findSyncariFk(entityName, syncariAttribute, tempValue, referredExternalEntity, currentContext, resolvedIds);
                                if (maybeResolved.hasResolvedReferences()) {
                                    value = syncariAttribute.isMultiValueField() ? maybeResolved.getResolvedReferences() : maybeResolved.getResolvedReference();
                                } else {
                                    log.debug("Could not resolve reference for {} on attribute {}.", a.getAttribute().getReferenceTo(), a.getAttribute().getApiName());
                                    value = null;
                                }
                                if (maybeResolved.hasUnresolvedReferences()) {
                                    Set<String> unresolvedRefs = syncariAttribute.isMultiValueField() ? maybeResolved.getUnresolvedReferences() : 
                                        Set.of(maybeResolved.getUnResolvedReference());
                                    List<UnresolvedReference> entries = unresolvedRefs.stream().map(unresolved ->
                                            new UnresolvedReference(syncariAttribute.getEntityId(), syncariEntity.getId(), syncariAttribute.getApiName(),
                                                    a.getConnectorId(), a.getAttribute().getReferenceTo(), unresolved, syncariAttribute.getReferenceTo())
                                    ).collect(Collectors.toList());
                                    if (batchedOperations.getUnresolvedReferences().size() >= UNRESOLVED_REFERENCE_BATCH_SIZE) {
                                        unresolvedReferenceRepo.upsertUnResolved(batchedOperations.getUnresolvedReferences());
                                        batchedOperations.getUnresolvedReferences().clear();
                                    }
                                    batchedOperations.getUnresolvedReferences().addAll(entries);
                                }
                                return value;
                            }).orElse(null);

                            convertedValue = resolvedValue;
                            // ths is to avoid changing hasChange signature below
                            finalResult = finalResult.withResult(convertedValue);
                            log.debug("Resolved reference for attribute {} and attribute {}", syncariAttribute.getReferenceTo(), syncariAttribute.getApiName());
                            efpTimer.end("fk_" + syncariAttribute.getApiName());
                        }

                        FieldChange change = new FieldChange()
                                .setFieldId(syncariAttribute.getId())
                                .setApiName(syncariAttribute.getApiName())
                                .setDisplayName(syncariAttribute.getDisplayName())
                                .setDataType(syncariAttribute.getDataType().getName())
                                .setNewValue(convertedValue)
                                .setOldValue(existing.isPresent() ? existingSyncariEntity.getValue(syncariAttribute.getApiName()) : null)
                                .setTimestamp(ts);
                        dag.getConnectedSources().forEach( source ->{
                            AttributeDefinition externalAttribute = ((AttributeSourceNodeConfig)source.getConfiguration()).getAttributeDefinition();
                            String externalAttributeId = externalAttribute.getId();
                            String contextKey = "field_" + externalAttributeId;
                            if(!StringUtils.isBlank(externalAttributeId) && childContext.containsKey(contextKey)){
                                Object value = maskValueIfEncrypted(childContext, externalAttributeId, childContext.get(contextKey));
                                logSourceField(change, externalAttribute, value, connectorCache, entityDefCache);
                            }

                            childContext.getValueOpt(AUTHORITY_SOURCE_ATTRIBUTE, AttributeValue.class).ifPresent(a -> {
                                if (externalAttributeId.equals(a.attribute.getId())) {
                                    change.setAuthoritativeSource(
                                            new ExternalValue()
                                                    .setFieldId(externalAttributeId)
                                                    .setApiName(a.getAttribute().getApiName())
                                                    .setDisplayName(a.getAttribute().getDisplayName())
                                                    .setDataType(a.getAttribute().getDataType().getName())
                                                    .setConnectorId(a.getConnectorId())
                                                    .setConnectorName(connectorCache.get(a.getConnectorId()).getName())
                                                    .setValue(maskValueIfEncrypted(childContext, externalAttributeId, a.getValue()))
                                    );
                                }
                            });
                        });

                        //Check if there are changes to be applied to Syncari, force changes if testing
                        //sending the converted value here instead of FunctionResult directly
                        boolean hasChange = hasChange(existing, existingSyncariEntity, syncariAttribute, finalResult, inputs);
                        if (hasChange || childContext.isTestMode()) {
                            transactionLog.addChange(change);
                            syncariEntity.addValue(syncariAttribute.getApiName(), convertedValue);
                        }

                        // capture node output for core node of FP
                        FunctionResult coreNodeOutput = new FunctionResult(convertedValue, syncariAttribute.getDataType(), finalResult.getLookupResult());
                        childContext.put("output_" + coreNode.getId(), Pair.of(coreNodeOutput, coreNode));
                        childContext.captureTestOutputForNode(coreNodeOutput, coreNode, result.y);
                    });
            }catch (TerminateExecutionPathException e){
                log.warn("Execution path was terminated due to a failing filter for field {}", syncariAttribute.getApiName());
            } catch (Exception e){
                log.error(
                        String.format(
                                "Error in field pipeline for attribute %s, dag id %s and executing source record %s. Error: %s",
                                syncariAttribute.getApiName(),dag.getId(), childContext.get(AUTHORITY_SOURCE_ATTRIBUTE), e.getMessage()),
                        e);
                throw e;
            }
            coreNodeCheck.close();
            //Run source side actions
            try (Timer check = new Timer(100, "runSourceConnectedAttributeActions", log)) {
                runSourceConnectedAttributeActions(dag, childContext);
            }
            childContext.clear();
            efpTimer.end("fp_" + syncariAttribute.getApiName());
        });
        currentContext.clearChildren();
        forEachDAGCheck.close(String.format("%d field DAGs", fieldDAGs.size()));
        // take a fresh copy of existing syncari record because it might have been modified as part of any FP (through updateSyncariRecord function)
        //Do this only if the current record was mutated by any FP
        final Set<String> mutatedRecordsIds = currentContext.cachedOrDefault("mutatedRecordIds_" + syncariEntityDef.getApiName(), Set.of());
        Optional<EntityData> existingRecord = mutatedRecordsIds.contains(syncariId)? entityRepo.findById(syncariEntityDef, syncariId) : existing;
        var namedFields = existingRecord.map(e -> new HashMap(e.getValues())).orElse(new HashMap<>());
        namedFields.putAll(syncariEntity.getValues());
        EntityData mergedEntity = syncariEntity.withValues(namedFields);
        existingRecord.ifPresent(e -> {
            mergedEntity.setSyncariScore(e.getSyncariScore());
        });
        records.getRecords().forEach(stagedBatchRecord -> {
            Optional<IdMapping.Mapping> disconnectedMapping = records.getIdMapping().flatMap(i -> i.findDisconnected(stagedBatchRecord.getExternalEntityDefinitionId()));
            if(disconnectedMapping.isEmpty()){
                repoService.connectExternalId(syncariEntityDef,mergedEntity,stagedBatchRecord.getExternalEntityDefinitionId(),
                        Optional.of(transactionLog),stagedBatchRecord.getExternalRecordId());

            }
        });

        //Unset the reparented flag, because we've handled the case of null FKs correctly by now.
        mergedEntity.setReparented(false);
        if(!featureService.isEnabled(Features.SinksideActions, true)) {
            Timer fieldDAGsCheck = new Timer(5000, String.format("runAttributeActions on %d field DAGs", fieldDAGs.size()), log);
            efpTimer.start("runActions");
            fieldDAGs.forEach((syncariAttribute, dag) -> {
                if (!getCoreConnectedActions(dag).findFirst().isPresent()) return;
                efpTimer.start("runActions_" + syncariAttribute.getApiName());
                var childContext = currentContext.getOrCreateSubContext(dag);
                childContext.setBatchActionContext(batchActionContext);
                childContext.put(entityName,namedFields);
                runAttributeActions(dag, syncariAttribute, mergedEntity.getValue(syncariAttribute.getApiName()), mergedEntity, syncariEntityDef, childContext);
                childContext.clear();
                efpTimer.end("runActions_" + syncariAttribute.getApiName());
            });
            fieldDAGsCheck.close();
            efpTimer.end("runActions");
        }

        currentContext.clearChildren();
        // capture nodeData for coreNode of EP
        currentContext.captureTestOutputForCoreEntityNode(new FunctionResult(mergedEntity, ObjectType.VALUE), currentContext.getGraph().getCoreNode());
        log.debug("Found Changes from upstream for entity {} with id {}, and changes {}", syncariEntity.getName(), syncariEntity.getSyncariEntityId(), transactionLog);
        methodCheck.close();
        efpTimer.end("createSyncariEntityWithGraph_" + syncariId);
        efpTimer.logDebug();
        return new Change(transactionLog, mergedEntity, existingRecord);
    }

    private boolean checkForEmptyString(FunctionResult finalResult) {
        Object result = finalResult.getResult();
        if(result != null && result instanceof String && StringUtils.isBlank(result.toString())) {
            return true;
        }
        return false;
    }

    private Object maskValueIfEncrypted(GraphContext context, String attributeId, Object value) {
        log.debug("{}_id encrypted: {}", attributeId, context.get(attributeId + "_encrypted"));
        return context.get(attributeId + "_encrypted") != null ? "******" : value;
    }

    protected boolean isResolvableReference(AttributeDefinition reference, GraphContext currentContext) {
        if(!reference.isReference() || StringUtils.isBlank(reference.getReferenceTo())){
            return false;
        }
        final Optional<EntityDefinition> referencedEntity = currentContext.cache("entity_" + reference.getReferenceTo(), () -> schemaService.getSyncariEntityByName(reference.getReferenceTo()));
        Optional<Long> count = referencedEntity.map(r -> currentContext.cache("record_count_" + r.getApiName(), () -> entityRepo.count(r, Optional.empty())));
        return (count.isPresent() && count.get() > 0);
    }

    private void logSourceField(FieldChange fieldChange, AttributeDefinition externalAttribute, Object value, Map<String, Connector> connectionCache, Map<String, EntityDefinition> entityDefCache) {
        var entityDefinition = entityDefCache.computeIfAbsent(externalAttribute.getEntityId(), id -> schemaService.getEntity(id));
        String connectorId = entityDefinition.getConnectorId();
        Optional<Connector> maybeConnector = connectionCache.containsKey(connectorId) ? Optional.ofNullable(connectionCache.get(connectorId)): connectorService.find(connectorId);
        maybeConnector.ifPresent(connector -> connectionCache.put(connectorId, connector));
        var connectorName = maybeConnector.map(c -> c.getName()).orElse("");
        ExternalValue externalValue = new ExternalValue().setFieldId(externalAttribute.getId())
                .setDisplayName(externalAttribute.getDisplayName())
                .setApiName(externalAttribute.getApiName())
                .setDataType(externalAttribute.getDataType().getName())
                .setConnectorName(connectorName)
                .setConnectorId(connectorId)
                .setValue(value);

        fieldChange.addIncomingExternalValue(externalAttribute.getId(), externalValue);
    }

    protected boolean isSyncariId(AttributeDefinition syncariAttribute, Object tempValue,Map<String, EntityDefinition> syncariEntityCache) {
        //check if valye is in a proper id format
        if(ObjectId.isValid(tempValue.toString())){
            //find and cache referenced entity
            final EntityDefinition referencedSyncariEntity=
            syncariEntityCache.computeIfAbsent(syncariAttribute.getReferenceTo(),(attributeName)->schemaService.getSyncariEntityByName(attributeName).orElse(null));
            //if referenced entity not found (should nnot happen) or if the record doesn't exist
            return referencedSyncariEntity==null? false : entityRepo.findById(referencedSyncariEntity, tempValue.toString()).isPresent();
        }
        return false;
    }

    private Object getChildRecords(AttributeDefinition syncariAttribute, List<Pair<FunctionResult, MappingNode>> results) {
        Object children = null;
        if(syncariAttribute.isChild()){
            List<EntityData> childRecords= new ArrayList<>();
            //each result is one or more fields
            results.forEach(result->{
                Object r = result.x.getResult();
                List<Object> partialChildRecords = toList(r);
                for (int i=0;i<partialChildRecords.size();i++) {
                    Object partialChildRecord = partialChildRecords.get(i);
                    EntityData childRecord = childRecords.size()>i ? childRecords.get(i) : new EntityData(syncariAttribute.getReferenceTo());
                    if (!FilterFailedResult.isFailedFilter(partialChildRecord)) {
                        childRecord.getValues().putAll(((EntityData)partialChildRecord).getValues());
                    }
                    if(childRecords.size()<=i){
                        childRecords.add(childRecord);
                    }
                }
            });
            List<EntityData> nonEmptyChildRecords = childRecords.stream().filter(c -> !c.getValues().isEmpty()).collect(Collectors.toList());
            if (syncariAttribute.isMultiValueField()) {
                children = nonEmptyChildRecords;
            } else {
                children = nonEmptyChildRecords.isEmpty() ? nonEmptyChildRecords : childRecords.get(0);
            }
        }
        return children;
    }

    private static List<Object> toList(Object r) {
        if (r instanceof List) {
            return (List<Object>) r;
        } else if (r == null) {
            return List.of();
        } else {
            return List.of(r);
        }
    }


    private EntityDefinition findExternalEntityByName(Map<String, EntityDefinition> entityDefCache, String connectorId, String apiName) {
        Optional<Map.Entry<String, EntityDefinition>> matching = entityDefCache.entrySet().stream().filter(e -> e.getValue().getConnectorId().equals(connectorId) && e.getValue().getApiName().equals(apiName)).findFirst();
        return matching.map(m -> m.getValue()).orElseGet(() -> {
            EntityDefinition entity = schemaService.findEntity(connectorId, apiName).orElse(null);
            if (entity != null) {
                entityDefCache.put(entity.getId(), entity);
            }
            return entity;
        });
    }

    private EntityDefinition findExternalEntityById(HashMap<String, EntityDefinition> entityDefCache, String entityDefId) {
        Optional<Map.Entry<String, EntityDefinition>> matching = entityDefCache.entrySet().stream().filter(e -> e.getValue().getId().equals(entityDefId)).findFirst();
        return matching.map(m -> m.getValue()).orElseGet(()->{
            EntityDefinition entity = schemaService.findEntity(entityDefId).orElse(null);
            if(entity!=null) {
                entityDefCache.put(entity.getId(), entity);
            }
            return  entity;
        });
    }

    Optional<AttributeValue> getAuthoritativeValue(RecordsBySyncariId records, CoreAttributeNodeConfig coreConfig, List<AttributeValue> candidates) {
        switch (coreConfig.getDataAuthority().getDatAuthorityStrategy()){
            case SELECTED_CONNECTOR:
                return new SelectedConnectorAuthority(records.getRecords(), candidates, coreConfig).authority();
            case LATEST_RECORD:
            case NONE:
            default:
                return new LatestRecordAuthority(records.getRecords(), candidates, coreConfig).authority();
        }
    }

    private boolean hasChange(Optional<EntityData> existing, EntityData existingSyncariEntity, AttributeDefinition syncariAttribute, FunctionResult finalResult, List<Object> inputs) {
        boolean isNullFK = syncariAttribute.isReference() && finalResult.getResult() ==null;
        //We allow null references, only if the existing record is currently not marked as reparented.
        Object value = checkForEmptyString(finalResult) ? finalResult.getResult() : syncariAttribute.convert(finalResult.typedValue());
        boolean allowNullFK = existing.map(e->!e.isReparented()).orElse(true);
        boolean allowValue = (isNullFK && allowNullFK) || !isNullFK;
        return !existing.isPresent() ||
                (!existingSyncariEntity.isIgnoredField(syncariAttribute.getApiName())
                        && existingSyncariEntity.hasChanges(syncariAttribute.getApiName(), value)
                        //Temporary fix to handle foreignkey fixes rrquired because of syncari merge
                        // if 2 accounts were merged into 1, in the synapse the loser will be deleted
                        //potentially setting fk refs of that loser to null.
                        //When this null value comes in in the cchild pipeline, we ignore it, because
                        // we really want to reparent, and not null out the FK.
                        && allowValue
                );
    }

    /**
     * return a pair of partially or fully resolved references & and list of remaining unresolved refs
     * @param entityName
     * @param syncariAttribute
     * @param tobeResolvedValue
     * @param externalEntityDefinition
     * @return
     */
    ResolvedReference findSyncariFk(String entityName, AttributeDefinition syncariAttribute, Object tobeResolvedValue,
                                            EntityDefinition externalEntityDefinition, GraphContext currentContext, Map<String, Map<String, String>> resolvedIdsMap) {

        if(tobeResolvedValue==null || externalEntityDefinition==null || StringUtils.isEmpty(tobeResolvedValue.toString())) {
            return new ResolvedReference(List.of(), Set.of());
        }
        String referenceEntity = syncariAttribute.getReferenceTo();

        // The incoming values can be list even though the syncariattribute is a single valued, in which case, we consider the first value.
        List<Object> tobeResolvedValues = (syncariAttribute.isMultiValueField() || List.class.isAssignableFrom(tobeResolvedValue.getClass())) ?
            List.class.cast(tobeResolvedValue) : List.of(tobeResolvedValue.toString());
        Set<String> tobeResolvedValueStrings = new LinkedHashSet<>();
        Set<String> finalTobeResolvedValueStrings1 = tobeResolvedValueStrings;
        tobeResolvedValues.forEach(r -> finalTobeResolvedValueStrings1.add(r.toString()));
        String connectorId = externalEntityDefinition.getConnectorId();
        Set<String> remainingUnresolved = new HashSet<>(tobeResolvedValueStrings);
        //try to resolve a reference to another entity, only if there iss an existing published pipeline for that entity
        if (!isResolvableReference(syncariAttribute, currentContext)) {
            return new ResolvedReference(List.of(), remainingUnresolved);
        }

        String key = referenceEntity + "#" + connectorId;
        Map<String, String> resolvedValueStrings = new HashMap<>();
        if(resolvedIdsMap.containsKey(key)) {
            Set<String> finalTobeResolvedValueStrings = tobeResolvedValueStrings;
            resolvedValueStrings = resolvedIdsMap.get(key).entrySet().stream().filter(x -> finalTobeResolvedValueStrings.contains(x.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            tobeResolvedValueStrings = tobeResolvedValueStrings.stream().filter(value -> !resolvedIdsMap.get(key).containsKey(value)).collect(Collectors.toSet());
        }

        if(!tobeResolvedValueStrings.isEmpty()) log.debug("Looking up {} ids from idMappingRepo", tobeResolvedValueStrings);
        List<IdMapping> resolvedIds = tobeResolvedValueStrings.isEmpty() ? List.of() : idMappingRepo.findByExternalIds(referenceEntity, connectorId, externalEntityDefinition.getId(), List.copyOf(tobeResolvedValueStrings));
        if (!resolvedIds.isEmpty() || !resolvedValueStrings.isEmpty()) {
            log.debug(
                    "Found fk value {} for entity {} on field {} and reference entity {} with id {} on connector {}",
                    resolvedIds, entityName, syncariAttribute.getApiName(), referenceEntity, tobeResolvedValues,
                    connectorId);
            Set<String> resolvedExternalIds = resolvedIds.stream().flatMap(resolvedId ->
                    resolvedId.getMappings(connectorId, externalEntityDefinition.getId()).stream().map(m -> m.getEntityId())
            ).collect(Collectors.toSet());
            resolvedExternalIds.addAll(resolvedValueStrings.keySet());

            remainingUnresolved.removeAll(resolvedExternalIds);
            List<String> resolvedReferences = resolvedIds.stream().map(r -> r.getSyncariId()).collect(Collectors.toList());
            resolvedReferences.addAll(resolvedValueStrings.values());
            return new ResolvedReference(resolvedReferences, remainingUnresolved);
        } else {
            log.debug(
                    "Could not resolve fk for entity {} on field {} and reference entity {} with id {} on connector {}",
                    entityName, syncariAttribute.getApiName(), referenceEntity, tobeResolvedValue, connectorId);
            return new ResolvedReference(List.of(),   remainingUnresolved);
        }
    }

    private void handleExistingUnresolvedFk(CurrentBatch batch,EntityDefinition syncariEntity) {
        // For syncari entity type, see if there are resolved fk in db, if yes, pick
        // them and update syncari entity
        List<UnresolvedReference> resolvedFks = unresolvedReferenceRepo.findResolvedReferenceBy(syncariEntity.getId());
        Map<String, EntityData> entitiesWithResolvedFks = new HashMap<>();
        List<String> nonExistingReferenceIds = new ArrayList<>();
        Connector syncariConnector = connectorService.getSyncariConnector();
        for (UnresolvedReference ref : resolvedFks) {
            EntityData record= entitiesWithResolvedFks.getOrDefault(ref.getSyncariRecordId(),new EntityData(syncariEntity.getApiName()));
            record.setSyncariEntityId(ref.getSyncariRecordId());
            record.setId(ref.getSyncariRecordId());
            // There are unresolvedreferences with reference fields that are non existing anymore in the syncari entity. 
            // Capture those and cleanup. Without this, we keep throwing error and stall the pipeline.
            if (!syncariEntity.hasField(ref.getSyncariAttributeName())) {
                nonExistingReferenceIds.add(ref.getId());
                continue;
            }
            AttributeDefinition syncariAttribute = syncariEntity.getFieldByName(ref.getSyncariAttributeName());
            if(syncariAttribute.isMultiValueField()){
                List<String> referenceList = record.has(syncariAttribute.getApiName()) ? record.getTypedValue(syncariAttribute.getApiName()) : new ArrayList<>();
                referenceList.add(ref.getResolvedSyncariValue());
                record.addValue(syncariAttribute.getApiName(), referenceList);
            }else{
                record.addValue(syncariAttribute.getApiName(), ref.getResolvedSyncariValue());
            }
            entitiesWithResolvedFks.put(record.getSyncariEntityId(),record);
        }
        //Saving entities updates timestamps and are caught downstream watermarks

        entityRepo.updateValues(syncariEntity, List.copyOf(entitiesWithResolvedFks.values()));
        createFKTransactions(batch, syncariEntity, entitiesWithResolvedFks, syncariConnector);
        // and delete the resolved fks
        List<String> tobeDeletedIds = resolvedFks.stream().map(u -> u.getId()).collect(Collectors.toList());
        if (tobeDeletedIds.size() > 0) {
            log.info("Deleting {} resolved fks", tobeDeletedIds.size());
        }
        List<List<String>> parts = Lists.partition(tobeDeletedIds, FLUSH_SIZE);
        parts.stream().forEach(x -> {
            unresolvedReferenceRepo.deleteAllById(x);
        });
        if (!nonExistingReferenceIds.isEmpty()) {
            log.info("Deleting {} non-existing unresolved fks: {}", nonExistingReferenceIds.size(), nonExistingReferenceIds);
            List<List<String>> partitioned = Lists.partition(nonExistingReferenceIds, FLUSH_SIZE);
            partitioned.stream().forEach(x -> {
                unresolvedReferenceRepo.deleteAllById(x);
            });
        }
    }

	private void createFKTransactions(CurrentBatch batch, EntityDefinition syncariEntity,
			Map<String, EntityData> entitiesWithResolvedFks, Connector syncariConnector) {
        long occurredAt = System.currentTimeMillis();
        List<TransactionLog> fkTransactions = entitiesWithResolvedFks.entrySet().stream().map(entry -> {
            TransactionLog fkTransaction = new TransactionLog().setBatchId(batch.getCurrentBatchId())
                    .setEntityName(syncariEntity.getApiName())
                    .setEntityId(syncariEntity.getId())
                    .setOperation(Operation.update)
                    .setSyncariId(entry.getValue().getSyncariEntityId())
                    .addSource(syncariConnector.getId(), syncariConnector.getName(), syncariEntity.getId(), entry.getValue().getSyncariEntityId(), System.currentTimeMillis())
                    .setOccurredAt(occurredAt);
            entry.getValue().getValues().forEach((refName, value) ->
                    //capture changes for only reference fields
                    syncariEntity.getField(refName).filter(f -> f.isReference()).ifPresent(field -> {
                        fkTransaction.addChange(new FieldChange().setApiName(refName)
                                .setFieldId(syncariEntity.getFieldByName(refName).getId())
                                .setTimestamp(occurredAt)
                                .setNewValue(value)
                                .setOldValue(null)
                        );
                    })
            );
            return fkTransaction;
        }).filter(t->!t.getChanges().isEmpty()).collect(Collectors.toList());
        transactionLogService.log(fkTransactions);
    }

    private boolean shouldFlushBeforeActions(MappingGraph graph, EntityDefinition syncariEntityDef) {
        var actionNodes = getCoreConnectedActions(graph);
        // if the graph has updateSyncariRecords action, the new records must be inserted for the action to be able to access it
        return actionNodes.anyMatch(actionNode -> {
            if (actionNode.isActionNode()) {
                GenericActionConfig actionConfig = actionNode.getTypedConfiguration();
                Object syncariEntityDefId = actionNode.getConfiguration().getConfigMap().get("syncariEntityDefId");
                return (ActionConstants.UPDATE_SYNCARI_RECORDS.equals(actionConfig.getName()) &&
                        ( syncariEntityDefId != null && syncariEntityDefId.toString().equalsIgnoreCase(syncariEntityDef.getId())));
            }
            return false;
        });
    }

    private void flushEntityBatch(EntityDefinition syncariEntityDef, List<EntityData> entitiesBatch,
                                  List<IdMapping> idMappingsBatch, List<TransactionLog> txBatch) {
        if (!entitiesBatch.isEmpty()) {
            log.info("Flushing entity batch before updateSyncariRecords action. Batch size: {}", entitiesBatch.size());
            entityRepo.saveEntityBatch(syncariEntityDef, entitiesBatch, idMappingsBatch);
        }

        if (!txBatch.isEmpty()) {
            log.info("Flushing transaction logs before updateSyncariRecords action. Batch size: {}", txBatch.size());
            var savedTransactions = transactionLogService.log(txBatch);
            repoService.updateLastTransactionId(syncariEntityDef, savedTransactions, entitiesBatch);
            eventStore.insertTransactionLogs(txBatch);
            txBatch.clear();
        }

        entitiesBatch.clear();
        idMappingsBatch.clear();
    }
}

@Data
@AllArgsConstructor
class ResolvedReference{
    List<String> resolvedReferences;
    Set<String> unresolvedReferences;

    public boolean hasResolvedReferences(){
        return !resolvedReferences.isEmpty();
    }
    public boolean hasUnresolvedReferences(){
        return !unresolvedReferences.isEmpty();
    }

    public String getResolvedReference(){
        return hasResolvedReferences() ? resolvedReferences.get(0) : null;
    }

    public String getUnResolvedReference(){
        return hasUnresolvedReferences() ? unresolvedReferences.stream().findFirst().get() : null;
    }

}

@AllArgsConstructor
class IdMappingResult {
    CurrentBatch batch;
    EntityDefinition syncariEntityDef;
    Connector connector;
    Optional<IdMapping> existing;
    StagedBatchRecord record;
    IdMappingOperation operation;
}

enum IdMappingOperation {
    deleted,
    connect,
    disconnected,
    nochange
}