package com.syncari.viper.streams.stages;

import com.google.common.collect.Lists;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.QuotaExceededException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.DataTransformer;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.ChildType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.*;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.service.*;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.token.TokenHelper;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import com.syncari.viper.GraphRunner;
import com.syncari.viper.ViperContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.CollectionUtils.map;
import static com.syncari.utils.I18n.i18n;
import static com.syncari.viper.ViperUtil.withPipelineException;


@Component
@Slf4j
public class SaveToSink {

    private static final int MAX_RECORDS_WRITTEN_PER_CYCLE = Integer.MAX_VALUE;

    private static final int MAX_TNX_ERRORS = 1000;
    protected IdMappingService idMappingService;
    protected SchemaService schemaService;
    protected PipelineEvaluator evaluator;
    protected EntityRepo entityRepo;
    protected AttributeRepo attributeProxyRepo;
    protected MappingGraphService graphService;
    protected DataServiceFactory dataServiceFactory;
    protected ConnectorService connectorService;
    protected TransactionLogService transactionLogService;
    protected SyncDetailMetricService syncDetailMetricService;
    protected FeatureService featureService;
    protected EntityRepoService repoService;

    @Autowired
    GCSFileManager storage;

    protected UnresolvedRecordService unresolvedRecordService;

    protected EventStore eventStore;
    protected WatermarkService watermarkService;

    protected DataTransformer dataTransformer;

    protected StagedBatchRecordRepo stagedBatchRecordRepo;

    protected TokenHelper tokenHelper;

    protected Publisher publisher;

    protected PipelineUtil pipelineUtil;

    protected static final String SYNCARI_ID = "syncariId";
    private static final int METRIC_BATCH_SIZE = 100;

    private static final Set<MappingNodeType> ACTION_TERMINALS = Set.of(
            MappingNodeType.ATTRIBUTE_SINK,
            MappingNodeType.ENTITY_SINK);

    public SaveToSink(){
    }
    @Autowired
    public SaveToSink(
            IdMappingService idMappingService,
            SchemaService schemaService,
            PipelineEvaluator evaluator,
            EntityRepo entityRepo,
            AttributeRepo attributeProxyRepo,
            MappingGraphService graphService,
            DataServiceFactory dataServiceFactory,
            ConnectorService connectorService,
            TransactionLogService transactionLogService,
            UnresolvedRecordService unresolvedRecordService,
            EventStore eventStore,
            WatermarkService watermarkService,
            DataTransformer dataTransformer,
            StagedBatchRecordRepo stagedBatchRecordRepo,
            TokenHelper tokenHelper,
            SyncDetailMetricService syncDetailMetricService,
            FeatureService featureService,
            EntityRepoService repoService,
            Publisher publisher,
            PipelineUtil pipelineUtil
    ) {

        this.idMappingService = idMappingService;
        this.schemaService = schemaService;
        this.evaluator = evaluator;
        this.entityRepo = entityRepo;
        this.attributeProxyRepo = attributeProxyRepo;
        this.graphService = graphService;
        this.dataServiceFactory = dataServiceFactory;
        this.connectorService = connectorService;
        this.transactionLogService = transactionLogService;
        this.unresolvedRecordService = unresolvedRecordService;
        this.eventStore = eventStore;
        this.watermarkService = watermarkService;
        this.dataTransformer = dataTransformer;
        this.stagedBatchRecordRepo = stagedBatchRecordRepo;
        this.tokenHelper = tokenHelper;
        this.syncDetailMetricService = syncDetailMetricService;
        this.featureService = featureService;
        this.repoService = repoService;
        this.publisher = publisher;
        this.pipelineUtil = pipelineUtil;
    }

    public void finishPipelineMetric(ViperContext context, GraphContext graphContext){
        var batch = graphContext.getCurrentBatch();
        String currentBatchId = batch.getCurrentBatchId();
        var entityGraph = graphContext.getGraph();
        var coreNode = entityGraph.getCoreNode();
        var syncariEntityDefinition = schemaService.getEntity(coreNode.getConfiguration().getConfigMap().get("entityDefinition").toString());
        String syncarEntityId = syncariEntityDefinition.getId();
        long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
        syncDetailMetricService.updateSyncDetailMetric(syncarEntityId,null,EntitySynchStatusMetricSummary.Stage.FINISHED_PIPELINE_EXECUTION,currentBatchId,(float) totalDurationtillNow);
    }

    public CurrentBatch execute(EntityDefinition sink, ViperContext context, GraphContext graphContext) {
        //This hydrates attributes
        var sinkEntity = schemaService.getEntity(sink.getId());
        var batch = graphContext.getCurrentBatch();
        var entityGraph = graphContext.getGraph();
        log.info("Saving batch to {}", sink.getApiName());
        var connectorId = sink.getConnectorId();
        var externalEntityName = sink.getApiName();
        return execute(batch, context, connectorId, externalEntityName,entityGraph,sinkEntity,graphContext);
    }

    protected Optional<EntityDefinition> refreshSchema(EntityDefinition sink, Connector connector, MappingGraph graph, boolean isSimulationMode) {
        if (connector.isActive() && sink.isActive()) {
            String lockOwnerId = graph.getId() + "_" + sink.getId();
            // refresh entity only if it was not a source entity
            List<EntityDefinition> refreshedDestinations = graph.isSource(sink.getId()) || isSimulationMode ? List.of(sink)
                    : withPipelineException(() -> schemaService.refreshSynapseSchema(sink.getConnectorId(), sink, lockOwnerId), graph, sink, false);
            return refreshedDestinations.stream().filter(e -> e.getApiName().equals(sink.getApiName())).findFirst().or(() -> Optional.of(sink));
        }
        return Optional.empty();
    }

    private CurrentBatch execute(CurrentBatch batch, ViperContext context, String connectorId, String externalEntityName, MappingGraph entityGraph, EntityDefinition originalSink, GraphContext graphContext) {
        var connector = connectorService.get(connectorId);
        var syncariEntityDefinition = graphContext.getSyncariEntity();
        var entityName = batch.getSyncariEntityName();
        SyncDetail sinkSyncDetail = watermarkService.getOrCreateDownstreamWatermark(entityName, originalSink);
        if (sinkSyncDetail.getNextSyncAt() > System.currentTimeMillis()) {
            // skip the sink if nextRun is scheduled in future
            log.info("Skipping sink for entity {} as nextSyncAt is scheduled at {}", originalSink.getId(), sinkSyncDetail.getNextSyncAt());
            return batch;
        }

        Watermark downstreamWatermark = sinkSyncDetail.getWatermark();
        long latestTS = downstreamWatermark.getEnd();
        if (!(context.isTestMode() || context.isRealTimeMode() || context.isSimulationMode())) {
            final PageCursor pageCursor = new PageCursor(downstreamWatermark.getChangeStream(), PageDirection.next, 1);
            final List<EntityData> records = entityRepo.find(syncariEntityDefinition, Instant.ofEpochMilli(downstreamWatermark.getStart()), pageCursor);
            if (records.isEmpty()) {
                log.info("No records found for destination entity {},connector {} on or after watermark {}",
                        originalSink.getApiName(), originalSink.getConnectorId(), downstreamWatermark);
                return batch;
            }
        }

        final EntityDefinition sink = refreshSchema(originalSink, connector, entityGraph, context.isSimulationMode())
                .orElse(null);
        if (sink == null) {
            log.info("Destination entity {} ({}),connector {} ({}) was either deleted or deactivated",
                    originalSink.getApiName(), originalSink.getStatus(), connector.getName(), connector.getStatus());
            return batch;
        }

        Timer executeMethodCheck = new Timer(300000, "SaveToSink::execute", log);
        graphContext.loadSynapseConfigFromCache();


        var updateCounter = new AtomicInteger(0);
        var insertCounter = new AtomicInteger(0);
        var deleteCounter = new AtomicInteger(0);

        var sinkAttribMap = sink.getApiNameLowerCasedToAttributes();
        var entitySchema = dataTransformer.toEntitySchema(sink,connector);
        DataService service = dataServiceFactory.getDataService(connector.getMetadata());
        var connectorInfo = dataTransformer.toConnectorInfo(connector);
        var allReferences = schemaService.getReferringAttributes(syncariEntityDefinition);
        // newly upda
        List<IdMapping> potentialConnectedMappings = new ArrayList<>();
        // this looks like the above, but this used for bq as we are not updating transactions so we will create once all the updates are taken in memory.
        Map<String, TransactionLog> batchTransactions = new HashMap<>();

        List<MappingGraph> attributeGraphs = graphContext.cache("attributeGraphs_" + entityGraph.getId(), () -> {
            return graphContext.isSimulationMode()
                    ? graphContext.getTestContext().getAttributeGraphs()
                    : graphService.retrieveAttributeGraphsForEntityGraph(entityGraph.getId());
        });

        List<String> attributeDefIds = attributeGraphs.stream().map(g -> g.getTargetId()).collect(Collectors.toList());
        Map<String, AttributeDefinition> attributesMap = new HashMap<>();
        attributeDefIds.forEach(id -> {
            var a = graphContext.cache("attributes_" + id, () -> attributeProxyRepo.findById(id));
            a.ifPresent(attr -> attributesMap.put(id, attr));
        });

        Map<AttributeDefinition, MappingGraph> fieldDAGs = attributeGraphs.stream().collect(Collectors.toMap(g -> attributesMap.get(g.getTargetId()),g->g));
        Map<String, CoreAttributeNodeConfig> sinkToCoreConfigMap = getSinkToCoreConfigMap(attributeGraphs);
        log.debug("Found {} attribute graphs for entity graph {} & entity id {}",fieldDAGs.size(),entityGraph.getId(),entityGraph.getTargetId());
        Map<String, Integer> winnerToLoserCount = new HashMap<>();

        //Downstream watermarks allow us to recover and restart from failures
        BatchActionContext batchActionContext = new BatchActionContext();
        BatchActionContext attributeBatchActionContext = new BatchActionContext();
        graphContext.setBatchActionContext(batchActionContext);
        var sinkNode = entityGraph.getConnectedSinks().filter(s -> sink.getId().equals(s.getConfiguration().getConfigMap().get("entityDefinition"))).findFirst()
                .orElseThrow(()-> new SyncariValidationException("Could not find sink node in graph for connector {}, entity {}",connector.getName(),sink.getApiName()));
        var entitySinksideActions = entityGraph.retrieveSinksideActionsInGraph();
        EntitySyncStatusMetric syncStatusMetric = null;
        final EntitySinkNodeConfig configuration = sinkNode.getTypedConfiguration();
        boolean syncOnlyOnTxnLog = configuration.isSyncOnTxnLog();
        Date batchTime = batch.getEntityBatches().values().stream().filter(b -> b.getCreatedAt() != null).map(b -> b.getCreatedAt()).min(Date::compareTo).orElse(Date.from(Instant.now()));
        String downStreamWMChangeStream = downstreamWatermark.getChangeStream();
        if (!fieldDAGs.isEmpty()) {
            Map<String, EntityDefinition> externalEntityDefinitions = fetchReferenceEntityDefinitions(fieldDAGs, sink);
            //TODO: page it!
            //Handle merges
            // page direction previous to to do _id ascending sort
            PageCursor nextPage = new PageCursor("", PageDirection.previous, 500);
            Page<TransactionLog> logs = transactionLogService.findMergesByBatchId(batch.getCurrentBatchId(), batchTime,  nextPage);
            log.debug("Entity Graph: {}, Total Merge Logs: {}", entityGraph.getName(),logs.getRecords().size());
            Set<String> mergeParticipants = new HashSet<>();
            List<Pair<EntityData, MergeRequest>> mergeRequestPairs = new ArrayList<>();
            List<UnresolvedRecord> recordResolutions = new ArrayList<>();
            Map<String, MergeRequest> loserMergeMap = new HashMap<>();
            while (!logs.getRecords().isEmpty()) {
                log.info("Entity Graph: {} Found {} Merge Logs",entityGraph.getName(),logs.getRecords().size());
                //nextPage = nextPage.next();
                logs.getRecords().forEach(log -> {
                    MergeOperation mergeOperation = log.getMergeOperation();
                    var record = mergeOperation.getWinningRecord();
                    Optional<IdMapping> value = idMappingService.findExistingMapping(syncariEntityDefinition.getApiName(), record.getSyncariEntityId(), connectorId, sink.getId());
                    List<IdMapping> loserMappings = idMappingService.findBySyncariIds(entityName, map(mergeOperation.getLosingRecords(), loser -> loser.getSyncariEntityId()));
                    // record -> mapping
                    if (value.isEmpty() && !loserMappings.stream().anyMatch(loserMapping -> loserMapping.isMapped(connectorId, sink.getId()))) {
                        this.log.debug("Skip Merge for winner {},  both winner and loser are not present in the synapse {}", record.getSyncariEntityId(), connectorId);
                        return;
                    }
                    fixDatatypes(attributesMap, record);
                    graphContext.setCurrentSyncariId(record.getSyncariEntityId());
                    Optional<EntityData> transformedOptionalEntity = applySinkSidePipeline(graphContext, entityGraph, sinkNode, record, syncariEntityDefinition, batchActionContext, value, Optional.empty(), batchTransactions);
                    if(transformedOptionalEntity.isEmpty()){
                        this.log.info("Skipping merge for entity {}, mergeTransaction {}, winner {}", externalEntityName,log.getId(),record.getId());
                        return;
                    }

                    List<Record> externalRecords = createExternalEntitiesForGraphs(record, connector.getId(),
                            syncariEntityDefinition, graphContext, fieldDAGs, sink, value, Map.of(), Map.of(), attributeBatchActionContext, batchTransactions, Optional.empty());
                    externalRecords.forEach(recordPair ->{
                        var data = recordPair.getEntityData();
                        recordResolutions.add(recordPair.getRecordResolution());
                        data.removeSystemFields();
                        if(data.getId() == null){
                            //This record is from another synapse. This synapse does not know about the winner.
                            // We need to either create a new record,
                            // or update the existing latest loser, if one is present
                            Stream<IdMapping.Mapping> loserMapping = getLatestLoserIdMapping( sink, mergeOperation.getLosingRecords(), loserMappings);
                            List<EntityData> losers = loserMapping.map(
                                    l -> data.withId(l.getEntityId())
                            ).collect(Collectors.toList());
                        }
                        fixDatatypes(sinkAttribMap, data);
                        mergeParticipants.add(data.getId());
                        var mergeRequest = new MergeRequest(connectorInfo, entitySchema);
                        if(sinkNode.getTypedConfiguration().getConfigMap().containsKey("destinationParams")
                            && sinkNode.getTypedConfiguration().getConfigMap().get("destinationParams") != null) {
                            mergeRequest.setDestParams((Map<String, Object>) sinkNode.getTypedConfiguration().getConfigMap().get("destinationParams"));
                        }
                        mergeRequest.setWinner(data);
                        loserMappings.forEach(loser-> {
                            List<IdMapping.Mapping> loserMapping = loser.getMappings(connectorId, sink.getId());
                            loserMapping.forEach(l -> {
                                //Make sure the loser was not promoted to a winner from code above
                                if(!l.getEntityId().equals(data.getId())){
                                    var externalLoserRecord =new EntityData(externalEntityName)
                                            .setSyncariEntityId(loser.getSyncariId()).setId(l.getEntityId());
                                    mergeParticipants.add(externalLoserRecord.getId());
                                    mergeRequest.addLoser(externalLoserRecord);
                                }
                            });
                        });

                        mergeOperation.getLosingRecords().stream().forEach(loser -> {
                            if (loserMergeMap.containsKey(loser.getSyncariEntityId())) {
                                // loser was winner before in the batch
                                MergeRequest winningLoser = loserMergeMap.get(loser.getSyncariEntityId());
                                winningLoser.getLosers().stream().forEach(oldLoser -> {
                                    if (oldLoser.getId() != null) {
                                        mergeRequest.addLoser(oldLoser);
                                    }
                                });
                                mergeRequestPairs.removeIf(p -> p.getY().getWinner().getSyncariEntityId().equals(loser.getSyncariEntityId()));
                            }
                        });

                        loserMergeMap.put(mergeRequest.getWinner().getSyncariEntityId(), mergeRequest);
                        mergeRequestPairs.add(Pair.of(record, mergeRequest));
                    });
                });
                if(recordResolutions.size() >=100) {
                    processRecordResolutions(recordResolutions);
                    recordResolutions.clear();
                }
                logs = transactionLogService.findMergesByBatchId(batch.getCurrentBatchId(), batchTime, nextPage);
            }
            processRecordResolutions(recordResolutions);
            recordResolutions.clear();
            // Compute the max number of records per sync cycle if sync rate is set
            int recordsWrittenPerCycle = MAX_RECORDS_WRITTEN_PER_CYCLE;
            if (connector.getSetting().getSyncRate() > 0) {
                recordsWrittenPerCycle = (connector.getSetting().getSyncRate() * GraphRunner.POLLING_INTERVAL / 3600);
            }
            log.debug("Entity Graph: {}, recordsPerCycle: {}, connector: {}",entityGraph.getName(), recordsWrittenPerCycle, connector.getName());
            int pageSize = Math.min(500, recordsWrittenPerCycle);
            PageCursor page = new PageCursor(downStreamWMChangeStream, PageDirection.next, pageSize);
            //Handle entityData
            List<RecordWithIdMapping> entityData = null;//entityRepo.find(entityName, Instant.ofEpochMilli(downstreamWatermark.getStart()), page);
            List<SyncError> syncErrors = new ArrayList<>();
            int totalProcessed = 0;
            int totalWritten = 0;
            int totalUpdatesProcessed = 0;
            Long cudTimeTaken = 0l;
            Long epTimer = 0l;
            Long fpTimer = 0l;
            do {
                entityData = getRecordsWithIdMapping(syncariEntityDefinition, sink, downstreamWatermark, page, batch.getCurrentBatchId(), batchTime, context);
                Map<String, List<TransactionLog>> transactionsSinceLastSync = new HashMap<>();
                if (syncOnlyOnTxnLog) {
                    transactionsSinceLastSync = getTxnLogsBySyncariId(syncariEntityDefinition, entityData.stream().map(e -> e.entityData.getSyncariEntityId()).collect(Collectors.toList()), downstreamWatermark);
                }

                Map<String, IdMapping> resolvedFks = resolveFKs(entityData, fieldDAGs);
                page = extractCursor(entityData, page, context);
                downStreamWMChangeStream = page.getCursor();
                //for(List<String> syncariEntityIdPartition: syncariEntityIdPartitions) {
                //List<EntityData> entityData = entityRepo.findByIdsIn(entityName, syncariEntityIdPartition);
                List<Pair<EntityData, EntityData>> entityPair = new ArrayList<>();
                int totalEntitiesProcessed = 0;
                int skippedCount = 0;
                int totalFieldProcessed = 0;
                if (!entityData.isEmpty()) {
                    Pipeline pipeline = new Pipeline(entityGraph.getName(), entityGraph.getDraftStatus().name(), SyncariContext.getSyncariId());
                    var creates = new SyncRequest().Builder(connectorInfo, entitySchema, pipeline).setStorage(storage);
                    var updates = new SyncRequest().Builder(connectorInfo, entitySchema, pipeline).setStorage(storage);
                    var deletes = new SyncRequest().Builder(connectorInfo, entitySchema, pipeline).setStorage(storage);
                    Map<String, ExternalDeleteInfo> externalDeleteMap = new HashMap<>();

                    if (sinkNode.getTypedConfiguration().getConfigMap().containsKey("destinationParams")
                            && sinkNode.getTypedConfiguration().getConfigMap().get("destinationParams") != null) {
                        creates.setDestParams((Map<String, Object>) sinkNode.getTypedConfiguration().getConfigMap().get("destinationParams"));
                        updates.setDestParams((Map<String, Object>) sinkNode.getTypedConfiguration().getConfigMap().get("destinationParams"));
                        deletes.setDestParams((Map<String, Object>) sinkNode.getTypedConfiguration().getConfigMap().get("destinationParams"));
                    }

                    Map<String, List<TransactionLog>> latestTransactions = transactionLogService.findLatestTransactions(batch.getCurrentBatchId(), batchTime, map(entityData, e -> e.entityData.getSyncariEntityId()));

                    log.info("downstreamFindByWatermark found {} records starting at {}(changestream {}), for entity {} connector {}", entityData.size(),
                            downstreamWatermark.getStart(), downstreamWatermark.getChangeStream(), externalEntityName, connector.getName());
                    Map<String, TransactionLog> txLogBySyncariId = new HashMap<>();
                    Map<String,String> newExternalIds = new HashMap<>();
                    for (RecordWithIdMapping recordWithIdMapping : entityData) {
                        EntityData syncariEntity = recordWithIdMapping.entityData;
                        List<TransactionLog> transactionLogs = latestTransactions.getOrDefault(syncariEntity.getSyncariEntityId(), List.of());
                        var sourceTxLog = transactionLogs.stream()
                                .filter(t -> t.getId().equals(syncariEntity.getLastTransactionLogId()))
                                .findFirst();
                        sourceTxLog.ifPresent(tx -> txLogBySyncariId.put(syncariEntity.getSyncariEntityId(), tx));
                        Timer entityDataTimer = new Timer(300000, "SaveToSink::execute::applySinkSidePipeline", log);
                        graphContext.setCurrentSyncariId(syncariEntity.getSyncariEntityId());
                        var originalValues = new HashMap<>(syncariEntity.getValues());
                        //Compute latest TS before transformations, because pipelines may skip records altogether.
                        latestTS = Math.max(latestTS, syncariEntity.getSyncariTimestamp());

                        boolean createDisconnectedMapping = configuration.isCreateDisconnectedMapping();

                        final Optional<IdMapping> existing = recordWithIdMapping.idMapping;
                        //existing may not contain id mapping for the current destination.
                        boolean isDisconnected = existing.stream().anyMatch(e->e.isDisconnected(connectorId, sink.getId()));
                        if(isDisconnected){
                            if (!createDisconnectedMapping) {
                                log.info("Skipping sink logic for connector {}, entity {} and syncari record {} because idMapping is disconnected",
                                        connector.getName(),sink.getApiName(),syncariEntity.getSyncariEntityId());
                                continue;
                            }

                            var disconnectInBatch = transactionLogs.stream().anyMatch(t ->
                                    t.getOperation().equals(Operation.disconnect) && t.getSources().stream()
                                            .anyMatch(source -> source.getConnectorId().equals(connectorId) && source.getEntityDefinitionId().equals(sink.getId())));

                            // if source txn is from same connector, as disconnected mapping, skip as this the same cycle where we processed disconnect
                            if (disconnectInBatch) {
                                log.info("Skipping sink logic for connector {}, entity {} and syncari record {} because idMapping is disconnected and update is coming from same connector",
                                        connector.getName(),sink.getApiName(),syncariEntity.getSyncariEntityId());
                                continue;
                            }

                            // store the mapping
                            var disconnectedMapping = existing.stream().filter(e->e.isDisconnected(connectorId, sink.getId())).findFirst().get();
                            // create a mapping for txn log
                            // add empty placeholder for id, replace with id returned from destination
                            var connectedMapping = new IdMapping().setEntityName(disconnectedMapping.getEntityName()).setSyncariId(disconnectedMapping.getSyncariId()).addMapping(connectorId, "", sink.getId(), false);
                            potentialConnectedMappings.add(connectedMapping);
                        }

                        Optional<IdMapping> idMapping = existing.filter(e-> e.isMapped(connectorId, sink.getId()));

                        List<String> acceptsDeletesFrom = configuration.getAcceptsDeletesFrom();
                        final Boolean shouldDelete = idMapping.map(m -> m.hasDisconnectedRecord(acceptsDeletesFrom)).orElse(false);
                        boolean isDeleted = syncariEntity.isDeleted() || shouldDelete;
                        //mark for record deletion if this record was disconnected from one of the accepted delete sources for this destination
                        syncariEntity.setDeleted(isDeleted);
                        if (isDeleted) {
                            findDisconnectedSources(syncariEntity, idMapping, acceptsDeletesFrom, graphContext, externalDeleteMap);
                        }

                        // sink side EP execution
                        Optional<EntityData> transformedOptionalEntity = applySinkSidePipeline(graphContext, entityGraph, sinkNode, syncariEntity, syncariEntityDefinition,
                                batchActionContext, idMapping, sourceTxLog, batchTransactions);
                        totalEntitiesProcessed++;
                        log.debug("Sink Side EP Applied to {}:{}",syncariEntity.getName(),syncariEntity.getId());

                        epTimer += entityDataTimer.getTimeTakenUntilNow();
                        entityDataTimer.close();
                        if (totalEntitiesProcessed >= METRIC_BATCH_SIZE){
                            syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(), externalEntityName,Instant.now(),
                                    (float)epTimer,
                                    totalEntitiesProcessed, skippedCount,0, deleteCounter.get(), insertCounter.get(), mergeRequestPairs.size(), updateCounter.get(), ChronoUnit.MILLIS);
                            long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
                            syncDetailMetricService.updateSyncDetailMetric(syncariEntityDefinition.getId(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_ENTITY_PIPELINE, batch.getCurrentBatchId(), (float)totalDurationtillNow);
                            totalEntitiesProcessed = 0;
                            epTimer = 0l; // reset the timer after every metric update
                        }
                        if (transformedOptionalEntity.isEmpty()) {
                            log.info("Skipping entity {},id {} on connector {} due to filter", syncariEntityDefinition.getApiName(), syncariEntity.getSyncariEntityId(), connector.getName());
                            String skippedReason =  String.format(i18n("transaction_log_destination_skipped_filter"), sink.getDisplayName(), connector.getName());
                            continue;
                        }
                        var transformedEntity = transformedOptionalEntity.get();
                        log.debug("Original values - {}, transformed values - {}", originalValues, transformedEntity.getValues());
                        Set<String> attributesWithSinkSideChanges = diff(originalValues, transformedEntity.getValues());
                        Timer fpDataTimer = new Timer(300000, "SaveToSink::execute::createExternalEntitiesForGraphs", log);
                        List<Record> externalRecords = createExternalEntitiesForGraphs(transformedEntity, connector.getId(), syncariEntityDefinition,
                                graphContext, fieldDAGs, sink, idMapping, resolvedFks, externalEntityDefinitions, attributeBatchActionContext, batchTransactions, sourceTxLog);
                        for(Record recordPair : externalRecords) {
                            EntityData data = recordPair.getEntityData();
                            recordResolutions.add(recordPair.getRecordResolution());
                            boolean skipMergeParticipants = data.getId() != null && ((data.isDeleted() && mergeParticipants.contains(data.getId())) ||
                                    (data.isNew() && mergeParticipants.contains(data.getId())));

                            if (skipMergeParticipants) {
                                //Skip merge winners/losers
                                log.debug("Skipping syncari id {} as it was merge participant", data.getSyncariEntityId());
                                continue;
                            }
                            // diff between original and transformed entity
                            boolean sinkPipelineChanges = !attributesWithSinkSideChanges.isEmpty() || (boolean)graphContext.getOrDefault("sinkField_mutated", false);
                            fixDatatypes(sinkAttribMap, data);
                            //Keep a copy of all values for syanpse that need the full record even on updates
                            Map<String, Object> originalDataValues = new HashMap<>(data.getValues());

                            //Skip records participating in merge
                            //if(mergeParticipants.contains(data.getId())) continue;
                            log.debug("Found candidate changes for entity {} with externalId {}, syncariId {}, changes {}, Txn {}", data.getName(),
                                    data.getId(), data.getSyncariEntityId(), data.getValues(), transactionLogs);

                            Optional<StagedBatchRecord> externalRecord = data.getId() != null ? batch.findExternalRecord(sink, data.getId()) : Optional.empty();
                            removeUnchangedFields(sinkAttribMap, entitySchema, data, externalRecord, sinkToCoreConfigMap);
                            final List<TransactionLog> transactionsSinceLastSyncForRecord = transactionsSinceLastSync.getOrDefault(data.getSyncariEntityId(), List.of());
                            filterTransactionFields(data, syncOnlyOnTxnLog, transactionsSinceLastSyncForRecord, graphContext, entitySchema);
                            //Records that are already marked as deleted in external system
                            // Or records marked as deleted in Syncari and there is no corresponding record in external system,
                            //missing externalId
                            // do not need to be deleted
                            var shouldSkipDeletes = (data.isDeleted() && data.getId() == null) || externalRecord.map(r -> (r.getEntityData().isDeleted() && data.isDeleted())).orElse(false);
                            boolean sourceHasChanges = transactionLogs.isEmpty() && !externalRecord.isEmpty() && !data.getValues().isEmpty();
                            //account for txns that were generated in previous cycle, but records were not processed.
                            boolean normalPipelineChanges = (!transactionLogs.isEmpty() || !transactionsSinceLastSyncForRecord.isEmpty()) && !data.getValues().isEmpty();
                            boolean newRecord = externalRecord.isEmpty() && StringUtils.isEmpty(data.getId()) && !data.getValues().isEmpty();

                            boolean nonPipelineChanges = false;
                            // changes not from this pipeline (Create Syncari Record/Update Syncari Record and Data Studio Update)
                            // compute non pipeline changes only if other conditions align, this is slight duplication of logic computing fullUpdate
                            if (externalRecord.isEmpty() && !StringUtils.isEmpty(data.getId()) && !data.getValues().isEmpty() && !sinkPipelineChanges) {
                                Optional<TransactionLog> lastTransaction = transactionLogs.stream()
                                    .filter(t -> t.getId().equals(data.getLastTransactionLogId()))
                                    .findFirst()
                                    .or(() -> data.getLastTransactionLogId() == null ? Optional.empty() :
                                            transactionLogService.findByTransactionLogId(data.getLastTransactionLogId(), data.getLastTransactionTimestamp()));
                                    nonPipelineChanges = transactionLogs.isEmpty() && lastTransaction.isPresent() && StringUtils.isEmpty(lastTransaction.get().getBatchId());
                            }
                            boolean fullUpdate = externalRecord.isEmpty() && !StringUtils.isEmpty(data.getId()) && !data.getValues().isEmpty() && (nonPipelineChanges || sinkPipelineChanges);

                            log.debug("attributesWithSinkSideChanges - {}, newRecord - {}, fullUpdate - {}, sourceHasChanges - {}, nonPipelineChanges - {}, sinkPipelineChanges - {}",
                                    attributesWithSinkSideChanges, newRecord, fullUpdate, sourceHasChanges, nonPipelineChanges, sinkPipelineChanges);
                            boolean hasChanges =  !attributesWithSinkSideChanges.isEmpty() || newRecord || fullUpdate || sourceHasChanges || normalPipelineChanges || (!shouldSkipDeletes && data.isDeleted());
                            //transactionLog == null && externalRecord.isEmpty()  -> no txn, no change from this synapse. skip
                            //transactionLog == null && !externalRecord.isEmpty() -> no txn generated, but some change came thru this synapse - drive thru data
                            //externalRecord.isEmpty()  && data.getId() is null ->  no incoming record from this synapse, but no idmapping. So creating record
                            //no transaction, no source, but we see changes - its either a replay or sources are different from destinations
                            log.info("Change Detection Flags for entity:{},externalId:{}, syncariId:{} - attributesWithSinkSideChanges.isEmpty:{}," +
                                            "newRecord:{},fullUpdate:{},sourceHasChanges:{},normalPipelineChanges:{},shouldSkipDeletes:{},isDeleted:{}",
                                    data.getName(), data.getId(), data.getSyncariEntityId(),
                                    attributesWithSinkSideChanges.isEmpty(), newRecord, fullUpdate, sourceHasChanges, normalPipelineChanges, shouldSkipDeletes, data.isDeleted()
                            );
                            log.info("Found changes -entity:{},externalId:{}, syncariId:{}, syncariTS:{}, numChangedFields: {}, hasChanges: {}," +
                                            "sourceStageBatchRecordId: {}, currentCycleTxnIds:{}, previousTxnIds:{}", data.getName(),
                                    data.getId(), data.getSyncariEntityId(), data.getSyncariTimestamp(), data.getValues().size(), hasChanges,
                                    externalRecord.map(e -> e.getId()).orElse(null),
                                    transactionLogs.stream().map(t -> t.getId()).collect(Collectors.toList()),
                                    transactionsSinceLastSyncForRecord.stream().map(t -> t.getId()).collect(Collectors.toList())
                            );

                            if (hasChanges) {
                                log.debug("Found changes for entity {} with externalId {}, syncariId {} Changes {}", data.getName(), data.getId(),
                                        data.getSyncariEntityId(), data.getValues());
                                graphContext.captureTestOutputForSinkEntityNode(new FunctionResult(data, ObjectType.VALUE), sinkNode);
                                if (data.isDeleted()) {
                                    deleteCounter.incrementAndGet();
                                    deletes.addData(connector.getId(), data.removeSystemFields());
                                    entityPair.add(Pair.of(syncariEntity, data));
                                } else if(!data.getValues().isEmpty()) {
                                    //update/insert only if there are values
                                    if (data.isNew()) {
                                        insertCounter.incrementAndGet();
                                        creates.addData(connector.getId(), data.removeSystemFields());
                                        entityPair.add(Pair.of(syncariEntity, data));
                                    } else {
                                        updateCounter.incrementAndGet();
                                        String insertOption = (String) updates.getDestParams().getOrDefault(Constants.BQ_INSERT_OPTION, null);
                                        if (Constants.BQ_FULL_RECORD_TO_INSERT_OPTION.equalsIgnoreCase(insertOption)) {
                                            data.setValues(originalDataValues);
                                        } else {
                                            removeNonUpdateableFields(entitySchema, data);
                                        }

                                        if (!data.getValues().isEmpty()) {
                                            updates.addData(connector.getId(), data);
                                            entityPair.add(Pair.of(syncariEntity, data));
                                        }
                                    }
                                }
                                totalWritten++;

                            } else {
                                // log skipped
                                String skippedReason =  String.format(i18n("transaction_log_destination_skipped_nochange"), sink.getDisplayName(), connector.getName());
                                log.info("saveToSink skipping record {}:{} for entity {} connector {}. No changes found", data.getId(), data.getSyncariEntityId(), externalEntityName, connector.getName());
                                skippedCount++;
                            }
                        }
                        fpTimer += fpDataTimer.getTimeTakenUntilNow();
                        fpDataTimer.close();

                        totalFieldProcessed++;
                        if (totalFieldProcessed >= METRIC_BATCH_SIZE){
                            syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(), externalEntityName,Instant.now(),
                                    (float)fpTimer,
                                    totalFieldProcessed, 0,0, deleteCounter.get(), insertCounter.get(), mergeRequestPairs.size(), updateCounter.get(), ChronoUnit.MILLIS);
                            long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
                            syncDetailMetricService.updateSyncDetailMetric(syncariEntityDefinition.getId(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_FIELD_PIPELINE, batch.getCurrentBatchId(), (float)totalDurationtillNow);
                            totalFieldProcessed = 0;
                            fpTimer = 0l; // reset the timer after every metric update
                        }
                    }
                    if (totalEntitiesProcessed > 0){
                        syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(), externalEntityName,Instant.now(),
                                (float)epTimer,
                                totalEntitiesProcessed, skippedCount,0, deleteCounter.get(), insertCounter.get(), mergeRequestPairs.size(), updateCounter.get(), ChronoUnit.MILLIS);
                        long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
                        syncDetailMetricService.updateSyncDetailMetric(syncariEntityDefinition.getId(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_ENTITY_PIPELINE, batch.getCurrentBatchId(), (float)totalDurationtillNow);
                        epTimer = 0l; // reset the timer after every metric update
                    }
                    if (totalFieldProcessed > 0){
                        syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(), externalEntityName,Instant.now(),
                                (float)fpTimer,
                                totalFieldProcessed, 0,0, deleteCounter.get(), insertCounter.get(), mergeRequestPairs.size(), updateCounter.get(), ChronoUnit.MILLIS);
                        long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
                        syncDetailMetricService.updateSyncDetailMetric(syncariEntityDefinition.getId(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_FIELD_PIPELINE, batch.getCurrentBatchId(),(float)totalDurationtillNow);
                        fpTimer = 0l; // reset the timer after every metric update
                    }
                    Map<String, Operation> operationMap = new HashMap<>();
                    Timer destWriteTimer = new Timer(500000, "SaveToSink::execute::destinationWriter", log);
                    if (!creates.getData().isEmpty()) {
                        creates.getSyncariIds().forEach(e -> operationMap.put(e, Operation.create));
                        log.info("Creating {} records for {}", insertCounter, externalEntityName);
                        try {
                            var createResponse = retryAuthFailures((syncRequest) -> service.create(syncRequest), creates);
                            List<IdMapping> mappings = idMappingService.saveIdMapping(syncariEntityDefinition, connector.getId(), createResponse, sink);
                            List<EntityData> recordsToBeUpdated = new ArrayList<>();
                            // for records created in destination, set the external id in Syncari
                            mappings.forEach(m -> {
                                EntityData d = new EntityData(syncariEntityDefinition.getApiName()).setSyncariEntityId(m.getSyncariId());
                                if(m.getMapping(sink.getId()).isPresent()) {
                                    String externalId = m.getMapping(sink.getId()).get().getEntityId();
                                    repoService.connectExternalId(syncariEntityDefinition, d, sink.getId(), Optional.empty(), externalId);
                                    newExternalIds.put(d.getSyncariEntityId(), externalId);
                                    recordsToBeUpdated.add(d);
                                }
                            });
                            entityRepo.updateValues(syncariEntityDefinition, recordsToBeUpdated);
                            updateReferringEntities(syncariEntityDefinition, createResponse);
                            if (!createResponse.isSuccess()) {
                                log.info("Create response for  {} :  {}", externalEntityName, createResponse);
                                syncErrors.addAll(createErrorLog(batch, sink, connector, creates, Optional.ofNullable(createResponse), null, Operation.create.name(), null));
                            }
                            logDestinationTxns(sink, syncariEntityDefinition, connector, creates.getData(), graphContext, Optional.ofNullable(createResponse),
                                    batch.getCurrentBatchId(), Operation.external_create, txLogBySyncariId, batchTransactions);
                            logMappingsTxn(syncariEntityDefinition, connector, Optional.ofNullable(createResponse), potentialConnectedMappings);
                            log.debug("Create response for  {} :  {}", externalEntityName, createResponse);
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                            List<SyncError> errors = createErrorLog(batch, sink, connector, creates, Optional.empty(), e, Operation.create.name(), null);
                            syncErrors.addAll(errors);
                            log.error("Skipping creates for entity {}, connector {}", externalEntityName, connector.getName());
                            shouldSkipWatermarkUpdate(context, e);
                        }
                    }

                    if (!updates.getData().isEmpty()) {
                        updates.getSyncariIds().forEach(e -> operationMap.put(e, Operation.update));
                        log.info("Updating {} records for {}", updateCounter, externalEntityName);
                        try {
                            var updateResponse = retryAuthFailures((syncRequest) -> service.update(syncRequest), updates);
                            if (!updateResponse.isSuccess()) {
                                List<Result> recordsNotFound = updateResponse.getResults().stream().filter(r -> !r.isSuccess()
                                        && ErrorCodes.DATA_NOT_FOUND.name().equals(r.getErrorCode())).collect(Collectors.toList());
                                if (!recordsNotFound.isEmpty()) {
                                    log.info("Update records not found, remove Syncari mapping {}", externalEntityName);
                                    deleteIdMappingAndUpdateExternalIds(entityName, connector.getId(), recordsNotFound, sink, syncariEntityDefinition);
                                    logDeleteTxns(syncariEntityDefinition, batch, recordsNotFound, externalDeleteMap, connector, sink);
                                }
                                log.info("Update response for  {} :  {}", externalEntityName, updateResponse);
                                syncErrors.addAll(createErrorLog(batch, sink, connector, updates, Optional.ofNullable(updateResponse), null, Operation.update.name(), null));
                            } {
                                logDestinationTxns(sink, syncariEntityDefinition, connector, updates.getData(), graphContext, Optional.ofNullable(updateResponse),
                                        batch.getCurrentBatchId(), Operation.external_update, txLogBySyncariId, batchTransactions);
                            }
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                            List<SyncError> errors = createErrorLog(batch, sink, connector, updates, Optional.empty(), e, Operation.update.name(), null);
                            syncErrors.addAll(errors);
                            log.error("Skipping updates for entity {}, connector {}", externalEntityName, connector.getName());
                            shouldSkipWatermarkUpdate(context, e);
                        }
                    }
                    if (!deletes.getData().isEmpty()) {
                        deletes.getSyncariIds().forEach(e -> operationMap.put(e, Operation.delete));
                        log.info("Deleting {} records for {}", deleteCounter, externalEntityName);
                        try {
                            var deleteResponse = retryAuthFailures((syncRequest) -> service.delete(syncRequest), deletes);
                            List<EntityData> recordsToBeUpdated = new ArrayList<>();
                            List<IdMapping> mappings = deleteIdMapping(entityName, connector.getId(), deleteResponse, sink);
                            // for records deleted in destination, clear the external id in Syncari
                            mappings.forEach(m -> {
                                EntityData d = new EntityData(syncariEntityDefinition.getApiName()).setSyncariEntityId(m.getSyncariId());
                                if(m.findDisconnected(sink.getId()).isPresent()) {
                                    repoService.disconnectExternalId(syncariEntityDefinition, d, sink.getId(), Optional.empty(), Optional.empty());
                                    recordsToBeUpdated.add(d);
                                }
                            });
                            entityRepo.updateValues(syncariEntityDefinition, recordsToBeUpdated);
                            if (deleteResponse != null && !deleteResponse.isSuccess()) {
                                syncErrors.addAll(createErrorLog(batch, sink, connector, creates, Optional.ofNullable(deleteResponse), null, Operation.delete.name(), null));
                            }
                            List<Result> results = deleteResponse.getResults().stream().filter(v -> v.isSuccess()).collect(Collectors.toList());
                            logDeleteTxns(syncariEntityDefinition, batch, results, externalDeleteMap, connector, sink);
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                            List<SyncError> errors = createErrorLog(batch, sink, connector, deletes, Optional.empty(), e, Operation.delete.name(), null);
                            syncErrors.addAll(errors);
                            log.error("Skipping deletes for entity {}, connector {}", externalEntityName, connector.getName());
                            shouldSkipWatermarkUpdate(context, e);
                        }
                    }
                    log.info("Created {} records and updated {} and deleted {} records for {} in synapse {}", insertCounter, updateCounter, deleteCounter, externalEntityName, connector.getName());
                    totalProcessed += entityData.size();
                    var syncErrorMap = syncErrors.stream().collect(Collectors.toMap(s -> s.getSyncariRecordId(), s-> s, ((s1, s2) -> s2)));

                    //All post destination actions executed here
                    Lists.partition(entityPair, 500).forEach(entityPairPartition -> {
                        // load the external records
                        List<String> syncariIds = entityPairPartition.stream().map(p -> p.x.getSyncariEntityId()).collect(Collectors.toList());
                        List<StagedBatchRecord> records = stagedBatchRecordRepo
                                .findByStagedBatchIdAndSyncariIdsAndEntity(graphContext.getCurrentBatch().getCurrentBatchId(), syncariIds, sink.getId());
                        Map<String, StagedBatchRecord> recordMap = records.stream().collect(Collectors.toMap(r -> r.getSyncariId(), r -> r, (r1, r2) -> r2));

                        entityPairPartition.forEach(pair -> {
                            // set common context stuff here
                            final GraphContext actionContext = graphContext.copy();
                            // copy error explicitly
                            actionContext.setErrors(graphContext.getErrors());
                            actionContext.set("destination_operation", operationMap.get(pair.x.getSyncariEntityId()));
                            Optional.ofNullable(syncErrorMap.get(pair.x.getSyncariEntityId())).ifPresentOrElse(error -> {
                                actionContext.set("destination_status", false);
                                actionContext.set("destination_error", error.getErrorCode());
                            },() -> {
                                actionContext.set("destination_status", true);
                            });
                            EntityData externalRecord = pair.y;
                            if (StringUtils.isEmpty(externalRecord.getId())) {
                                externalRecord.setId(newExternalIds.get(pair.x.getSyncariEntityId()));
                            }
                            StagedBatchRecord stagedRecord = recordMap.get(pair.x.getSyncariEntityId());
                            if (stagedRecord != null) {
                                actionContext.set("incoming_record", stagedRecord.getEntityData());
                            }

                            applyPostDestinationEntityPipeline(connector, entityGraph, pair.x, syncariEntityDefinition, externalRecord, sink, actionContext, batchActionContext);

                            applyPostDestinationFieldPipelines(connector, fieldDAGs, pair.x, externalRecord, syncariEntityDefinition, sink, actionContext, attributeBatchActionContext);
                            });
                        });
                }
                //flush every 100
                if (recordResolutions.size() >= 100) {
                    processRecordResolutions(recordResolutions);
                    recordResolutions.clear();
                }

                //flush transaction logs
                if (batchTransactions.size() > 0) {
                    transactionLogService.log(new ArrayList<>(batchTransactions.values()));
                }
                batchTransactions.clear();
                log.info("Records processed and written in this cycle {} {}", totalProcessed, totalWritten);
            } while (entityData != null && !StringUtils.isBlank(page.getCursor()) && totalWritten < recordsWrittenPerCycle);
            processRecordResolutions(recordResolutions);
            recordResolutions.clear();

            //Apply merges
            Timer destMergeTimer = new Timer(500000, "SaveToSink::execute::destinationMergeOps", log);

            Map<String, IdMapping> idMappingsFromMerge = new HashMap();
            int mergeCount = 0;

            List<MergeRequest> mergeRequests = mergeRequestPairs.stream().map(Pair::getY).collect(Collectors.toList());

            if(!mergeRequests.isEmpty()) {
                try {
                    var refreshedConnectorInfo = dataTransformer.toConnectorInfo(connectorService.refreshAuthentication(connector));
                    mergeRequests.stream().forEach(mr -> mr.setConnector(refreshedConnectorInfo));
                    List<MergeResponse> mergeResponses = service.merge(mergeRequests);
                    mergeCount = mergeRequests.size();
                    for (int i = 0; i < mergeRequests.size(); i++) {
                        MergeRequest mergeRequest = mergeRequests.get(i);
                        MergeResponse merge = mergeResponses.get(i);
                        log.info("Merge Results {}", merge);
                        if (merge != null) {
                            if (merge.getWinnerResult() != null && !merge.getWinnerResult().isSuccess()) {
                                List<Result> recordsNotFound = merge.getWinnerResult().getResults().stream().filter(r -> !r.isSuccess()
                                        && ErrorCodes.DATA_NOT_FOUND.name().equals(r.getErrorCode())).collect(Collectors.toList());
                                if (!recordsNotFound.isEmpty()) {
                                    log.info("merge winner records not found, remove Syncari mapping {}", externalEntityName);
                                    deleteIdMappingAndUpdateExternalIds(entityName, connector.getId(), recordsNotFound, sink, syncariEntityDefinition);
                                    logDeleteTxns(syncariEntityDefinition, batch, recordsNotFound, new HashMap<>(), connector, sink);
                                }
                                var error = new RuntimeException("Merge failed. Winner error: " + String.join("\n", merge.getWinnerResult().getErrors()));
                                syncErrors.addAll(createMergeErrorWinnerLog(batch, sink, connector, mergeRequest, Optional.ofNullable(merge), error, Operation.merge.name(), mergeRequest.getWinner().getSyncariEntityId()));
                            }
                            if (merge.getLoserResult() != null && !merge.getLoserResult().isSuccess()) {
                                var error = new RuntimeException("Merge failed. Loser error: " + String.join("\n", merge.getLoserResult().getErrors()));
                                syncErrors.addAll(createMergeErrorLoserLog(batch, sink, connector, mergeRequest, Optional.ofNullable(merge), error, Operation.merge.name(), mergeRequest.getWinner().getSyncariEntityId()));
                            }
                            if (merge.getWinnerResult() != null && merge.getWinnerResult().isSuccess()) {
                                merge.getWinnerResult().getResults().forEach(result -> {
                                    if (result.getSyncariId() != null && result.getId() != null) {

                                        // second check is to ensure that if response winner id and request winner id are different then update winner id in idMapping
                                        // (one case where this happens is in marketo when request winnner id has been deleted in Marketo and when we try to do update then Marketo responds
                                        // by creating a new record and sending that id back. This change ensure that we use returned id instead of deleted id.
                                        if ((mergeRequest.getLosers().isEmpty()) || !result.getId().equals(mergeRequest.getWinner().getId())) {
                                            String key = result.getSyncariId() + "_" + entityName + "_" + connectorId + "_" + result.getId() + "_" + sink.getId();
                                            if (!idMappingsFromMerge.containsKey(key)){
                                                idMappingsFromMerge.put(key, new IdMapping().setSyncariId(result.getSyncariId())
                                                        .setEntityName(entityName)
                                                        .addMapping(connectorId, result.getId(), sink.getId()));
                                            }
                                        }
                                    }
                                });
                            }
                            String winnerId = mergeRequest.getWinner().getId();
                            deleteLoserIdMapping(connectorId, sink, entityName, mergeRequest);

                            var count = winnerToLoserCount.getOrDefault(winnerId, 0);
                            winnerToLoserCount.put(winnerId, count + mergeRequest.getLosers().size());
                        }
                    }
                } catch (Exception e) {
                    SyncError mergeError = SyncError.builder().connectorName(connector.getName()).connectorId(connector.getId())
                            .operation(Operation.merge.name()).batchId(batch.getCurrentBatchId())
                            .syncariEntityName(batch.getSyncariEntityName())
                            .externalEntityName(sink.getApiName()).errorCode(e.getMessage())
                            .errorDetails(toStrackTraceString(e))
                            .occuredTime(Instant.now()).build();
                    syncErrors.add(mergeError);
                    log.error(e.getMessage(), e);
                    log.error("Skipped some merges from {}, connector {}", mergeRequests, connector.getName());
                }

                idMappingService.upsert(new ArrayList<>(idMappingsFromMerge.values()));
                var syncErrorMap = syncErrors.stream().collect(Collectors.toMap(s -> s.getSyncariRecordId(), s-> s, ((s1, s2) -> s2)));
                mergeRequestPairs.stream().forEach(pair -> {
                    // set common context stuff here
                    final GraphContext actionContext = graphContext.copy();
                    // copy error explicitly
                    actionContext.setErrors(graphContext.getErrors());
                    actionContext.set("destination_operation", Operation.merge);
                    Optional.ofNullable(syncErrorMap.get(pair.x.getSyncariEntityId())).ifPresentOrElse(error -> {
                        actionContext.set("destination_status", false);
                        actionContext.set("destination_error", error.getErrorCode());
                    },() -> {
                        actionContext.set("destination_status", true);
                    });
                    EntityData winner = pair.y.getWinner();
                    actionContext.set("loserRecords", pair.y.getLosers());
                    applyPostDestinationEntityPipeline(connector, entityGraph, pair.x, syncariEntityDefinition, winner, sink, actionContext, batchActionContext);
                    // applyPostDestinationFieldPipelines(connector, fieldDAGs, pair.x, externalRecord, syncariEntityDefinition, sink, actionContext, attributeBatchActionContext);
                    applyPostDestinationFieldPipelines(connector, fieldDAGs, pair.x, winner, syncariEntityDefinition, sink, actionContext, batchActionContext);
                });
            }
            totalUpdatesProcessed = updateCounter.get() + deleteCounter.get()+insertCounter.get() + mergeCount;
            cudTimeTaken  += destMergeTimer.getTimeTakenUntilNow();
            syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(), externalEntityName,Instant.now(),
                    (float)cudTimeTaken,
                    totalUpdatesProcessed, 0,0, deleteCounter.get(), insertCounter.get(), mergeRequestPairs.size(), updateCounter.get(), ChronoUnit.MILLIS);
            long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
            syncDetailMetricService.updateSyncDetailMetric(syncariEntityDefinition.getId(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.WRITING_DATA_TO_DESTINATION, batch.getCurrentBatchId(), (float)totalDurationtillNow);
            Integer totalDuplicates = winnerToLoserCount.values().stream().reduce((a, b) -> a + b).orElse(0) + winnerToLoserCount.size();
			if (graphContext.getTestContext() != null) {
				syncErrors.stream().forEach((syncError) -> {
					if (syncError.getSyncariRecordId() != null && sinkNode.getId() != null) {
						NodeData nd = graphContext.getTestContext().getNodeData(syncError.getSyncariRecordId(),
								sinkNode.getId());
						if (nd != null) {
							nd.setFailed(true);
						}
					}
				});
			}
            eventStore.insertErrorLogs(syncErrors);

            var syncErrorSummary = getSyncErrorMetrics(syncErrors, sinkNode.getId(), sink.getId(), insertCounter.get(), updateCounter.get(), deleteCounter.get());
            var nodeErrorSummary = pipelineUtil.getEntitySyncErrorMetrics(graphContext.getErrors(), graphContext.getNodeStatusMetrics());
            var errors = Stream.concat(syncErrorSummary, nodeErrorSummary).collect(Collectors.toList());
            syncDetailMetricService.updateSyncErrorMetric(syncariEntityDefinition.getId(), graphContext.getCurrentBatch().getCurrentBatchId(), errors);
        } else{
            log.warn("No field pipelines found for{}, graph id {}",entityName,entityGraph.getId());
        }

        runAttributeBatchActions(fieldDAGs, graphContext, attributeBatchActionContext);
        runBatchActions(entityGraph,graphContext, batchActionContext);
        graphContext.setBatchActionContext(null);
        //Have to keep moving the watermark unless there is an error. end of watermark does not matter
        //TODO: Dont update watermark if there are errors
        if(context.updateWatermark()) {
            watermarkService.updateWatermark(sink, entityName, downstreamWatermark.setStart(latestTS).setEnd(latestTS).setChangeStream(downStreamWMChangeStream));
        }
            executeMethodCheck.close();
        return batch;
    }

    private void logDeleteTxns(EntityDefinition syncariEntityDef, CurrentBatch batch, List<Result> results,
                               Map<String, ExternalDeleteInfo> externalDeleteMap, Connector connector, EntityDefinition sink) {



        Map<String, List<Result>> syncariIdToResult = results.stream()
                .filter(v -> !StringUtils.isEmpty(v.getSyncariId())).collect(Collectors.groupingBy(r -> r.getSyncariId()));


        List<TransactionLog> logs = syncariIdToResult.keySet().stream().map(syncariId -> {
            TransactionLog log = new TransactionLog();
            log.setSyncariId(syncariId);
            log.setEntityName(syncariEntityDef.getApiName());
            log.setEntityId(syncariEntityDef.getId()); // entityId
            log.setBatchId(batch.getCurrentBatchId()); // batchId
            log.setOccurredAt(Instant.now().toEpochMilli());
            log.setOperation(Operation.external_delete);
            var externalId = syncariIdToResult.get(syncariId).stream().map(r -> r.getId()).findFirst();
            var recordInfo = externalDeleteMap.getOrDefault(syncariId, new ExternalDeleteInfo());
            var deletedExternalId = new ExternalDeleteInfo.ExternalId();
            externalId.ifPresent(id ->deletedExternalId.setId(id).setApiName(sink.getApiName()).setDisplayName(sink.getDisplayName()).setConnectorId(connector.getId()).setConnectorName(connector.getName()));
            recordInfo.setDeletedId(deletedExternalId);
            log.setAdditionalInfo(Map.of("deleteInfo", recordInfo));
            return log;
        }).collect(Collectors.toList());
        transactionLogService.log(logs);
    }

    private void findDisconnectedSources(EntityData syncariEntity, Optional<IdMapping> idMapping, List<String> acceptsDeletesFrom, GraphContext graphContext, Map<String, ExternalDeleteInfo> externalDeleteMap) {
        var disconnectedSources = idMapping.map(m -> m.getDisconnectedMappings(acceptsDeletesFrom)).orElse(List.of());
        List<ExternalDeleteInfo.ExternalId> sourceIds = disconnectedSources.stream().map(mapping -> {
            final ExternalDeleteInfo.ExternalId sourceId = new ExternalDeleteInfo.ExternalId();
            Optional<EntityDefinition> sourceDefinitionOpt = graphContext.cache("entityId_" + mapping.getEntityId(), () -> schemaService.findEntity(mapping.getEntityDefinitionId()));
            sourceDefinitionOpt.ifPresent(def -> sourceId.setApiName(def.getApiName()).setDisplayName(def.getDisplayName()).setEntityId(def.getId()));
            sourceId.setId(mapping.getEntityId());

            Optional<Connector> connectorOpt = graphContext.cache("connectorId_" + mapping.getConnectorId(), () -> connectorService.find(mapping.getConnectorId(), false));
            connectorOpt.ifPresent(conn -> sourceId.setConnectorName(conn.getName()));
            sourceId.setConnectorId(mapping.getConnectorId());
            return sourceId;
        }).collect(Collectors.toList());
        ExternalDeleteInfo externalDelete = new ExternalDeleteInfo().setSyncariDeleted(syncariEntity.isDeleted())
                .setDisconnectedSources(sourceIds);
        externalDeleteMap.put(syncariEntity.getSyncariEntityId(), externalDelete);
    }

    protected Stream<EntitySyncErrorMetric> getSyncErrorMetrics(List<SyncError> syncErrors, String nodeId, String externalEntityId, int insertCount, int updateCount, int deleteCount) {

        return syncErrors.stream().collect(Collectors.toMap(error -> nodeId + "_" + error.getErrorDetails(), error -> {
            EntitySyncErrorMetric entitySyncErrorMetric = new EntitySyncErrorMetric();
            entitySyncErrorMetric.setErrorDetails(error.getErrorCode());
            entitySyncErrorMetric.setErrorMessage(error.getErrorCode());
            entitySyncErrorMetric.setNodeId(nodeId);
            entitySyncErrorMetric.setTargetId(externalEntityId);
            entitySyncErrorMetric.setScope(Scope.ENTITY);
            entitySyncErrorMetric.setErrorType(ErrorType.SYNC);
            if (Operation.create.name().equals(error.getOperation())) {
                entitySyncErrorMetric.setTotalCount(insertCount);
            } else if (Operation.update.name().equals(error.getOperation())) {
                entitySyncErrorMetric.setTotalCount(updateCount);
            } else if (Operation.delete.name().equals(error.getOperation())) {
                entitySyncErrorMetric.setTotalCount(deleteCount);
            }
            entitySyncErrorMetric.setErrorCount(1);
            return entitySyncErrorMetric;
        }, (error1, error2) -> error1.setErrorCount(error1.getErrorCount() + error2.getErrorCount()))).values().stream();
    }


    protected void filterTransactionFields(EntityData data, boolean syncOnlyOnTxnLog, List<TransactionLog> transactions, GraphContext context, EntitySchema entitySchema) {
        // if record is deleted, then no need to remove values
        if (syncOnlyOnTxnLog && !data.isDeleted()) {
            if (!transactions.isEmpty()) {
                Iterator<Map.Entry<String, Object>> iterator = data.getValues().entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Object> attrValue = iterator.next();
                    Optional.ofNullable((AttributeDefinition)context.get("sinkField_" + attrValue.getKey())).ifPresent(syncariField -> {
                        if(!transactions.stream().anyMatch(t -> t.hasChangeFor(syncariField.getId()))) {
                            // if the current field is id field then do not remove it.
                            if (!(entitySchema.hasIdField()
                                    && entitySchema.getIdField().getApiName().equals(attrValue.getKey())
                                    && attrValue.getValue() != null && StringUtils.isNotEmpty(attrValue.getValue().toString()))) {
                                iterator.remove();
                            }
                        }
                    });
                }
            } else {
                data.getValues().clear();
            }
        }
    }

    public void updateReferringEntities(EntityDefinition syncariEntityDefinition, SyncResponse createResponse) {

        if (featureService.isEnabled(Features.UpdateReferencesOnIdMappingChange, true)) {
            var syncariIds = createResponse.getResults().stream().filter(v -> v.isSuccess()).map(result -> result.getSyncariId()).collect(Collectors.toList());

            Map<String, Object> eventDetails = Map.of("entityId", syncariEntityDefinition.getId(),"syncariIds", syncariIds);

            publisher.publishToViperQueue(new Event().setType(EventTypes.UPDATE_FK_REFERENCES)
                    .setLoggedTime(new Date())
                    .setDetails(eventDetails));
        }
    }

    private void shouldSkipWatermarkUpdate(ViperContext context, Exception e) {
        if(e instanceof RetriableException || e instanceof QuotaExceededException) {
            log.info("Skipping sinkside watermark update because of RetriableException");
            context.setUpdateWatermark(false);
        }
    }

    private Map<String, EntityDefinition> fetchReferenceEntityDefinitions(Map<AttributeDefinition, MappingGraph> fieldDAGs, EntityDefinition sink) {
        Map<String, EntityDefinition> result = new HashMap<>();
        fieldDAGs.forEach((attr, dag) -> {
           dag.getConnectedSinks()
                   .map(n -> ((AttributeSinkNodeConfig) n.getConfiguration()))
                   .filter(nodeConfig -> nodeConfig.getAttributeDefinition().getEntityId().equals(sink.getId()) && nodeConfig.getAttributeDefinition().isReference()
                           && sink.hasField(nodeConfig.getAttributeDefinition().getApiName()))
                   .forEach(nodeConfig -> {
                        var sinkAttribute = sink.getAttribute(nodeConfig.getAttributeDefinition().getId());
                        var entityDefOpt = schemaService.getEntityByName(sink.getConnectorId(), sinkAttribute.getReferenceTo());
                        if(entityDefOpt.isPresent()) {
                            result.put(sink.getConnectorId() + "_" + sinkAttribute.getReferenceTo(), entityDefOpt.get());
                        }
                    });
        });
       return result;
    }

    private Map<String, IdMapping> resolveFKs(List<RecordWithIdMapping> entityData, Map<AttributeDefinition, MappingGraph> fieldDAGs) {
        String msg = "SaveToSink - resolveFKs";
        Timer resolveFKsCheck = new Timer(30000, msg, log);
        Map<String, Set<String>> referenceToMap = new HashMap<>();
        entityData.forEach(ed -> {
            fieldDAGs.keySet().stream().filter(AttributeDefinition::isReference).forEach(attributeDefinition -> {
                log.debug("attribute - {}, referenceTo - {}, value - {}", attributeDefinition.getApiName(), attributeDefinition.getReferenceTo(), ed.entityData.getValue(attributeDefinition.getApiName()));
                String referenceTo = attributeDefinition.getReferenceTo();
                Object attributeValue = ed.entityData.getValue(attributeDefinition.getApiName());
                if (attributeValue instanceof String) {
                    addToReferenceMap(referenceToMap, referenceTo, (String) attributeValue);
                } else if(attributeValue instanceof List){
                   List value = (List) attributeValue;
                   value.forEach(v -> addToReferenceMap(referenceToMap, referenceTo, (String) v));
                }
            });
        });

        Map<String, IdMapping> result = new HashMap<>();
        referenceToMap.forEach((reference, ids) -> {
            List<IdMapping> idMappingList = idMappingService.findBySyncariIds(reference, ids);
            idMappingList.forEach(idMapping -> {
                String key = reference + "#" + idMapping.getSyncariId();
                result.put(key, idMapping);
            });
        });

        resolveFKsCheck.close();
        log.debug("Successfully resolved foreign keys, reference_syncariid -> idmapping size - {}", result.size());
        return result;
    }

    private void addToReferenceMap(Map<String, Set<String>> referenceToMap, String referenceTo, String attributeValue) {
        String value = attributeValue;
        if(value != null) {
            if (referenceToMap.containsKey(referenceTo)) {
                referenceToMap.get(referenceTo).add(value);
            } else {
                referenceToMap.put(referenceTo, new HashSet<>(Arrays.asList(value)));
            }
        }
    }

    private SyncResponse retryAuthFailures(Function<SyncRequest, SyncResponse> op, SyncRequest syncRequest) {
        try {
             return op.apply(syncRequest);
        } catch(NonRetriableException e) {
            if (ErrorCodes.ACCESS_DENIED.name().equals(e.getErrorCode()) || ErrorCodes.TOKEN_EXPIRED.name().equals(e.getErrorCode())) {
                //try with refreshed connector
                syncRequest.setConnector(connectorService.find(syncRequest.getConnector().getId())
                        .map(dataTransformer::toConnectorInfo).orElse(syncRequest.getConnector()));
                return op.apply(syncRequest);
            } else {
                throw e;
            }
        }
    }

    private Map<String, CoreAttributeNodeConfig> getSinkToCoreConfigMap(List<MappingGraph> attributeGraphs) {
        Map<String, CoreAttributeNodeConfig> sinkToCoreConfigMap = new HashMap<>();
        attributeGraphs.forEach(graph -> {
            var coreNode = graph.getCoreNode();
            var sinks = graph.getConnectedSinks();
            sinks.forEach(sink -> {
                AttributeSinkNodeConfig sinkNodeConfig = sink.getTypedConfiguration();
                sinkToCoreConfigMap.put(sinkNodeConfig.getAttributeDefinition().getApiName(), coreNode.getTypedConfiguration());
            });
        });
        return sinkToCoreConfigMap;
    }

    private PageCursor extractCursor(List<RecordWithIdMapping> entityData, PageCursor page, ViperContext context) {
        if (entityData.isEmpty() || entityData.size() < page.getPageSize() || context.isTestMode())
            return new PageCursor("", PageDirection.next, page.getPageSize());
        EntityData lastRecord = entityData.get(entityData.size() - 1).entityData;
        String cursor = lastRecord.getSyncariTimestamp() + "_" + lastRecord.getSyncariEntityId();
        return new PageCursor(cursor, PageDirection.next, page.getPageSize());
    }

    private void logMappingsTxn(EntityDefinition syncariEntityDefinition, Connector connector, Optional<SyncResponse> response, List<IdMapping> potentialConnectedMappings) {
        Map<String, String> successIds = response.isPresent() ? response.get().getResults().stream().filter(Result::isSuccess).collect(Collectors.toMap(Result::getSyncariId, Result::getId, (first, second) -> second)) : Map.of();
        var connectedMappings = potentialConnectedMappings.stream().filter(idMapping -> successIds.containsKey(idMapping.getSyncariId())).collect(Collectors.toList());
        connectedMappings.forEach(mapping -> mapping.getMappings().get(0).setEntityId(successIds.get(mapping.getSyncariId())));

        var txnlogs = connectedMappings.stream().map(idMapping -> {
            var mapping = idMapping.getMappings().get(0);
            return new TransactionLog().setSyncariId(idMapping.getSyncariId()).setEntityName(syncariEntityDefinition.getApiName())
                    .setOperation(Operation.connect).setAdditionalInfo(Map.of("idMapping", idMapping)).addSource(connector.getId(), connector.getName(), mapping.getEntityDefinitionId(), mapping.getEntityId(), System.currentTimeMillis());
        }).collect(Collectors.toList());
        transactionLogService.log(txnlogs);
    }

    private void logDestinationTxns(EntityDefinition sinkEntityDefinition, EntityDefinition syncariEntityDef, Connector connector, Map<String, List<EntityData>> data, GraphContext context,
                                    Optional<SyncResponse> response, String batchId, Operation operation, Map<String, TransactionLog> txLogsBySyncariId, Map<String, TransactionLog> batchTransactions) {
        logDestinationOnlyTxns(sinkEntityDefinition, syncariEntityDef, connector, data, context, response, batchId, operation, batchTransactions, txLogsBySyncariId);
    }

    private TransactionLog getOrCreateTxn(Map<String, TransactionLog> batchTransactions, String syncariId, EntityDefinition syncariEntityDef,
                                          String batchId, Optional<String> lastTransactionId) {

        TransactionLog log = batchTransactions.get(syncariId);
        if (log == null) {
            log = new TransactionLog();
            log.setId(ObjectId.get().toHexString());
            log.setSyncariId(syncariId).setBatchId(batchId).setEntityId(syncariEntityDef.getId())
                    .setEntityName(syncariEntityDef.getApiName()).setNew(false).setOperation(Operation.external_update);
            lastTransactionId.ifPresent(log::setSourceTransactionId);
        }
        batchTransactions.put(syncariId, log);
        return log;
    }

    private void logDestinationOnlyTxns(EntityDefinition sinkEntityDefinition, EntityDefinition syncariEntityDef, Connector connector, Map<String, List<EntityData>> data, GraphContext context,
                                    Optional<SyncResponse> response, String batchId, Operation op, Map<String, TransactionLog> batchTransactions, Map<String, TransactionLog> txnLogsBySyncariId) {

        Map<String, String> externalIdMap = data.values().stream().flatMap(l -> l.stream()).filter(e -> e.getId() != null)
                .collect(Collectors.toMap(e -> e.getSyncariEntityId(), e -> e.getId(), (e1, e2) -> e2));
        Map<String, String> successIds = response.isPresent() ? response.get().getResults().stream()
                .filter(Result::isSuccess).collect(Collectors.toMap(r -> r.getSyncariId(), r -> r.getId() == null ? (externalIdMap.containsKey(r.getSyncariId()) ?
                        externalIdMap.get(r.getSyncariId()) : "") : r.getId(), (r1, r2) -> r1)) : Map.of();

        var entityData = data.entrySet().stream().flatMap(d -> d.getValue().stream());
        entityData.forEach(ed -> {
            if (successIds.containsKey(ed.getSyncariEntityId()) || (response.isPresent() && response.get().isSuccess())) {
                Optional<TransactionLog> lastTransaction = Optional.ofNullable(txnLogsBySyncariId.get(ed.getSyncariEntityId()));

                TransactionLog log = getOrCreateTxn(batchTransactions, ed.getSyncariEntityId(), syncariEntityDef, batchId, lastTransaction.map(TransactionLog::getId));
                log.setNew(false);
                //presence of id does not trigger createdAt/createdBy
                log.setCreatedAt(new Date());
                log.setCreatedBy(Optional.ofNullable(SyncariContext.getUser()).map(u->u.getId()).orElse(null));
                log.setOperation(op);

                // for each destination field, find syncari field. create field changes out of it
                // return list of field changes
                Map<String, FieldChange> fieldChanges = ed.getValues().entrySet().stream().map(e -> {
                    String destSinkField = e.getKey();
                    return Optional.ofNullable((AttributeDefinition) context.get("sinkField_" + destSinkField))
                            .flatMap(syncariField -> sinkEntityDefinition.getField(destSinkField).map(sinkAttribDef -> {
                                ExternalValue value = new ExternalValue();
                                value.setFieldId(sinkAttribDef.getId());
                                value.setApiName(sinkAttribDef.getApiName());
                                value.setDisplayName(sinkAttribDef.getDisplayName());
                                value.setDataType(sinkAttribDef.getDataType().getName());
                                value.setConnectorId(connector.getId());
                                value.setConnectorName(connector.getName());
                                value.setValue(maskValueIfDecrypted(context, sinkAttribDef.getId(), ed.getValue(sinkAttribDef.getApiName())));
                                FieldChange partialFieldChange = new FieldChange();
                                partialFieldChange.setFieldId(syncariField.getId());
                                partialFieldChange.setApiName(syncariField.getApiName());
                                partialFieldChange.setDisplayName(syncariField.getDisplayName());
                                partialFieldChange.setDataType(syncariField.getDataType().getName());
                                partialFieldChange.addOutgoingExternalValue(sinkAttribDef.getId(), value);
                                return partialFieldChange;
                            }));
                }).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(f->f.getFieldId(), f->f, (f1,f2) -> f2));
                log.setChanges(fieldChanges);
                batchTransactions.putIfAbsent(ed.getSyncariEntityId(), log);
            }}
        );
    }

    private void updateTxnsForDestination(EntityDefinition sinkEntityDefinition, Connector connector, Map<String, List<EntityData>> data, GraphContext context, Optional<SyncResponse> response, Map<String, TransactionLog> txLogsBySyncariId) {
        // Only log destination transactions that succeeded
        Set<String> successIds = response.isPresent() ? response.get().getResults().stream().filter(Result::isSuccess).map(Result::getSyncariId).collect(Collectors.toSet()) : new HashSet<>();

        var entityData = data.entrySet().stream().flatMap(d -> d.getValue().stream());
        entityData.forEach(ed -> {
            if (successIds.contains(ed.getSyncariEntityId()) || (response.isPresent() && response.get().isSuccess())) {
                Optional<TransactionLog> transactionLogOpt = Optional.ofNullable(txLogsBySyncariId.get(ed.getSyncariEntityId())).or(
                        () -> transactionLogService.findByTransactionLogId(ed.getLastTransactionLogId(), ed.getLastTransactionTimestamp()));
                transactionLogOpt.ifPresent(trxLog -> {
                    var fieldChangeList = ed.removeSystemFields().getValues().keySet().stream().map(destField -> {
                        return Optional.ofNullable((AttributeDefinition) context.get("sinkField_" + destField)).flatMap(syncariField -> {
                            return sinkEntityDefinition.getField(destField).map(sinkAttribDef -> {
                                ExternalValue value = new ExternalValue()
                                        .setFieldId(sinkAttribDef.getId())
                                        .setApiName(sinkAttribDef.getApiName())
                                        .setDisplayName(sinkAttribDef.getDisplayName())
                                        .setDataType(sinkAttribDef.getDataType().getName())
                                        .setConnectorId(connector.getId())
                                        .setConnectorName(connector.getName())
                                        .setValue(maskValueIfDecrypted(context, sinkAttribDef.getId(), ed.getValue(sinkAttribDef.getApiName())));
                                FieldChange partialFieldChange = new FieldChange()
                                        .setFieldId(syncariField.getId())
                                        .setApiName(syncariField.getApiName())
                                        .setDisplayName(syncariField.getDisplayName())
                                        .setDataType(syncariField.getDataType().getName())
                                        .addOutgoingExternalValue(sinkAttribDef.getId(), value);
                                return partialFieldChange;
                            });
                        });
                    }).flatMap(Optional::stream).collect(Collectors.toList());
                    transactionLogService.setExternalOutgoingValue(trxLog.getId(), fieldChangeList);
                });
            }
        });
    }

    private Object maskValueIfDecrypted(GraphContext context, String attributeId, Object value) {
        log.debug("{}_id encrypted: {}", attributeId, ((Set) context.get("decrypted_sink_attribute_ids")).contains(attributeId));
        return ((Set) context.get("decrypted_sink_attribute_ids")).contains(attributeId) ? "******" : value;
    }

    protected void removeUnchangedFields(Map<String, AttributeDefinition> sinkAttribMap, EntitySchema entitySchema, EntityData data,
                                         Optional<StagedBatchRecord> externalRecord, Map<String, CoreAttributeNodeConfig> sinkToCoreConfigMap) {
        removeIdAndAuditFields(entitySchema, data);
        //Remove unchanged fields
        externalRecord.ifPresent(r -> {
                    log.debug("External record {} with Syncari Id {} modified by pipeline {}", r.getExternalRecordId(), r.getSyncariId(), r.isModifiedByPipeline());
                    if (!r.isModifiedByPipeline()) {
                        var transformed = fixDatatypes(sinkAttribMap, r.getEntityData());
                        Iterator<Map.Entry<String, Object>> iterator = data.getValues().entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry<String, Object> attrValue = iterator.next();
                            CoreAttributeNodeConfig coreAttributeNodeConfig = sinkToCoreConfigMap.get(attrValue.getKey());
                            if (!transformed.hasChanges(attrValue.getKey(), attrValue.getValue(), coreAttributeNodeConfig.isRejectEmptyString())) {
                                iterator.remove();
                            }
                        }
                    }
                }
        );
    }

    protected void deleteLoserIdMapping(String connectorId, EntityDefinition sink, String entityName, MergeRequest mergeRequest) {
        List<String> loserSyncariRecordIds = mergeRequest.getLosers().stream().map(l -> l.getSyncariEntityId()).collect(Collectors.toList());
        List<IdMapping> bySyncariIds = idMappingService.findBySyncariIds(entityName, loserSyncariRecordIds);
        List<IdMapping> toUpdate = new ArrayList<>();
        List<IdMapping> toDelete = new ArrayList<>();
        bySyncariIds.forEach(loserIdMapping -> {
            mergeRequest.getLosers().forEach(loser -> {
                loserIdMapping.removeMapping(connectorId, sink.getId(), loser.getId());
            });
            if (!loserIdMapping.hasConnectedMappings()) {
                toDelete.add(loserIdMapping);
            } else {
                toUpdate.add(loserIdMapping);
            }

        });
        if(!toUpdate.isEmpty()) {
            idMappingService.saveAll(toUpdate);
        }
        if(!toDelete.isEmpty()){
            idMappingService.deleteAll(toDelete);
        }
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

    protected void runAttributeBatchActions(Map<AttributeDefinition, MappingGraph> attributeGraphs, GraphContext context, BatchActionContext batchActionContext) {
        batchActionContext.enableRunActions();
        context.setBatchActionContext(batchActionContext);
        attributeGraphs.forEach((attribute, graph) -> {
            var actionNodes = batchActionContext.getTopoSortedBatchActionNodes(graph);
            actionNodes.forEach(actionNode -> {
                try {
                    evaluator.evaluate(actionNode, graph, context, n -> n != actionNode, new HashSet<String>());
                } catch (TerminateExecutionPathException e) {
                    log.warn("Batch Action Execution terminated due to errors " + actionNode.getName() + " in graph " + graph.getName(), e);
                }
            });
        });
        context.setBatchActionContext(null);

    }
    
    private List<EntityData> getEntityData(EntityDefinition syncariEntityDefinition, EntityDefinition externalEntityDefinition,
                                           Watermark downstreamWatermark, PageCursor pageCursor, String currentBatchId, Date batchTime, ViperContext context) {
        if(context.isTestMode() || context.isRealTimeMode()){
            Map<String, List<TransactionLog>> latestTransactions = transactionLogService.findLatestTransactions(currentBatchId, batchTime);
            Iterable<EntityData> testRecords = entityRepo.findByIds(syncariEntityDefinition, latestTransactions.keySet());
            List<EntityData> testRecordList = new ArrayList<>();
            testRecords.forEach(record -> testRecordList.add(record));
            return testRecordList;
        }
        List<EntityData> entityData = entityRepo.find(syncariEntityDefinition, Instant.ofEpochMilli(downstreamWatermark.getStart()), pageCursor);
        if (entityData == null || entityData.isEmpty()) {
            log.debug("Exhausted all updated records in syncari. Getting records with unresolved external references now");
            Iterable<EntityData> unresolvedEntities = unresolvedRecordService.getUnresolvedEntities(
                    syncariEntityDefinition.getId(), externalEntityDefinition.getId());
            List unresolvedRecordList = IteratorUtils.toList(unresolvedEntities.iterator());
            log.debug("Found {} records with unresolved external references for external entity {}, syncari entity {}", unresolvedRecordList.size(), externalEntityDefinition.getApiName(), syncariEntityDefinition.getApiName());
            return unresolvedRecordList;
        }
        return entityData;
    }

    class RecordWithIdMapping {
        private final EntityData entityData;
        private final Optional<IdMapping> idMapping;

        // this is used only if when we use txn logs to decide which records to sync
        public RecordWithIdMapping(EntityData entityData, Optional<IdMapping> idMapping) {
            this.entityData = entityData;
            this.idMapping = idMapping;
        }
    }

    private List<RecordWithIdMapping> getRecordsWithIdMapping(EntityDefinition syncariEntityDefinition, EntityDefinition externalEntityDefinition, Watermark downstreamWatermark, PageCursor pageCursor,
                                                              String currentBatchId, Date batchTime, ViperContext context) {
        return getRecordsWithIdMapping(syncariEntityDefinition.getApiName(), getEntityData(syncariEntityDefinition, externalEntityDefinition, downstreamWatermark, pageCursor, currentBatchId, batchTime, context));
    }

    private List<RecordWithIdMapping> getRecordsWithIdMapping(String entityName, List<EntityData> records) {
        Map<String, EntityData> recordMap = records.stream().collect(Collectors.toMap(EntityData::getSyncariEntityId, r -> r));
        final List<IdMapping> idMappings = idMappingService.findBySyncariIds(entityName, recordMap.keySet());
        Map<String, IdMapping> idMappingMap = idMappings.stream().collect(Collectors.toMap(IdMapping::getSyncariId, r -> r));
        return records.stream().map(r -> new RecordWithIdMapping(r, Optional.ofNullable(idMappingMap.get(r.getSyncariEntityId())))).collect(Collectors.toList());
    }

    protected Map<String, List<TransactionLog>> getTxnLogsBySyncariId(EntityDefinition syncariEntityDefinition, List<String> syncariIds, Watermark downstreamWatermark) {
        List<TransactionLog> transactions = transactionLogService.findTransactions(syncariEntityDefinition, syncariIds, downstreamWatermark.getStart());
        Map<String, List<TransactionLog>> txnLogBySyncari = new LinkedHashMap<>();
        for (TransactionLog transaction : transactions) {
            if (Operation.create.equals(transaction.getOperation())
                    || Operation.update.equals(transaction.getOperation())) {
                txnLogBySyncari.computeIfAbsent(transaction.getSyncariId(), k -> new ArrayList<>()).add(transaction);
            }
        }
        return txnLogBySyncari;
    }

    Set<String> diff(HashMap<String, Object> originalValues, Map<String, Object> newValues) {
        Set<String> changedAttributes = new HashSet<>();
        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            log.debug("Checking {} for diff", entry.getKey());
            if (!originalValues.containsKey(entry.getKey()) // newly introduced value
                    || !Objects.equals(originalValues.get(entry.getKey()), entry.getValue())) {//existing value is transformed
                log.debug("diff detected for key - {}", entry.getKey());
                changedAttributes.add(entry.getKey());
            }
        }
        return changedAttributes;
    }

    private void removeIdAndAuditFields(EntitySchema entitySchema, EntityData data) {
        data.removeSystemFields();
        entitySchema.getAttributes().forEach(attribute ->{
            if (attribute.isCreatedAtField() || attribute.isUpdatedAtField() || (attribute.isIdField() && data.getId() != null)) {
                data.remove(attribute.getApiName());
            }
        });
    }

    private void removeNonUpdateableFields(EntitySchema entitySchema, EntityData data) {
        data.removeSystemFields();
        entitySchema.getAttributes().forEach(attribute ->{
            if(!attribute.isUpdateable() || attribute.isCreateOnly()){
                data.remove(attribute.getApiName());
            }
        });
    }

    private Stream<IdMapping.Mapping> getLatestLoserIdMapping( EntityDefinition sink, List<EntityData> losers, List<IdMapping> loserMappings) {
        //Find the latest loser record
        Optional<EntityData> latestLoser = findLatestLoser(losers);
        Stream<IdMapping.Mapping> mappings = latestLoser.stream().flatMap(loser ->
                {
                    Stream<IdMapping> idMappingStream = loserMappings.stream()
                            //find the loser's idmapping
                            .filter(m -> m.getSyncariId().equals(loser.getSyncariEntityId()));
                    Stream<IdMapping.Mapping> mappingStream = idMappingStream
                            //get all the idMapping for the current synapse+entitydefinition
                            .flatMap(m -> m.getMappings(sink.getConnectorId(), sink.getId()).stream());
                    return mappingStream;
                }
        );
        return mappings;
    }


    private Optional<EntityData> findLatestLoser(List<EntityData> losingRecords) {
        return losingRecords.stream().max((e1,e2)  ->(int)(e1.getLastModified() - e2.getLastModified()));
    }

    private List<SyncError> createMergeErrorWinnerLog(CurrentBatch batch, EntityDefinition sink, Connector connector,
                                                MergeRequest request, Optional<MergeResponse> response, Exception e, String operation,
                                                String failedWinnerId) {
        // pick losers from
        return response
                .map(resp -> Optional.of(resp.getWinnerResult()).stream().flatMap(winnerResult -> winnerResult.getResults()
                        .stream().filter(result -> !result.isSuccess())
                        .map(r -> {
                            SyncError syncError =  SyncError.builder().connectorName(connector.getName()).connectorId(connector.getId())
                                    .operation(operation).batchId(batch.getCurrentBatchId())
                                    .externalEntityName(sink.getApiName()).errorCode(e.getMessage())
                                    .errorDetails(String.join("\n", r.getErrors())).syncariRecordId(r.getSyncariId())
                                    .externalRecordId(r.getId()).occuredTime(Instant.now()).build();
                            if (StringUtils.isNotEmpty(batch.getSyncariEntityName())){
                                syncError.setSyncariEntityName(batch.getSyncariEntityName());
                            }
                            return syncError;
                        }))
                        .collect(Collectors.toList()))
                .orElseGet(() -> request.getLosers().stream()
                        .map(r -> {
                            SyncError syncError =  SyncError.builder().connectorName(connector.getName()).connectorId(connector.getId())
                                    .operation(operation).batchId(batch.getCurrentBatchId())
                                    .externalEntityName(sink.getApiName()).errorCode(e.getMessage())
                                    .syncariRecordId(r.getSyncariEntityId())
                                    .errorDetails(toStrackTraceString(e))
                                    .externalRecordId(r.getId()).occuredTime(Instant.now()).build();
                            if (StringUtils.isNotEmpty(batch.getSyncariEntityName())){
                                syncError.setSyncariEntityName(batch.getSyncariEntityName());
                            }
                            return syncError;
                        })
                        .collect(Collectors.toList()));
    }

    private List<SyncError> createMergeErrorLoserLog(CurrentBatch batch, EntityDefinition sink, Connector connector,
            MergeRequest request, Optional<MergeResponse> response, Exception e, String operation,
            String failedWinnerId) {
        // pick losers from
        return response
                .map(resp -> Optional.of(resp.getLoserResult()).stream().flatMap(loserResult -> loserResult.getResults()
                        .stream().filter(result -> !result.isSuccess())
                        .map(r -> {
                            SyncError syncError =  SyncError.builder().connectorName(connector.getName()).connectorId(connector.getId())
                                .operation(operation).batchId(batch.getCurrentBatchId())
                                .externalEntityName(sink.getApiName()).errorCode(e.getMessage())
                                .errorDetails(String.join("\n", r.getErrors())).syncariRecordId(r.getSyncariId())
                                .externalRecordId(r.getId()).occuredTime(Instant.now()).build();
                            if (StringUtils.isNotEmpty(batch.getSyncariEntityName())){
                                syncError.setSyncariEntityName(batch.getSyncariEntityName());
                            }
                            return syncError;
                        }))
                        .collect(Collectors.toList()))
                .orElseGet(() -> request.getLosers().stream()
                        .map(r -> {
                            SyncError syncError =  SyncError.builder().connectorName(connector.getName()).connectorId(connector.getId())
                                    .operation(operation).batchId(batch.getCurrentBatchId())
                                    .externalEntityName(sink.getApiName()).errorCode(e.getMessage())
                                    .syncariRecordId(r.getSyncariEntityId())
                                    .errorDetails(toStrackTraceString(e))
                                    .externalRecordId(r.getId()).occuredTime(Instant.now()).build();
                            if (StringUtils.isNotEmpty(batch.getSyncariEntityName())){
                                syncError.setSyncariEntityName(batch.getSyncariEntityName());
                            }
                            return syncError;
                        })
                        .collect(Collectors.toList()));
    }

    private List<SyncError> createErrorLog(CurrentBatch batch, EntityDefinition sink, Connector connector, SyncRequest request,Optional<SyncResponse> response, Exception e, String operation, String failedWinnerId) {
        //if a response is present, pick syncari ids from failed result objects, else pick syncari ids of all request objects
        List<SyncError> errorList = new ArrayList<>();
        Map<String, EntityData> entityDataMap = request.getData().values().stream().flatMap(List::stream).collect(Collectors.toMap(EntityData::getSyncariEntityId, Function.identity(), (e1, e2) -> e2));

        if (response.isPresent()) {
            for (Result r : response.get().getResults()) {
                if (!r.isSuccess()) {
                    String errors = String.join("\n", r.getErrors());
                    if (StringUtils.isBlank(errors)) {
                        errors = String.format("Empty Error Response received from Synapse %s", connector.getName());
                    }
                    errorList.add(SyncError.builder().connectorName(connector.getName()).connectorId(connector.getId())
                            .operation(operation).batchId(batch.getCurrentBatchId())
                            .externalEntityName(sink.getApiName()).errorCode(e == null ? errors : e.getMessage())
                            .syncariEntityName(batch.getSyncariEntityName())
                            .errorDetails(errors).syncariRecordId(r.getSyncariId())
                            .externalRecordId(!StringUtils.isBlank(r.getId()) ? r.getId() : entityDataMap.getOrDefault(r.getSyncariId(), new EntityData()).getId()).occuredTime(Instant.now()).build());
                }
            }
        } else {
            request.getData().forEach((connectorId, entities) -> {
                entities.forEach(r -> {
                    errorList.add(SyncError.builder().connectorName(connector.getName()).connectorId(connector.getId())
                            .operation(operation).batchId(batch.getCurrentBatchId())
                            .syncariEntityName(batch.getSyncariEntityName())
                            .externalEntityName(sink.getApiName()).errorCode(e.getMessage())
                            .errorDetails(toStrackTraceString(e))
                            .syncariRecordId(r.getSyncariEntityId()).externalRecordId(!StringUtils.isBlank(r.getId()) ? r.getId() : entityDataMap.getOrDefault(r.getSyncariEntityId(), new EntityData()).getId())
                            .occuredTime(Instant.now()).build());                 
                });
            });
        }
        return errorList;
    }

    private String toStrackTraceString(Exception e) {
        StringWriter w = new StringWriter();
        e.printStackTrace(new PrintWriter(w));
        return w.toString();
    }

    private Optional<EntityData> applySinkSidePipeline(GraphContext context, MappingGraph graph, MappingNode sinkNode, EntityData syncariEntity, EntityDefinition syncariEntityDefinition,
                                                       BatchActionContext batchActionContext, Optional<IdMapping> idMapping, Optional<TransactionLog> txLog, Map<String, TransactionLog> batchTransactions) {
        log.debug("Applying sink side pipeline for {} graph {}", graph.getScope().name(), graph.getName());
        var coreNode = graph.getCoreNode();
        var executionContext = context.copy().setBatchActionContext(batchActionContext);
        executionContext.setSyncariRecord(syncariEntity);
        executionContext.setCurrentSyncariId(syncariEntity.getSyncariEntityId());
        executionContext.put("record", syncariEntity);
        executionContext.put("previous", syncariEntity);
        executionContext.put("output_" + coreNode.getId(), Pair.of(new FunctionResult(syncariEntity, ObjectType.VALUE), coreNode));
        executionContext.put(PipelineHelper.INCOMING_CHANGE_FIELD, idMapping.isEmpty() ? "insert" : "update");
        syncariEntityDefinition.getAttributes().forEach(attributeDefinition -> {
            executionContext.put("field_"+attributeDefinition.getId(),syncariEntity.getValue(attributeDefinition.getApiName()));
        });
        try {
            evaluator.evaluate(sinkNode, graph, executionContext, node -> node.getType() == MappingNodeType.CORE_ENTITY, new HashSet<String>());
            if(!executionContext.getErrors().isEmpty()){

                var currentRecord = executionContext.getSyncariRecord();
                // if TxnBQMoveWrite enabled then no need to look for old txn to attach, we create a new txn anyway
                Optional<TransactionLog> lastTxn = txLog;
                lastTxn = Optional.of(getOrCreateTxn(batchTransactions, syncariEntity.getSyncariEntityId(), syncariEntityDefinition,
                        context.getCurrentBatch().getCurrentBatchId(), txLog.map(TransactionLog::getId)));

                lastTxn.ifPresent(last -> {
                    var errors = executionContext.getErrors().getOrDefault(executionContext.getCurrentSyncariId(), new ArrayList<>());
                    if (last.getErrors().size() < MAX_TNX_ERRORS) {
                        last.getErrors()
                                .addAll(errors);
                    }
                    context.getErrors().merge(executionContext.getCurrentSyncariId(), errors,  (o,v) -> {o.addAll(v); return o;});
                });
                executionContext.getErrors().computeIfPresent(executionContext.getCurrentSyncariId(),
                        (k,v) -> null);
            }
            var results = graph.getInboundEdges(sinkNode).stream().flatMap(edge -> Optional.ofNullable((Pair<FunctionResult, MappingNode>) executionContext.get("output_" + edge.getSourceStage().getId())).stream()).collect(Collectors.toList());
            Optional<Pair<FunctionResult, MappingNode>> result = results.stream().filter(
                    r -> !FilterFailedResult.isFailedFilter(r.x.typedValue())
            ).findFirst();
            result.ifPresent(res -> {
                log.debug("Sink side pipeline execution output for {} graph {} is:{}", graph.getScope().name(), graph.getName(), res.x.getResult());
                context.addSinkEntityNodeInput(res);
                if (!(res.x.getResult() instanceof EntityData)){
                    log.info("Result not an instance of EntityData output for {} graph {} is:{}, syncariEntityId is {}", graph.getScope().name(), graph.getName(), res.x.getResult(), syncariEntity.getSyncariEntityId());
                }
            });
            Optional<EntityData> entityData = result.map(filtered -> (EntityData) filtered.x.getResult());
            return entityData;

        }catch(TerminateExecutionPathException e){
            return Optional.empty();
        }
        //return results.map(result -> FilterFailedResult.isFailedFilter(result.x.typedValue())).reduce((r1,r2)-> r1||r2).orElse(false);

    }

    void processRecordResolutions(List<UnresolvedRecord> records){
        List<UnresolvedRecord> unresolvedRecords = new ArrayList<>();
        List<UnresolvedRecord> resolvedRecords = new ArrayList<>();
        for (UnresolvedRecord record : records) {
            if (record.hasUnresolvedFields()) {
                unresolvedRecords.add(record);
            } else {
                resolvedRecords.add(record);
            }
        }
        log.debug("Found {} records with unresolved references, Found {} fully resolved records", unresolvedRecords.size(),resolvedRecords.size());
        unresolvedRecordService.upsert(unresolvedRecords);
        unresolvedRecordService.delete(resolvedRecords);
    }

    List<Record> createExternalEntitiesForGraphs(EntityData record, String connectorId, EntityDefinition syncariEntityDefinition, GraphContext currentContext,
                                                 Map<AttributeDefinition, MappingGraph> fieldDAGs, EntityDefinition sink) {

        Optional<IdMapping> value = idMappingService.findExistingMapping(syncariEntityDefinition.getApiName(), record.getSyncariEntityId(), connectorId,sink.getId());
        return createExternalEntitiesForGraphs(record, connectorId, syncariEntityDefinition, currentContext, fieldDAGs, sink, value, Map.of(), Map.of(), new BatchActionContext(), new HashMap<>(), Optional.empty());
    }

    List<Record> createExternalEntitiesForGraphs(EntityData record, String connectorId, EntityDefinition syncariEntityDefinition, GraphContext currentContext,
                                                 Map<AttributeDefinition, MappingGraph> fieldDAGs, EntityDefinition sink, Optional<IdMapping> value,
                                                 Map<String, IdMapping> resolvedFks, Map<String, EntityDefinition> externalEntityDefinitions,
                                                 BatchActionContext attributeBatchActionContext, Map<String, TransactionLog> batchTransactions, Optional<TransactionLog> lastTxn) {

        List<EntityData> externalRecords = value.stream().flatMap(idMapping ->
                idMapping.getMappings(connectorId, sink.getId()).stream().map(m -> {
                    var externalEntity = new EntityData(sink.getApiName());
                    externalEntity.setDeleted(record.isDeleted());
                    externalEntity.setSyncariEntityId(record.getSyncariEntityId());
                    externalEntity.setNew(false);
                    externalEntity.setId(m.getEntityId());
                    externalEntity.setLastTransactionLogId(record.getLastTransactionLogId());
                    externalEntity.setLastTransactionTimestamp(record.getLastTransactionTimestamp());
                    externalEntity.setSyncariTimestamp(record.getSyncariTimestamp());
                    return externalEntity;
                })).collect(Collectors.toList());
        if(externalRecords.isEmpty()){
            var externalEntity = new EntityData(sink.getApiName());
            externalEntity.setDeleted(record.isDeleted());
            externalEntity.setSyncariEntityId(record.getSyncariEntityId());
            externalEntity.setSyncariTimestamp(record.getSyncariTimestamp());
            externalEntity.setNew(true);
            externalEntity.setLastTransactionLogId(record.getLastTransactionLogId());
            externalRecords = List.of(externalEntity);
        }
        List<EntityData> uniqueExternalRecords = getUniqueExternalRecords(externalRecords);
        List<Record> records = uniqueExternalRecords.stream().map(r -> executePipelines(r, record, syncariEntityDefinition,
                currentContext, fieldDAGs, sink, resolvedFks, externalEntityDefinitions, attributeBatchActionContext, batchTransactions, lastTxn)).collect(Collectors.toList());
        return records;
    }

    private List<EntityData> getUniqueExternalRecords(List<EntityData> externalRecords) {
        List<EntityData> uniqueExternalRecords= new ArrayList<>();
        //TODO: Identify the root cause for duplicate idmappings for same connector, entitydef and record
        Map<String, List<EntityData>> byKey = externalRecords.stream().
                collect(Collectors.groupingBy(r -> r.getConnectorId() + "_" + r.getName() + "_" + r.getId()));
        byKey.forEach((k,records)->{
            if(!records.isEmpty()) {
                uniqueExternalRecords.add(records.get(0));
            }
        });
        return uniqueExternalRecords;
    }

    private String recordKey(EntityData record){
        return record.getConnectorId()+"_"+record.getName()+"_"+record.getId();
    }

    Record executePipelines(EntityData externalEntity, EntityData record, EntityDefinition syncariEntityDefinition, GraphContext currentContext,
                            Map<AttributeDefinition, MappingGraph> fieldDAGs, EntityDefinition sink, Map<String, IdMapping> resolvedFks,
                            Map<String, EntityDefinition> externalEntityDefinitions, BatchActionContext attributeBatchActionContext, Map<String, TransactionLog> batchTransactions, Optional<TransactionLog> sourceTxn) {
        UnresolvedRecord unresolvedRecord = new UnresolvedRecord().setConnectorId(sink.getConnectorId())
                .setSyncariEntityDefinitionId(syncariEntityDefinition.getId())
                .setSyncariId(record.getSyncariEntityId())
                .setExternalEntityDefinitionId(sink.getId());
        if (externalEntity.isDeleted()) return new Record(externalEntity, unresolvedRecord);
        currentContext.put("sinkField_mutated", false);
        fieldDAGs.forEach((syncariAttribute, dag) -> {
            log.debug("Executing sink side pipeline for {}:{}", sink.getApiName(), syncariAttribute.getApiName());
            List<MappingNode> sinks = dag.getConnectedSinks().filter(n -> ((AttributeSinkNodeConfig) n.getConfiguration()).getAttributeDefinition().getEntityId().equals(sink.getId())).collect(Collectors.toList());
            //run pipelines on all fields
            var childContext = currentContext.createSubContext(dag);
            childContext.setErrors(new LinkedHashMap<>());
            childContext.setSyncariRecord(record);
            childContext.setCurrentSyncariId(record.getSyncariEntityId());
            childContext.put(PipelineHelper.INCOMING_CHANGE_FIELD,externalEntity.isNew() ?  "insert":"update");
            childContext.put(SYNCARI_ID, record.getSyncariEntityId());
            childContext.put(syncariEntityDefinition.getApiName(), record.getValues());
            var coreNode = dag.getCoreNode();
            Object syncariValue = !syncariAttribute.isIdField() ? record.getValue(syncariAttribute.getApiName()) : record.getId();
            childContext.put("output_" + coreNode.getId(), Pair.of(new FunctionResult(syncariValue, syncariAttribute.getDataType()), coreNode));
            childContext.put("previous", syncariValue);
            childContext.put("record", record);
            childContext.setBatchActionContext(attributeBatchActionContext);
            syncariEntityDefinition.getActiveAttributes().forEach(attribute->{
                if(record.has(attribute.getApiName())){
                    childContext.put("field_" +attribute.getId(),record.getValue(attribute.getApiName()));
                }
            });
            sinks.forEach(sinkNode -> {
                try {
                    AttributeSinkNodeConfig sinkNodeCOnfig = (AttributeSinkNodeConfig) sinkNode.getConfiguration();
                    var sinkAttribute = sink.getAttribute(sinkNodeCOnfig.getAttributeDefinition().getId());
                    if(sinkAttribute==null){
                        log.warn("Destination node {} no longer exists. Skipping evaluation",sinkNode.getName());
                        return;
                    }
                    // store sink attribute to syncari attribute mapping for later transaction log processing
                    currentContext.putIfAbsent("sinkField_" + sinkAttribute.getApiName(), syncariAttribute);
                    childContext.put("current_sink_attribute_id", sinkAttribute.getId());

                    evaluator.evaluate(sinkNode, dag, childContext, n -> n.getType() == MappingNodeType.CORE_ATTRIBUTE, new HashSet<String>());
                    if(!childContext.getErrors().isEmpty()){

                        EntityData currentRecord = childContext.getSyncariRecord();
                        Optional<TransactionLog> lastTxn = Optional.of(getOrCreateTxn(batchTransactions, record.getSyncariEntityId(), syncariEntityDefinition, childContext.getCurrentBatch().getCurrentBatchId(),
                                sourceTxn.map(TransactionLog::getId)));
                        lastTxn.ifPresent(last -> {
                            var errors = childContext.getErrors().getOrDefault(childContext.getCurrentSyncariId(), new ArrayList<>());
                            if (last.getErrors().size() < MAX_TNX_ERRORS) {
                                last.getErrors()
                                        .addAll(errors);
                            }
                            currentContext.getErrors().merge(childContext.getCurrentSyncariId(), errors,  (o,v) -> {o.addAll(v); return o;});
                        });
                        childContext.getErrors().computeIfPresent(childContext.getCurrentSyncariId(),
                                (k,v) -> null);
                    }
                    addDecryptedAttributeIdsToContext(currentContext, childContext);
                    Stream<String> resultIds = dag.getInboundEdges(sinkNode).stream().map(e -> "output_" + e.getSourceStage().getId());
                    List<Pair<FunctionResult, MappingNode>> results = resultIds.flatMap(resultId -> Optional.ofNullable((Pair<FunctionResult, MappingNode>) childContext.get(resultId)).stream()).collect(Collectors.toList());
                    List<Pair<FunctionResult, MappingNode>> successfulResults = results.stream().filter(r -> !FilterFailedResult.isFailedFilter(r.x.getResult())).collect(Collectors.toList());
                    List<Pair<FunctionResult, MappingNode>> nonnullResults = successfulResults.stream().filter(r -> r.x.typedValue() != null).collect(Collectors.toList());
                    var result = nonnullResults.isEmpty() ? successfulResults.stream().findFirst() : nonnullResults.stream().findFirst();

                    if(sinkAttribute.isChild()){
                        Object children = getChildRecords(sinkAttribute, nonnullResults);
                        result = result.map(r-> Pair.of(new FunctionResult(children, ChildType.VALUE),r.y));
                    }

                    result.ifPresentOrElse(r-> {
                        //use defaultValue if evaluated value is null & this is a required field
                        Object defaultValue = getDefaultValue(sinkNodeCOnfig, sinkAttribute, childContext);
                        Object pipelineResult = r.x.typedValue();
                        boolean isBlank = pipelineResult == null || StringUtils.isEmpty(pipelineResult.toString());
                        boolean nullAndMandatory = !sinkAttribute.isNillable() && isBlank;
                        boolean defaultOnBlank = sinkNodeCOnfig.isAlwaysUseDefaultOnEmpty() && isBlank;

                        // check if pipeline result is different from incoming syncari value
                        if (!Objects.equals(syncariValue, pipelineResult)) {
                            log.debug("Syncari value {} is different from pipeline result {} for attribute {}", syncariValue,
                                    pipelineResult, syncariAttribute.getApiName(), sinkNodeCOnfig.getAttributeDefinition().getApiName());
                            currentContext.put("sinkField_mutated", true);
                        }

                        Object finalValue = defaultOnBlank || nullAndMandatory ? defaultValue : pipelineResult;
                        Object resolvedExternalFk = null;
                        if (syncariAttribute.isReference() && sinkAttribute.isReference()) {
                            resolvedExternalFk = resolveExternalFk(syncariAttribute,
                                    sink, pipelineResult, sinkAttribute, resolvedFks, externalEntityDefinitions);
                            //If resolved FK is null, it's either not mapped in Syncari yet, or we haven't seen the resolved fk yet in syncari
                            //Don't set it
                            //One of three actions - use resolved FK or use default value or skip setting fK
                            //The logic is based on (kinda) solving the K-Map below for actions
                            //Its kinda solving k-map because we are not looking for a single logic expression
                            //but a sequential set of expressions that'll do the job
                            //|Row| pipelineResult  | resolvedFK | IsNew | IsNillable | Action      |
                            //-----------------------------------------------------------------------
                            //| 1 |   Y             |     Y      |  Y    |    Y       |  resolvedFK |
                            //| 2 |   Y             |     Y      |  Y    |    N       |  resolvedFK |
                            //| 3 |   Y             |     Y      |  N    |    Y       |  resolvedFK |
                            //| 4 |   Y             |     Y      |  N    |    N       |  resolvedFK |
                            //| 5 |   Y             |     N      |  Y    |    Y       |  Skip       |
                            //| 6 |   Y             |     N      |  Y    |    N       |  default    |
                            //| 7 |   Y             |     N      |  N    |    Y       |  Skip       |
                            //| 8 |   Y             |     N      |  N    |    N       |  Skip       |
                            //| 9 |   N             |     Y      |  Y    |    Y       |  Invalid    |
                            //| 10|   N             |     Y      |  Y    |    N       |  Invalid    |
                            //| 11|   N             |     Y      |  N    |    Y       |  Invalid    |
                            //| 12|   N             |     Y      |  N    |    N       |  Invalid    |
                            //| 13|   N             |     N      |  Y    |    Y       |  default    |
                            //| 14|   N             |     N      |  Y    |    N       |  default    |
                            //| 15|   N             |     N      |  N    |    Y       |  Skip       |
                            //| 16|   N             |     N      |  N    |    N       |  Skip       |

                            if(pipelineResult!=null && resolvedExternalFk!=null){ //K-Map rows 1 thru 4
                                externalEntity.addValue(sinkNodeCOnfig.getAttributeDefinition().getApiName(), resolvedExternalFk);
                            }else if(pipelineResult!=null && externalEntity.isNew()  && !sinkAttribute.isNillable() && defaultValue!=null){//k-map row 6
                                externalEntity.addValue(sinkNodeCOnfig.getAttributeDefinition().getApiName(), defaultValue);
                            }else if(pipelineResult==null && externalEntity.isNew() && defaultValue!=null && !StringUtils.isBlank(defaultValue.toString())){//k-map rows 13/14
                                externalEntity.addValue(sinkNodeCOnfig.getAttributeDefinition().getApiName(), defaultValue);
                            }else{
                                //Required non null FK during create is null.
                                if((!sinkAttribute.isNillable() || sinkNodeCOnfig.isRequired()) && externalEntity.isNew()){
                                    unresolvedRecord.addUnresolvedField(sinkAttribute.getId());
                                }
                                //Skip setting FK for all other rows
                            }
                        } else {
                            //non-FK value
                            log.debug("Final value {} added to external entity for attribute {}", finalValue, sinkNodeCOnfig.getAttributeDefinition().getApiName());
                            externalEntity.addValue(sinkNodeCOnfig.getAttributeDefinition().getApiName(), finalValue);

                        }
                        applyRejectEmptyPolicy(externalEntity, sinkNode);
                        childContext.captureTestOutputForNode(new FunctionResult(finalValue, sinkAttribute.getDataType()), sinkNode, r.y);
                        log.debug("Executed SaveToSink FP for entity {} id {} field {} value {}, haskey {} , exists : {}",externalEntity.getName(),
                                externalEntity.getId(),sinkAttribute.getApiName(), externalEntity.getValue(sinkNodeCOnfig.getAttributeDefinition().getApiName()),sinkNodeCOnfig.getAttributeDefinition().getApiName(),
                                externalEntity.getValues().containsKey(sinkNodeCOnfig.getAttributeDefinition().getApiName()));

                    },()-> {
                        log.debug("Value discarded due to filter on attribute {} on entity {}", syncariAttribute.getApiName(), sink.getApiName());
                    });
                } catch (TerminateExecutionPathException e) {
                    log.warn("Execution terminated by a filter");
                }
            });
        });
        currentContext.clearChildren();
        log.debug("Syncari Entity {}, Transformed record for {} to {} for synapse {}, before removing it for nillable condition",syncariEntityDefinition.getApiName(),externalEntity.getName(),externalEntity, sink.getConnectorId());

        //skip picklists, non nullable values
        //TODO: Resolve references, validate picklist values etc
        sink.getAttributes().stream().forEach( attribute-> {
            if((!attribute.isNillable()  && externalEntity.getValue(attribute.getApiName())==null)){
                externalEntity.remove(attribute.getApiName());
            }
        });
        log.debug("Syncari Entity {}, Transformed record for {} to {} for synapse {}",syncariEntityDefinition.getApiName(),externalEntity.getName(),externalEntity, sink.getConnectorId());
        return  new Record(externalEntity, unresolvedRecord);
    }

    protected void applyRejectEmptyPolicy(EntityData externalEntity, MappingNode sinkNode) {
        // if configured to ignore null, remove null values from entitydata
        if(sinkNode.getTypedConfiguration().getConfigMap().containsKey(Constants.REJECT_EMPTY)
                && sinkNode.getTypedConfiguration().getConfigMap().get(Constants.REJECT_EMPTY) != null) {
            Object rejectEmptyParam = sinkNode.getTypedConfiguration().getConfigMap().get(Constants.REJECT_EMPTY);
            if(rejectEmptyParam != null) {
                Constants.REJECT_EMPTY_ENUM rejectEmpty = Constants.REJECT_EMPTY_ENUM.NEVER;
                try {
                    rejectEmpty = Constants.REJECT_EMPTY_ENUM.valueOf(rejectEmptyParam.toString());
                } catch (IllegalArgumentException e){
                }
                if(rejectEmpty != Constants.REJECT_EMPTY_ENUM.NEVER) {
                    //create or update
                    if(rejectEmpty == Constants.REJECT_EMPTY_ENUM.ALWAYS || (externalEntity.getId() == null && rejectEmpty == Constants.REJECT_EMPTY_ENUM.ON_CREATE) ||
                            (externalEntity.getId() != null && rejectEmpty == Constants.REJECT_EMPTY_ENUM.ON_UPDATE)) {
                        if (externalEntity.getValues().containsKey(sinkNode.getApiName())){
                            Object value = externalEntity.getValues().get(sinkNode.getApiName());
                            if (Objects.isNull(value) || ((value instanceof String) && StringUtils.isEmpty((String)value)) ||
                                    ((value instanceof List) && CollectionUtils.isEmpty((List)value)) || ((value instanceof Map) && MapUtils.isEmpty((Map<?, ?>) value))){
                                externalEntity.getValues().remove(sinkNode.getApiName());
                            }
                        }
                    }
                }
            }
        }
    }

    private void addDecryptedAttributeIdsToContext(GraphContext currentContext, GraphContext childContext) {
        if (currentContext.containsKey("decrypted_sink_attribute_ids")) {
            Object currentDecryptedId = childContext.get("decrypted_sink_attribute_id");
            Set<Object> decryptedIds = (HashSet<Object>) currentContext.get("decrypted_sink_attribute_ids");
            decryptedIds.add(currentDecryptedId);
        } else{
            Object currentDecryptedId = childContext.get("decrypted_sink_attribute_id");
            Set<Object> decryptedIds = new HashSet<>();
            decryptedIds.add(currentDecryptedId);
            currentContext.put("decrypted_sink_attribute_ids", decryptedIds);
        }
    }

    private boolean isDefaultOnEmptySet(EntityData record, AttributeDefinition syncariAttribute, List<MappingNode> sinks) {
        boolean isEmpty = StringUtils.isEmpty(record.getValueAsString(syncariAttribute.getApiName()));
        return sinks.stream().anyMatch(m -> AttributeSinkNodeConfig.class.cast(m.getConfiguration()).isAlwaysUseDefaultOnEmpty()) && isEmpty;
    }

    protected Object getDefaultValue(AttributeSinkNodeConfig sinkNodeCOnfig, AttributeDefinition sinkAttribute, GraphContext childContext) {
        try {
            Object defaultValue = sinkNodeCOnfig.getDefaultValue();
            // do not try to resolve tokens if this value is a list
            if (!Objects.isNull(defaultValue) && !List.class.isAssignableFrom(defaultValue.getClass()) && !sinkAttribute.isMultiValueField()) {
                return sinkAttribute.convert(tokenHelper.resolveTokens(childContext, defaultValue.toString()));
            }
            return sinkAttribute.convert(defaultValue);
        }catch (Exception e){
            log.error(e.getMessage(),e);
            log.error("Converting default value {} to {} failed",sinkNodeCOnfig.getDefaultValue(), sinkAttribute.getDataType());
        }
        return null;
    }

    private List<IdMapping> deleteIdMapping(String entityName, String connectorId, SyncResponse deleteResponse, EntityDefinition sink) {
        Map<String, List<Result>> syncariIdToResult = deleteResponse.getResults().stream()
                .filter(v -> v.isSuccess() && !StringUtils.isEmpty(v.getSyncariId())).collect(Collectors.groupingBy(r -> r.getSyncariId()));

        var mappings = idMappingService.findBySyncariIds(entityName, List.copyOf(syncariIdToResult.keySet())).stream().map(idMapping -> {
            syncariIdToResult.getOrDefault(idMapping.getSyncariId(), List.of()).forEach(result -> {
                idMapping.disconnectMapping(connectorId, sink.getId(), result.getId());
            });
            return idMapping;
        }).collect(Collectors.toList());

        return idMappingService.saveAll(mappings);
    }

    private void deleteIdMappingAndUpdateExternalIds(String entityName, String connectorId, List<Result> results, EntityDefinition sink, EntityDefinition syncariEntityDefinition) {
        Map<String, List<Result>> syncariIdToResult = results.stream()
                .filter(v -> !StringUtils.isEmpty(v.getSyncariId())).collect(Collectors.groupingBy(r -> r.getSyncariId()));

        List<EntityData> recordsToBeUpdated = new ArrayList<>();
        var mappings = idMappingService.findBySyncariIds(entityName, syncariIdToResult.keySet()).stream().map(idMapping -> {
            syncariIdToResult.getOrDefault(idMapping.getSyncariId(), List.of()).forEach(result -> {
                idMapping.disconnectMapping(connectorId, sink.getId(), result.getId());
                EntityData d = new EntityData(syncariEntityDefinition.getApiName()).setSyncariEntityId(idMapping.getSyncariId());
                repoService.disconnectExternalId(syncariEntityDefinition, d, sink.getId(), Optional.empty(), Optional.empty());
                recordsToBeUpdated.add(d);
            });
            return idMapping;
        }).collect(Collectors.toList());
        idMappingService.saveAll(mappings);
        entityRepo.updateValues(syncariEntityDefinition, recordsToBeUpdated);
    }


    private EntityData fixDatatypes(Map<String, AttributeDefinition> attribMap, EntityData d) {
        var current = new HashMap<>(d.getValues());
        current.forEach((key,value)->{
            if(attribMap.containsKey(key.toLowerCase())) {
                var attrib = attribMap.get(key.toLowerCase());
                    try {
                        Object converted;
                        // Remove old and replace with proper case.
                        value = d.getValues().remove(key);
                        // Force converting reference types to string. The external id from resolveExternalFk will always be string.
                        if(attrib != null && attrib.getDataType().equals(ReferenceType.VALUE)) {
                            // for MultiValueField references, we need to convert them into List of strings
                            if (attrib.isMultiValueField()){
                                if (value != null && List.class.isAssignableFrom(value.getClass())) {
                                    converted = List.class.cast(value).stream()
                                            .map(v -> new StringType().convert(v))
                                            .collect(Collectors.toList());
                                } else {
                                    converted = (value == null) ? null :
                                            (new StringType().convert(value) == null ? null : List.of(new StringType().convert(value))
                                    );
                                }
                            } else {
                                converted = new StringType().convert(value);
                            }
                        } else {
                            converted = attrib.convert(value);
                        }
                        // Put back the keys with attribute's schema apiName along with proper casing as it would appear in the schema.
                        d.getValues().put(attrib.getApiName(), converted==null ? value : converted);
                    }catch(Exception e){
                        log.error("Conversion error. Could not convert value {} to datatype {} for field {}",value,attrib.getDataType().getName(),key);
                        log.error(e.getMessage(), e);
                    }

            }
        });
        return d;
    }

    private Object getChildRecords(AttributeDefinition attributeDefinition, List<Pair<FunctionResult, MappingNode>> results) {
        Object children = null;
        if(attributeDefinition.isChild()){
            List<EntityData> childRecords= new ArrayList<>();
            //each result is one or more fields
            results.forEach(result->{
                Object r = result.x.getResult();
                List<Object> partialChildRecords = (r instanceof List) ? (List<Object>) r : List.of(r);
                for (int i=0;i<partialChildRecords.size();i++) {
                    Object partialChildRecord = partialChildRecords.get(i);
                    EntityData childRecord = childRecords.size()>i ? childRecords.get(i) : new EntityData(attributeDefinition.getReferenceTo());
                    if (!FilterFailedResult.isFailedFilter(partialChildRecord)) {
                        childRecord.getValues().putAll(((EntityData)partialChildRecord).getValues());
                    }
                    if(childRecords.size()<=i){
                        childRecords.add(childRecord);
                    }
                }
            });
            List<EntityData> nonEmptyChildRecords = childRecords.stream().filter(c -> !c.getValues().isEmpty()).collect(Collectors.toList());
            if(attributeDefinition.isMultiValueField()){
                children = nonEmptyChildRecords;
            }else{
                children = nonEmptyChildRecords.isEmpty() ? nonEmptyChildRecords : childRecords.get(0);
            }
        }
        return children;
    }

    private Object resolveExternalFk(AttributeDefinition syncariAttribute,
                                     EntityDefinition externalEntityDefinition, Object syncariId, AttributeDefinition externalAttribute, Map<String, IdMapping> resolvedIdMap, Map<String, EntityDefinition> externalEntityDefinitions) {
        if (syncariAttribute.isReference() && syncariId != null) {
            String syncariEntityName = syncariAttribute.getReferenceTo();
            List<String> syncariIds = syncariAttribute.isMultiValueField() ? List.class.cast(syncariId) : List.of(syncariId.toString());
            List<IdMapping> resolvedIds = new ArrayList<>();
            List<String> unresolvedIds = new ArrayList<>();
            syncariIds.forEach(id -> {
                String key = syncariEntityName + "#" + id;
                if(resolvedIdMap.containsKey(key)) {
                    resolvedIds.add(resolvedIdMap.get(key));
                } else {
                    unresolvedIds.add(id);
                }
            });
            if(!unresolvedIds.isEmpty()) {
                log.debug("{} unresolved ids exist. Lookup up in db", unresolvedIds.size());
                resolvedIds.addAll(idMappingService.findBySyncariIds(syncariEntityName, unresolvedIds));
            }
            Optional<EntityDefinition> referencedEntity;
            String connectorId = externalEntityDefinition.getConnectorId();
            String referenceEntityMapKey = connectorId + "_" + externalAttribute.getReferenceTo();
            if(externalEntityDefinitions.containsKey(referenceEntityMapKey)) {
                referencedEntity = Optional.of(externalEntityDefinitions.get(referenceEntityMapKey));
            } else {
                log.debug("Cannot find entitydefinition for {} in memory. Lookup up in db", externalAttribute.getReferenceTo());
                referencedEntity = schemaService.getEntityByName(connectorId, externalAttribute.getReferenceTo());
            }
            List<String> externalFks = new ArrayList<>();
            referencedEntity.ifPresent(ref-> {
                for (IdMapping resolvedId : resolvedIds) {
                    resolvedId.getMappings(connectorId, ref.getId())
                            .forEach(resolved -> externalFks.add(resolved.getEntityId()));
                }
            });
            log.debug(
                    "Found fk value {} for external entity {} on field {} and reference entity {} with syncariId {}",
                    externalFks, externalEntityDefinition.getApiName(), syncariAttribute.getApiName(), externalAttribute.getReferenceTo(), syncariId);
            if(externalFks.isEmpty()){
                return null;
            }
            if(syncariAttribute.isMultiValueField()){
                return externalFks;
            }else{
                return externalFks.get(0);
            }
        }
        return null;
    }

    private void applyPostDestinationFieldPipelines(Connector connector, Map<AttributeDefinition, MappingGraph> fieldDAGs, EntityData record, EntityData externalRecord,
                                                    EntityDefinition syncariEntityDefinition,
                                                    EntityDefinition sink,
                                                    GraphContext parentContext, BatchActionContext attributeBatchActionContext) {

        fieldDAGs.forEach((syncariAttribute, dag) -> {
            log.debug("Executing post destination sink side pipelines for {}:{} for record {}",sink.getApiName(), syncariAttribute.getApiName(), record.getSyncariEntityId());
            List<MappingNode> sinks = dag.getConnectedSinks().filter(n -> ((AttributeSinkNodeConfig) n.getConfiguration()).getAttributeDefinition().getEntityId().equals(sink.getId())).collect(Collectors.toList());
            //run pipelines on all fields
            var childContext = parentContext.createSubContext(dag);

            populateContext(connector, dag, record, syncariEntityDefinition, externalRecord, sink, childContext, attributeBatchActionContext);
            childContext.setErrors(new LinkedHashMap<>());
            childContext.setSyncariRecord(record);
            childContext.put(SYNCARI_ID, record.getSyncariEntityId());
            syncariEntityDefinition.getActiveAttributes().forEach(attribute->{
                if(record.has(attribute.getApiName())){
                    childContext.put("field_" +attribute.getId(),record.getValue(attribute.getApiName()));
                }
            });
            sinks.forEach(sinkNode -> {
                try {

                    AttributeSinkNodeConfig sinkNodeCOnfig = (AttributeSinkNodeConfig) sinkNode.getConfiguration();
                    var sinkAttribute = sink.getAttribute(sinkNodeCOnfig.getAttributeDefinition().getId());
                    if(sinkAttribute==null){
                        log.warn("Destination node {} no longer exists. Skipping evaluation",sinkNode.getName());
                        return;
                    }
                    Stream<MappingNode> actionNodes = findStandaloneActions(dag, sinkAttribute.getId());

                    Object value = !sinkAttribute.isIdField() ? externalRecord.getValue(sinkAttribute.getApiName()) : externalRecord.getId();
                    childContext.put("previous", value);

                    actionNodes.forEach(actionNode -> {
                        childContext.put("output_" + sinkNode.getId(), Pair.of(new FunctionResult(value, sinkAttribute.getDataType()), sinkNode));
                        evaluator.evaluate(actionNode, dag, childContext, n -> ACTION_TERMINALS.contains(n.getType()), new HashSet<String>());
                        //TODO: Action error handling here
                        if(!childContext.getErrors().isEmpty()){
                        Optional<TransactionLog> lastTxn = transactionLogService.findByTransactionLogId(childContext.getSyncariRecord().getLastTransactionLogId(), childContext.getSyncariRecord().getLastTransactionTimestamp());
                        lastTxn.ifPresent(last -> {
                            var errors = childContext.getErrors().getOrDefault(childContext.getCurrentSyncariId(), new ArrayList<>());
                            if (last.getErrors().size() < MAX_TNX_ERRORS) {
                                last.getErrors()
                                        .addAll(errors);
                                transactionLogService.log(last);
                            }
                            parentContext.getErrors().merge(childContext.getCurrentSyncariId(), errors,  (o,v) -> {o.addAll(v); return o;});
                        });
                        childContext.getErrors().computeIfPresent(childContext.getCurrentSyncariId(),
                                (k,v) -> null);
                    }
                    });
                } catch (TerminateExecutionPathException e) {
                    log.warn("Execution terminated by a filter");
                }
            });
        });
        parentContext.clearChildren();
    }

    private void populateContext(Connector connector, MappingGraph graph, EntityData entityData,
                                 EntityDefinition syncariEntityDef, EntityData externalRecord, EntityDefinition externalEntityDef,
                                 GraphContext context, BatchActionContext batchActionContext) {

        context.setBatchActionContext(batchActionContext);
        //Set source records

        List<MappingNode> sinks = graph.getSink(externalEntityDef.getId());
        sinks.forEach(sink ->{
            context.put("output_"+ sink.getId(),Pair.of(new FunctionResult(externalRecord,ObjectType.VALUE),sink));
        });

        syncariEntityDef.getAttributes().forEach(a->{
            if(entityData.getValues().containsKey(a.getApiName())) {
                context.put("field_" + a.getId(), entityData.getValue(a.getApiName()));
            }
        });

        context.put("record",entityData);
        context.setSyncariRecord(entityData);
        context.setCurrentSyncariId(entityData.getSyncariEntityId());
        context.put(syncariEntityDef.getApiName(),entityData.getValues());
        context.put("external_record", externalRecord);
    }

	private void applyPostDestinationEntityPipeline(Connector connector, MappingGraph graph, EntityData entityData,
			EntityDefinition syncariEntityDef, EntityData externalRecord, EntityDefinition externalEntityDef,
			GraphContext context, BatchActionContext batchActionContext) {
        var actionNodes = findStandaloneActions(graph, externalEntityDef.getId()).collect(Collectors.toList());
        if(actionNodes.isEmpty()) {
        	return;
        }

        populateContext(connector, graph, entityData, syncariEntityDef, externalRecord, externalEntityDef, context, batchActionContext);

        actionNodes.forEach(actionNode -> {
            try {
                evaluator.evaluate(actionNode, graph, context, n -> ACTION_TERMINALS.contains(n.getType()), new HashSet<String>());
                if(!context.getErrors().isEmpty()) {
               	 Optional<TransactionLog> lastTxn = transactionLogService.findByTransactionLogId(entityData.getLastTransactionLogId(), entityData.getLastTransactionTimestamp());
                 var errors = context.getErrors().getOrDefault(context.getCurrentSyncariId(), new ArrayList<>());
                 if (errors.size() < MAX_TNX_ERRORS) {
                     lastTxn.ifPresent(t->{
                         t.getErrors()
                                 .addAll(errors);
                         transactionLogService.log(t);
                     });
                 }
                    context.getErrors().merge(context.getCurrentSyncariId(), errors,  (o,v) -> {o.addAll(v); return o;});
                }
            } catch (Exception e){
            	 Optional<TransactionLog> lastTxn = transactionLogService.findByTransactionLogId(entityData.getLastTransactionLogId(), entityData.getLastTransactionTimestamp());
            	 lastTxn.ifPresent(t->{
 					t.getErrors()
					.add(new NodeError().setError(e.getMessage())
							.setErrorDetails(ExceptionUtils.getStackTrace(e)).setNodeId(actionNode.getId())
							.setNodeName(actionNode.getApiName()));
 					transactionLogService.log(t);
            	 });
                log.warn("Action Execution terminated due to errors", e);
                if(!(e instanceof TerminateExecutionPathException)) throw e;
            }
        });
    }

    /*
     * Find actions that have no outbound edges and are connected to a destination
     */
    private Stream<MappingNode> findStandaloneActions(MappingGraph graph, String destinationId) {
        Stream<MappingNode> actions = graph.getActions();
        Stream<MappingNode> loopNodes = graph.getLoopNodes();

        if (graph.getScope().equals(Scope.ATTRIBUTE)) {
            var terminalActions = actions.filter(a -> graph.getOutboundEdges(a).isEmpty() && graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.ATTRIBUTE_SINK
                    && ((AttributeSinkNodeConfig) node.getConfiguration()).getAttributeDefinition().getId().equals(destinationId)));
            var terminalLoopNodes = loopNodes.filter(a -> graph.getOutboundEdges(a).size() == 1 && graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.ATTRIBUTE_SINK
                    && ((AttributeSinkNodeConfig) node.getConfiguration()).getAttributeDefinition().getId().equals(destinationId)));
            return Stream.concat(terminalActions, terminalLoopNodes);

        } else {
            var terminalActions = actions.filter(a -> graph.getOutboundEdges(a).isEmpty() && graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.ENTITY_SINK
                    && ((EntitySinkNodeConfig) node.getConfiguration()).getEntityDefinition().getId().equals(destinationId)));
            var terminalLoopNodes = loopNodes.filter(a -> graph.getOutboundEdges(a).size() == 1 && graph.pathToNodeMatches(a,node -> node.getType() == MappingNodeType.ENTITY_SINK
                    && ((EntitySinkNodeConfig) node.getConfiguration()).getEntityDefinition().getId().equals(destinationId)));
            return Stream.concat(terminalActions, terminalLoopNodes);
        }
    }
}

@Data
@AllArgsConstructor
class Record {
    EntityData entityData;
    UnresolvedRecord recordResolution;
}
