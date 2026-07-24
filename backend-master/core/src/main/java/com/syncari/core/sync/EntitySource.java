package com.syncari.core.sync;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.DataSourceRequest;
import com.syncari.core.DataTransformer;
import com.syncari.core.GlobalConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.EventData;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.StreamState;
import com.syncari.core.model.misc.*;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.repositories.customer.StagedBatchRepo;
import com.syncari.core.repositories.customer.StagedExternalRecordRepo;
import com.syncari.core.repositories.customer.UnresolvedReferenceRepo;
import com.syncari.core.service.*;
import com.syncari.core.template.TemplateRenderer;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.CollectionUtils.map;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Component
public class EntitySource implements DataSource {
    private static final String SYNC_COMPLETION_TEMPLATE_PATH = "templates/sync.complete.template";
    public static final int MAX_REQUEUED_RECORDS_PER_CYCLE = 1000;
    private static final int UNRESOLVED_REFERENCE_BATCH_SIZE = 1000;
    //"Workaround": If SFDC is one of the synapses, we can only query upto 5 minutes before now
    // Also for cases like Marketo or Googlesheets, we have to yield for 5 minutes before now
    private static final long SYNC_MAX_WATERMARK_SECONDS = 5*60;

    @Autowired
    WatermarkService syncService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService connectorMetadataService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    StagedExternalRecordRepo externalRecordRepo;
    @Autowired
    StagedBatchRepo stagingRepo;
    @Autowired
    StagedBatchRecordRepo recordRepo;
    @Autowired
    DataServiceFactory factory;
    @Autowired
    DateUtil dateUtil;
    @Autowired
    DataTransformer transformer;
    @Autowired
    UserService userService;
    @Autowired
    EventStore eventStore;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    TemplateRenderer renderer;
    @Autowired
    AppConfig appConfig;
    @Autowired
    EntitySourceHelper helper;
    @Autowired
    ResyncService resyncService;
    @Autowired
    UnresolvedReferenceService unresolvedReferenceService;
    @Autowired
    NotificationService notificationService;
    @Autowired
    BatchJobService batchJobService;
    @Autowired
    GCSFileManager storage;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    RequeueService requeueService;
    @Autowired
    EventDataService eventDataService;
    @Autowired
    SyncDetailMetricService syncDetailMetricService;
    @Autowired
    UnresolvedReferenceRepo unresolvedReferenceRepo;

    @Autowired
    IdMappingService idMappingService;
    @Autowired
    FeatureService featureService;
    @Autowired
    EntityRepoService entityRepoService;
    // syncariid_connectorid_entitydefid_recordid_timestamp
    Cache<String, String> processedIdCache = CacheBuilder.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .build();
    Map<String, List<String>> tempProcessedIdCache = new HashMap<>();

    /**
     * An api which takes in a Syncari entity name and looks up all the connected
     * end systems, gets the mapped entities and pull the newly created/updated
     * records from the end system, starting from the previous watermark. The
     * records are persisted to avoid re-polling in case of failures
     */

    @Override
    public CurrentBatch fetch(DataSourceRequest request) {
        return null;
    }

    @Override
    public CurrentBatch fetchSource(DataSourceRequest request) {
        return getCurrentBatch(request.getSyncariEntity().getApiName(),
                getEndSystemEntityMap(request.getSourceEntities()), request.getGraph(), request.getSourceParamMap(), request.getSyncStartTime(), request.getSyncCycleId(), request.getAdditionalParamMap());
    }

    @Override
    public CurrentBatch fetchSourceById(DataSourceRequest request) {
        return null;
    }

    @Override
    public CurrentBatch fetchSourceFromTestInput(EntityDefinition syncariEntity, PipelineTest test) {
        return null;
    }


    private CurrentBatch getCurrentBatch(String entityName, Map<Connector, List<EntityDefinition>> systemEntityMap, MappingGraph graph, Map<String, Map<String, Object>> sourceParams, Long syncStartTime, String syncCycleId, Map<String, Map<String, Object>> additionalParams) {

        CurrentBatch currentBatch = new CurrentBatch(recordRepo, stagingRepo,idMappingService, entityRepoService, featureService, externalRecordRepo);

        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(entityName)
                .orElseThrow(() -> new RuntimeException(format("Syncari Entity with name %s not found", entityName)));
        currentBatch.setSyncariEntity(syncariEntity);
        // process newly issued resync if any
        resyncService.processNewResync(syncariEntity.getId());
        Optional<ResyncDetail> resync = resyncService.findProcessingResync(syncariEntity.getId());

        // Check for inactive sources in processing resync
        if (resync.isPresent()) {
            resyncService.validateProcessingResyncSources(syncariEntity.getId(), resync.get());
        }

        Map<String, EntitySchema> mappedFieldsSchemaMap = getMappedFieldsSchemaMap(syncariEntity, systemEntityMap, graph);
        List<EntityFetchResult> results = fetchResults(entityName, currentBatch, systemEntityMap, resync, sourceParams,syncStartTime, mappedFieldsSchemaMap, graph, additionalParams);
        saveStagedBatchRecords(syncariEntity.getDisplayName(),entityName, currentBatch, syncCycleId, resync, results, graph, syncStartTime, sourceParams, mappedFieldsSchemaMap, additionalParams);
        return currentBatch.setSuccess(true).setSyncariEntityName(entityName).setCurrentBatchId(syncCycleId);
    }

    private Map<String, EntitySchema> getMappedFieldsSchemaMap(EntityDefinition syncariEntity, Map<Connector, List<EntityDefinition>> systemEntityMap, MappingGraph entityGraph) {
        Map<String, EntitySchema> sourceWithMappedFieldsMap = new HashMap<>();
        systemEntityMap.forEach((conn, entities) -> {
            entities.forEach(source -> {
                EntitySchema schema = transformer.toEntitySchema(schemaService.getSourceEntityWithMappedAndSystemFields(syncariEntity, source, entityGraph), conn);
                sourceWithMappedFieldsMap.put(source.getId(), schema);
            });
        });
        return  sourceWithMappedFieldsMap;
    }

    protected List<EntityFetchResult> fetchResults(String entityName, CurrentBatch currentBatch,
                                                   Map<Connector, List<EntityDefinition>> systemEntityMap, Optional<ResyncDetail> resync, Map<String, Map<String, Object>> sourceParams, long syncStartTime,
                                                   Map<String, EntitySchema> mappedFieldsSchemaMap, MappingGraph graph, Map<String, Map<String, Object>> additionalParams) {
        List<EntityFetchResult> results = new ArrayList<>();
        long now = Instant.now().minus(findMaxEndWatermark(systemEntityMap), ChronoUnit.SECONDS).toEpochMilli();

        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName(entityName)
                .orElseThrow(() -> new RuntimeException(format("Syncari Entity with name %s not found", entityName)));
        systemEntityMap.forEach((initialConnector, entities) -> {

            for (EntityDefinition entity : entities) {
                Timer metricTimer = new Timer("EntitySource::fetchResults::fetchResults");
                final Connector connector = connectorService.refreshAuthentication(initialConnector);
                DataService dataService = factory.getDataService(connector.getMetadata());

                var upstreamSyncDetail = syncService.findUpstreamWatermark(entityName, entity.getId())
                        .orElseThrow(() -> new RuntimeException(format("No upstream watermark found for external entityId %s", entity.getId())));
                Watermark retrieved = upstreamSyncDetail.getWatermark();
                // check if resync is issued on stream then put the resync in SYNCING status
                boolean isHistoricSyncRunningOnStream = resync.isPresent();
                boolean isHistoricSyncForSourceEntity = resync.map(r -> r.isSourceInProgress(entity.getId())).orElse(false);

                // do not pull data if historicSync is running on stream but not issued for current source
                if (isHistoricSyncRunningOnStream && !isHistoricSyncForSourceEntity) {
                    log.info("Historical Sync is running on stream {} without source entity {}. Skipping it from this cycle", entityName, entity.getId());
                    continue;
                }

                boolean onGoingSync = upstreamSyncDetail.isOnGoingSync();

                var computedWm = Optional.of(retrieved);
                long endTime = isHistoricSyncForSourceEntity ? resync.get().getEndTime().toEpochMilli() : onGoingSync ? upstreamSyncDetail.getEndTime() : now;
                if (retrieved.getOffset() == 0 && StringUtils.isEmpty(retrieved.getChangeStream())) {
                    computedWm = retrieved.moveTo(endTime);
                    if(!isHistoricSyncRunningOnStream && computedWm.isPresent() && !computedWm.get().isInitial()) {
                        // start from X duration behind to ensure we dont miss any updates since some synapses doesnt guarentee reads after writes
                        final int lookBehindDuration = dataService.lookBehindDuration(transformer.toConnectorInfo(connector));
                        long newStart = Math.max(0, Math.min(computedWm.get().getStart(), Instant.now().toEpochMilli() - lookBehindDuration));
                        log.debug("Appling lookBehindDuration {} to {} for {}", lookBehindDuration, newStart, connector.getName());
                        computedWm.get().setStart(newStart);
                    }
                } else {
                    // This is a DO NOT Modify WM window condition. This means it's an offset based iteration and the offset is > 0,
                    // indicating the pagination has begun and need to drain before moving the wm start and end values.
                }
                computedWm.ifPresentOrElse(newWatermark ->{
                    ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
                    connectorInfo.setMetaConfig(new HashMap<>(connector.getMetaConfig()));
                    EntitySchema entitySchema = transformer.toEntitySchema(entity, connector);
                    List<JobDetail> pendingJobs = batchJobService.findUnprocessed(connector.getId(), entity.getApiName());
                    SyncRequest request = new SyncRequest()
                            .Builder(connectorInfo, entitySchema)
                            .setPipeline(new Pipeline(graph.getName(), graph.getDraftStatus().name(), SyncariContext.getSyncariId()))
                            .setWatermark(transformer.toWatermarkInfo(newWatermark))
                            .setSourceParams(sourceParams.getOrDefault(entitySchema.getId(), Map.of()))
                            .setAdditionalParams(additionalParams != null ?additionalParams.getOrDefault(entitySchema.getId(), Map.of()):Map.of())
                            .setEntitySchemaWithMappedFields(mappedFieldsSchemaMap.get(entity.getId()))
                            .setStorage(storage)
                            .setBatchJobs(pendingJobs.stream().map(p -> p.getJob()).collect(Collectors.toList()));
                    Timer timer = new Timer("EntitySource::fetchResults::getByWatermark");

                    FetchResponse resp = withPipelineException(() -> dataService.getByWatermark(request), graph, entity);
                    resp.setTimeTaken(timer.getTimeTakenUntilNow());
                    timer.close();
                    results.add(new EntityFetchResult(entity,request,resp,connector,entitySchema,retrieved, false));
                    currentBatch.setCurrentWatermark(entity, newWatermark);

                    FetchResponse deletedResp = null;
                    deletedResp = withPipelineException(() -> dataService.getDeletedByWatermark(request.copy()), graph, entity);
                    if (deletedResp != null && deletedResp.getIterator() != null && deletedResp.getIterator().hasNext()) {
                        results.add(new EntityFetchResult(entity,request,deletedResp,connector,entitySchema,retrieved.getCopy(), true));
                    }
                    //TODO: Don't prune sources which are batched
                },()->{
                    log.info("Skipping fetch for entity {} from synapse {}, because we are already caught up to {},and current high watermark is {}",entity.getApiName(),connector.getName(),retrieved.getEnd(),now);
                });

                Instant watermarkForMetric = computedWm.isPresent() ? Instant.ofEpochMilli(computedWm.get().getEnd()): Instant.now();
                EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entity.getApiName(),watermarkForMetric,(float)metricTimer.getTimeTakenUntilNow(),
                        0, 0, 0);
                long totalDurationtillNow = Instant.now().toEpochMilli() - syncStartTime;
                syncDetailMetricService.findOrCreateSyncSourceDetails(syncariEntity.getDisplayName(), syncariEntity.getId(),syncariEntity.getApiName(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE,resync.isPresent(), false,
                        currentBatch.getCurrentBatchId(), (float)totalDurationtillNow, 0);
            }
        });
        return results;
    }

    /**
     * TODO: Do away with this, and rely on underlying synapses for this information.
     * @param systemEntityMap
     * @return if salesforce/GS is one of the synapses the skew is 5 minutes, 2 minutes for zendesk, otherwise its 5 seconds
     */
    private long findMaxEndWatermark(Map<Connector, List<EntityDefinition>> systemEntityMap) {
        long maxWatermark = Watermark.CLOCK_SKEW_TOLERANCE_SECONDS; // ideally this should never be used
        for (Map.Entry<Connector, List<EntityDefinition>> entry : systemEntityMap.entrySet()) {
            Connector connector = entry.getKey();
            DataService dataService = factory.getDataService(connector.getMetadata());
            final int clockSkewTolerance = dataService.clockSkewTolerance(transformer.toConnectorInfo(connector));
            if (clockSkewTolerance > maxWatermark) {
                maxWatermark = clockSkewTolerance;
            }
        }
        return maxWatermark;
    }

    protected <T> T withPipelineException(Supplier<T> func, MappingGraph graph, EntityDefinition entityDefinition) {
        try {
            return func.get();
        } catch (Exception e) {
            PipelineException exception = new PipelineException(e).setGraphId(graph.getId()).setScope(graph.getScope());
            graph.getSourceNode(entityDefinition.getId()).ifPresent(node -> exception.setNodeId(node.getId()));
            throw exception;
        }
    }

    protected void saveStagedBatchRecords(String syncariEntityDisplayName, String entityName, CurrentBatch currentBatch, String syncCycleId, Optional<ResyncDetail> resync, List<EntityFetchResult> results,
                                          MappingGraph graph, Long syncStartTime, Map<String, Map<String, Object>> sourceParams, Map<String, EntitySchema> mappedFieldsSchemaMap, Map<String, Map<String, Object>> additinalParams) {
        EntityPageIterator entityPageIterator = new EntityPageIterator(results, resync.isPresent());
        Map<String, StagedBatch> batches = new HashMap<>();
        Map<String, Set<String>> entityDefToRecordIds = new HashMap<>();
        //schemaService.getSyncariEntityByName(entityName).ifPresent(x -> log.info("Graph Target Id is {} and syncariEntityId is {}", graph.getTargetId(), x.getId()));
        results.forEach(entityFetchResult->{
            EntityDefinition entity = entityFetchResult.getEntityDefinition();
            Connector connector = entityFetchResult.getConnector();
            DataService dataService = factory.getDataService(connector.getMetadata());
            Map<String, AttributeDefinition> apiNameToAttrMap = entity.getApiNameLowerCasedToAttributes();

            Set<String> externalRecordIds = pullUnresolvedReferences(entityFetchResult,entityName, apiNameToAttrMap, dataService, batches, syncCycleId,syncStartTime, sourceParams, mappedFieldsSchemaMap, graph, additinalParams);
            requeueService.cleanupAndNotifyExpiredRequests(entity.getId(),graph.getId());
            pullRequeuedRecords(entityFetchResult,entityName, apiNameToAttrMap, dataService, batches, graph.getId(),externalRecordIds, syncCycleId,syncStartTime, sourceParams, mappedFieldsSchemaMap, graph, additinalParams);
            pullEventData(entityFetchResult, entityName, apiNameToAttrMap, batches, externalRecordIds, syncCycleId, graph, currentBatch, syncStartTime);
            Set<String> recordsInThisBatch = new HashSet<>(externalRecordIds);
            entityDefToRecordIds.put(entity.getId(),recordsInThisBatch);
            if(!recordsInThisBatch.isEmpty()) {
                currentBatch.setEntityBatch(entity,batches.get(entity.getId()));
            }
        });
        Timer timer = new Timer("EntitySource::saveStagedBatchRecords::saveStagedBatchRecords");
        HashSet<EntityFetchResult> processedResults = new HashSet<>();
        AtomicInteger numberOfEmptyIdRec = new AtomicInteger(0);

        try {
            while (entityPageIterator.hasNext()) {
                List<EntityPage> nextPage = entityPageIterator.next();
                nextPage.forEach(page -> {
                    EntityFetchResult entityFetchResult = page.getResult();
                    EntityDefinition entity = entityFetchResult.getEntityDefinition();
                    Connector connector = entityFetchResult.getConnector();
                    DataService dataService = factory.getDataService(connector.getMetadata());
                    SyncRequest request = entityFetchResult.getRequest();
                    FetchResponse resp = entityFetchResult.getResponse();
                    if(entityFetchResult.getWatermark() != null && resp != null && resp.getWatermark() != null && resp.getWatermark().getStreamState() != null) {
                        StreamState savedStreamState = entityFetchResult.getWatermark().getStreamState();
                        String previousCursor = resp.getWatermark().getStreamState().getPreviousCursor();
                        boolean offsetOverflow = resp.getWatermark().getStreamState().isOffsetOverflow();

                        if(savedStreamState == null) {
                            entityFetchResult.getWatermark().setStreamState(new StreamState());
                        }
                        entityFetchResult.getWatermark().getStreamState().setPreviousCursor(previousCursor).setOffsetOverflow(offsetOverflow);
                    }
                    Map<String, AttributeDefinition> apiNameToAttrMap = entity.getApiNameLowerCasedToAttributes();
                    if(!batches.containsKey(entity.getId())){

                        log.debug("Fetched prune state last record {}", entityFetchResult.getWatermark()!= null && entityFetchResult.getWatermark().getPruneState() != null ?
                                entityFetchResult.getWatermark().getPruneState().getLastRecordNotPruned() : "");

                        batches.put(entity.getId(),
                                stagingRepo.save(new StagedBatch(entityName).setConnectorId(connector.getId())
                                                .setCurrentBatchId(syncCycleId).setWatermark(entityFetchResult.getWatermark()).setSourceEntityName(entity.getApiName()))
                                        .setSourceEntityDefinitionId(entity.getId())
                        );
                    }
                    StagedBatch staged = batches.get(entity.getId());

                    var stageBatchId = staged.getId();
                    Set<String> recordsInThisBatch = entityDefToRecordIds.getOrDefault(entity.getId(),new HashSet<>());
                    List<EntityData> batchData = page.getRecords();
                    log.info(format("Got %s records for connector %s and entity %s with %s , Using watermark %s", batchData.size(),
                            connector.getName(), entityName, page.getWatermark(),Instant.ofEpochMilli(page.getWatermark())));

                    List<StagedBatchRecord> filteredRecords = new ArrayList<>();
                    Set<String> skippedRecords = new HashSet<>();
                    batchData.forEach(record -> {
                        if (StringUtils.isEmpty(record.getId())){
                            numberOfEmptyIdRec.incrementAndGet();
                        }
                        // is this from resync source, then don't apply skew skip logic
                        boolean skipRecord = !resync.isPresent() && shouldSkipRecord(connector, dataService, entity, record, staged.getEntityName());
                        log.debug("skipRecord {}", skipRecord);
                        if (!recordsInThisBatch.contains(record.getId()) && !skipRecord) {
                            EntityData entityData = helper.fixDatatypes(apiNameToAttrMap, record);
                            List<AttributeDefinition> fileLinks = entity.getFileLinkAttributes();
                            fileLinks.forEach(fileLink -> {
                                //We don't want to persist deleted records
                                if (!record.isDeleted()) {
                                    DocumentRequest docReq = new DocumentRequest(transformer.toConnectorInfo(connector), entityFetchResult.getSchema(), entityData);
                                    if (fileLink.isMultiValueField()) {
                                        {
                                            List<String> filePaths = new ArrayList<>();
                                            Map<String, InputStream> fileContents = dataService.getFileContents(docReq).getContentMap();
                                            for (Map.Entry<String, InputStream> entry : fileContents.entrySet()) {
                                                String filePath = String.format("%s/%s_%s_%s_%s_%s", SyncariContext.getInstance().getSyncariId(),
                                                        connector.getId(), entity.getApiName(), fileLink.getApiName(), entityData.getId(), entry.getKey());
                                                request.getStorage().write(entry.getValue(), filePath);
                                                filePaths.add(filePath);
                                            }
                                            entityData.addValue(fileLink.getApiName(), filePaths);
                                        }
                                    } else {
                                        String filePath = String.format("%s/%s_%s_%s_%s", SyncariContext.getInstance().getSyncariId(),
                                                connector.getId(), entity.getApiName(), fileLink.getApiName(), entityData.getId());
                                        request.getStorage().write(withPipelineException(() -> dataService.getFileContents(docReq), graph, entity).getContents(), filePath);
                                        entityData.addValue(fileLink.getApiName(), filePath);
                                    }
                                }
                            });
                            entityData.setConnectorId(connector.getId());
                            log.debug("Got Record from connector {}:{}, data {} ", connector.getName(), entity.getApiName(),
                                    entityData.getId());
                            recordsInThisBatch.add(entityData.getId());
                            filteredRecords.add(new StagedBatchRecord().setStagedBatchId(stageBatchId)
                                    .setEntityData(entityData)
                                    .setExternalEntityDefinitionId(entity.getId())
                                    .setExternalRecordId(entityData.getId())
                            );
                            if(shouldSkipSkew(connector,dataService) && !entityFetchResult.getWatermark().isInitial() && !entityFetchResult.getWatermark().isResync()) {
                                String key = getFormattedId(connector, entity, entityData, entityName);
                                tempProcessedIdCache.putIfAbsent(syncCycleId, new ArrayList<>());
                                tempProcessedIdCache.get(syncCycleId).add(key);
                            }
                        }else{
                            log.debug("Skipping Record from connector {}:{}, data {} ", connector.getName(), entity.getApiName(),
                                    record.getId());
                            skippedRecords.add(record.getId());
                        }
                    });
                    recordRepo.saveAll(filteredRecords);
                    externalRecordRepo.upsert(helper.toExternal(filteredRecords, graph.getId()), entity);
                    if (!entityFetchResult.isDeletedRecordsBatch()) {
                        if (page.getOffset() > 0 || StringUtils.isNotEmpty(page.getChangeStream())) {
                            // When we are iterating over the offset based queries, we do not want to override the watermark start/end values.
                            staged.setReadOffsetWatermark(currentBatch.getCurrentWatermark().get(entity).getStart(),
                                    currentBatch.getCurrentWatermark().get(entity).getEnd(), page.getOffset());
                        } else {
                            staged.setReadOffsetWatermark(page.getWatermark(), page.getWatermark(), page.getOffset());
                        }
                        staged.setChangeStream(page.getChangeStream());
                    }
                    stagingRepo.save(staged);
                    if (page.size() > 0 || !request.hasPendingJobs()) {
                        currentBatch.setEntityBatch(entity, staged);
                        if (!entityFetchResult.isDeletedRecordsBatch()) {
                            log.info("Setting current watermark for the current batch {} ", staged.getWatermark());
                            Watermark stagedWm = staged.getWatermark();
                            if(page.getLastModified() > 0) {
                                if(stagedWm.getStreamState() == null) {
                                    stagedWm.setStreamState(new StreamState().setLastModified(page.getLastModified()));
                                } else {
                                    stagedWm.getStreamState().setLastModified(page.getLastModified());
                                }
                            }
                            currentBatch.setCurrentWatermark(entity, stagedWm);
                        }
                    } else if (request.hasPendingJobs()) {
                        currentBatch.removeCurrentWatermark(entity);
                    } else {
                        stagingRepo.delete(staged);
                    }
                    processedResults.add(entityFetchResult);
                    helper.logSyncStats(eventStore,currentBatch, entity, connector, resp);
                    int totalProcessedCount = batchData.size();
                    Instant batchWatermark = currentBatch.getCurrentWatermark().containsKey(entity) ? Instant.ofEpochMilli(currentBatch.getCurrentWatermark().get(entity).getEnd()) : Instant.now();
                    Instant lastUpdatedRecordTime = CollectionUtils.isNotEmpty(batchData) ? Instant.ofEpochMilli(batchData.get(batchData.size()-1).getLastModified()) : batchWatermark;
                    Long totalTimeTaken = ((resp.getTimeTaken() > 0) && (!entityPageIterator.hasNext())) ? (timer.getTimeTakenUntilNow() + resp.getTimeTaken()) : timer.getTimeTakenUntilNow();
                    EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entity.getApiName(),batchWatermark,(float)totalTimeTaken,
                            totalProcessedCount, skippedRecords.size(), recordsInThisBatch.size());
                    long totalDurationtillNow = Instant.now().toEpochMilli() - syncStartTime;
                    syncDetailMetricService.findOrCreateSyncSourceDetails(syncariEntityDisplayName, graph.getTargetId(),entityName, syncStatusMetric, EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE,resync.isPresent(), false,
                            syncCycleId, (float)totalDurationtillNow, totalProcessedCount);
                    totalProcessedCount=0;
                    timer.reset();
                });
            }

        } catch (PipelineException e) {
            if (!StringUtils.isBlank(e.getExternalEntityDefinitionId())) {
                e.setGraphId(graph.getId()).setScope(graph.getScope());
                graph.getSourceNode(e.getExternalEntityDefinitionId()).ifPresent(node -> e.setNodeId(node.getId()));
                throw e;
            }
            throw e;
        }

        timer.close();
        results.forEach(result->{
            if(!processedResults.contains(result)){
                entityPageIterator.getIterator(result).ifPresent(iterator->{
                    currentBatch.getCurrentWatermark().get(result.getEntityDefinition()).setOffset(iterator.getLastOffset())
                            .setChangeStream(iterator.getChangeStream());
                });
            }
            List<BatchJob> updatedJobs = result.getResponse().getBatchJobs();
            batchJobService.upsert(updatedJobs);
        });
        // notify number of empty id records
        if (numberOfEmptyIdRec.get() > 0){
            Instance currInstance = SyncariContext.getInstance();
            notificationService.broadcast(i18n("empty_records"),format(i18n("empty_records_body"), numberOfEmptyIdRec.get(),
                    entityName, currInstance.getDisplayName()), NotificationType.WARN);
        }
    }

    private boolean shouldSkipRecord(Connector connector, DataService dataService, EntityDefinition entity, EntityData record, String syncariEntityName) {
        return shouldSkipSkew(connector, dataService) && processedIdCache.getIfPresent(getFormattedId(connector, entity, record, syncariEntityName)) !=null;
    }

    private boolean shouldSkipSkew(Connector connector, DataService dataService) {
        // This should be a whitelist of synapses eventually and a better way of metadata driven approach. Currently its sfdc, when we
        // have a second usecase we'll expose synapse api
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
        return dataService.lookBehindDuration(connectorInfo) > 0;
    }

    private String getFormattedId(Connector connector, EntityDefinition entity, EntityData record, String syncariEntityName) {
        return String.format("%s_%s_%s_%s_%s_%s", SyncariContext.getSyncariId(), connector.getId(), syncariEntityName, entity.getId(), record.getId(), record.getLastModified());
    }

    protected Set<String> pullUnresolvedReferences(EntityFetchResult entityFetchResult, String entityName, Map<String, AttributeDefinition> apiNameToAttrMap,
                                                   DataService dataService, Map<String, StagedBatch> batches, String syncCycleId, long syncStartTime, Map<String, Map<String, Object>> sourceParams, Map<String, EntitySchema> mappedFieldsSchemaMap, MappingGraph graph,
                                                   Map<String, Map<String, Object>> additionalParams) {


        Connector connector = entityFetchResult.getConnector();
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
        EntityDefinition externalEntityDefinition = entityFetchResult.getEntityDefinition();
        EntitySchema entitySchema = entityFetchResult.getSchema();
        Set<String> externalRecordIds = new HashSet<>();
        schemaService.getSyncariEntityByName(entityName).ifPresent(syncariEntity -> {
            int totalProcessed = 0;
            int totalPulled = 0;
            String nextUnresolvedReferenceId = "";
            List<UnresolvedReference> unresolvedReferences = new ArrayList<>();
            Timer timer = new Timer("EntitySource::pullUnresolvedReferences::pageiterator");
            Long watermark = Instant.now().toEpochMilli();
            Map<String, UnresolvedReference> unresolvedReferenceMap = new HashMap<>();
            SyncRequest syncRequest = new SyncRequest().setConnector(connectorInfo);
            syncRequest.setEntitySchema(entitySchema);
            //syncRequest.setEntitySchemaWithMappedFields(transformer.toEntitySchema(schemaService.getSourceEntityWithMappedAndSystemFields(entityName, externalEntityDefinition.getId(), true), connector));
            syncRequest.setEntitySchemaWithMappedFields(mappedFieldsSchemaMap.get(externalEntityDefinition.getId()));
            syncRequest.setSourceParams(sourceParams.getOrDefault(entitySchema.getId(), Map.of()));
            syncRequest.setAdditionalParams(additionalParams.getOrDefault(entitySchema.getId(), Map.of()));
            do {
                unresolvedReferences = unresolvedReferenceService.getUnresolvedReferencesFor(
                        nextUnresolvedReferenceId, connectorInfo.getId(), externalEntityDefinition.getApiName(), UnresolvedReferenceService.PAGE_SIZE);

                totalPulled += unresolvedReferences.size();

                // filter out unresolved references that do not point to this Syncari Entity
                unresolvedReferences = unresolvedReferences.stream().filter(r -> StringUtils.isEmpty(r.referredSyncariEntity) || r.referredSyncariEntity.equals(entityName)).collect(Collectors.toList());

                if (unresolvedReferences.size() > 0) {
                    Set<String> idsInCurrrentPage = new HashSet<>();
                    unresolvedReferences.forEach(unresolvedReference -> {
                        //exclude dupes from both current page and all resolved refs so far
                        if (!idsInCurrrentPage.contains(unresolvedReference.getExternalRefRecordId()) && !externalRecordIds.contains(unresolvedReference.getExternalRefRecordId())) {
                            syncRequest.addData(connectorInfo.getId(),
                                    new EntityData(externalEntityDefinition.getApiName())
                                            .setId(unresolvedReference.getExternalRefRecordId())
                                            .setConnectorId(connectorInfo.getId()));
                        }
                        idsInCurrrentPage.add(unresolvedReference.getExternalRefRecordId());
                        unresolvedReferenceMap.put(unresolvedReference.getExternalRefRecordId(), unresolvedReference);
                    });
                    log.info("Found {} unresolved references for external entity {} on connector {}, Total idsInCurrentPage: {}",
                            unresolvedReferences.size(), externalEntityDefinition.getDisplayName(), connectorInfo.getName(),idsInCurrrentPage.size());
                    List<EntityData> recordsById = new ArrayList<>();
                    try {
                        if(!syncRequest.getIds().isEmpty()) {
                            recordsById.addAll(dataService.getByIds(syncRequest));
                        }
                    } catch (NotSupportedException e) {
                        log.error(e.getMessage(), e);
                    } catch(Exception e) {
                        PipelineException exception = new PipelineException(e).setGraphId(graph.getId()).setScope(graph.getScope());
                        graph.getSourceNode(externalEntityDefinition.getId()).ifPresent(node -> exception.setNodeId(node.getId()));
                        throw exception;
                    }
                    if(!batches.containsKey(externalEntityDefinition.getId())){
                        batches.put(externalEntityDefinition.getId(),
                                stagingRepo.save(new StagedBatch(entityName).setConnectorId(connector.getId())
                                                .setCurrentBatchId(syncCycleId).setWatermark(entityFetchResult.getWatermark()).setSourceEntityName(externalEntityDefinition.getApiName()))
                                        .setSourceEntityDefinitionId(externalEntityDefinition.getId())
                        );
                        watermark = entityFetchResult.getWatermark().getEnd();
                    }
                    incrementRetriesForUnresolvedRecords(recordsById, unresolvedReferenceMap);
                    log.info("Total records found by getByIds in this cycle: {}", recordsById.size());
                    String stageBatchId = batches.get(externalEntityDefinition.getId()).getId();
                    final List<StagedBatchRecord> records = recordRepo.saveAll(map(recordsById, d -> {
                        EntityData entityData = helper.fixDatatypes(apiNameToAttrMap, d);
                        entityData.setConnectorId(connectorInfo.getId());
                        log.debug("Getting records for unresolved references for {} entity {} record {}", connectorInfo.getName(), externalEntityDefinition.getApiName(),
                                entityData);
                        //side effect!
                        externalRecordIds.add(d.getId());

                        List<AttributeDefinition> fileLinks = entityFetchResult.getEntityDefinition().getFileLinkAttributes();
                        fileLinks.forEach(fileLink -> {
                            //We don't want to persist deleted records
                            if (!entityData.isDeleted()) {
                                DocumentRequest docReq = new DocumentRequest(transformer.toConnectorInfo(connector), entityFetchResult.getSchema(), entityData);
                                if(fileLink.isMultiValueField()){ {
                                    List<String> filePaths = new ArrayList<>();
                                    Map<String, InputStream> fileContents = dataService.getFileContents(docReq).getContentMap();
                                    for (Map.Entry<String, InputStream> entry : fileContents.entrySet()) {
                                        String filePath = String.format("%s/%s_%s_%s_%s_%s", SyncariContext.getInstance().getSyncariId(),
                                                connector.getId(), entitySchema.getApiName(), fileLink.getApiName(), entityData.getId(), entry.getKey());
                                        storage.write(entry.getValue(), filePath);
                                        filePaths.add(filePath);
                                    }
                                    entityData.addValue(fileLink.getApiName(), filePaths);
                                }} else {
                                    String filePath = String.format("%s/%s_%s_%s_%s", SyncariContext.getInstance().getSyncariId(),
                                            connector.getId(), entitySchema.getApiName(), fileLink.getApiName(), entityData.getId());
                                    storage.write(dataService.getFileContents(docReq).getContents(), filePath);
                                    entityData.addValue(fileLink.getApiName(), filePath);
                                }
                            }
                        });
                        return new StagedBatchRecord().setStagedBatchId(stageBatchId).setExternalRecordId(entityData.getId())
                                .setEntityData(entityData).setExternalEntityDefinitionId(externalEntityDefinition.getId());
                    }));
                    externalRecordRepo.upsert(helper.toExternal(records, null), externalEntityDefinition);
                    totalProcessed+=records.size();
                    nextUnresolvedReferenceId = unresolvedReferences.get(unresolvedReferences.size() - 1).getId();
                    syncRequest.clearData();
                }
            }
            while (unresolvedReferences.size() > 0 && totalProcessed <= 9000);
            updateUnresolvedReferences(unresolvedReferenceMap);
            log.debug("Unresolved references stats. Total pulled from unresolvedReferences: {}, Total processed from the external source: {}",
                    totalPulled, totalProcessed);
            EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),externalEntityDefinition.getApiName(),Instant.ofEpochMilli(watermark),(float)timer.getTimeTakenUntilNow(),
                    totalProcessed, 0, totalProcessed);
            long totalDurationtillNow = Instant.now().toEpochMilli() - syncStartTime;
            syncDetailMetricService.findOrCreateSyncSourceDetails(entityName, syncariEntity.getId(),entityName, syncStatusMetric, EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE,
                    false, false, syncCycleId, (float)totalDurationtillNow,totalProcessed);
            timer.close();
        });
        return externalRecordIds;
    }

    private void updateUnresolvedReferences(Map<String, UnresolvedReference> unresolvedReferenceMap) {
        if(!unresolvedReferenceMap.isEmpty()) {
            var partioned = Lists.partition(new ArrayList<>(unresolvedReferenceMap.values()), UNRESOLVED_REFERENCE_BATCH_SIZE);
            partioned.forEach(partition -> unresolvedReferenceRepo.upsertUnResolved(partition));
        }
    }

    private void incrementRetriesForUnresolvedRecords(List<EntityData> recordsById, Map<String, UnresolvedReference> unresolvedReferenceMap) {
        Set<String> resolvedRecords = recordsById.stream().map(record -> record.getId()).collect(Collectors.toSet());
        Set<String> unresolvedReferenceIds = unresolvedReferenceMap.keySet();
        unresolvedReferenceIds.removeAll(resolvedRecords);
        unresolvedReferenceIds.forEach(id -> {
            UnresolvedReference unresolvedReference = unresolvedReferenceMap.get(id);
            unresolvedReference.incrementRetries();
        });
    }

    protected void pullEventData(EntityFetchResult entityFetchResult, String entityName,
                                 Map<String, AttributeDefinition> apiNameToAttrMap, Map<String, StagedBatch> batches,
                                 Set<String> externalRecordIds, String syncCycleId, MappingGraph graph, CurrentBatch currentBatch, long syncStartTime) {
        Connector connector = entityFetchResult.getConnector();
        EntityDefinition externalEntityDefinition = entityFetchResult.getEntityDefinition();
        // TODO: Put a limit of page numbers here?
        long watermark = Instant.now().toEpochMilli();

        //TODO: Increasing this temporarily for Deepgram, bring it back to 2K
        int maxEventsPerConnector = 1000;
        int numEvents = 0;
        Pageable pageReq = null;
        Integer totalEventsPulled = 0;
        do {
            Timer timer = new Timer("EntitySource::pullEventData::pageiterator");
            //TODO: Increasing this temporarily for Deepgram, bring it back to 1K
            pageReq = (pageReq == null) ? PageRequest.of(0, 3000, Sort.by(Sort.Order.asc("createdAt"))) : pageReq.next();

            String sourceEntityName = externalEntityDefinition.getApiName();
            List<EventData> content = readEventRecords(connector.getId(), graph.getId(), sourceEntityName, pageReq, syncCycleId);
            if(content.isEmpty()) return;
            if (!content.isEmpty()) {
                watermark = content.get(content.size() - 1).getData().getLastModified();
            }
            numEvents += content.size();
            log.info("Read {} records Event Data for Connector {} GraphId {} Total events read {}", content.size(), connector.getId(), graph.getId(), numEvents);

            if (!batches.containsKey(externalEntityDefinition.getId())) {
                batches.put(externalEntityDefinition.getId(),
                        stagingRepo
                                .save(new StagedBatch(entityName).setConnectorId(connector.getId())
                                        .setCurrentBatchId(syncCycleId).setWatermark(entityFetchResult.getWatermark())
                                        .setSourceEntityName(externalEntityDefinition.getApiName()))
                                .setSourceEntityDefinitionId(externalEntityDefinition.getId()));
            }
            String stageBatchId = batches.get(externalEntityDefinition.getId()).getId();
            List<StagedBatchRecord> saved = recordRepo.saveAll(map(content, d -> {
                EntityData entityData = helper.fixDatatypes(apiNameToAttrMap, d.getData());
                entityData.setConnectorId(connector.getId());
                log.debug("Getting records for event data for {} entity {} record {}", connector.getName(),
                        externalEntityDefinition.getApiName(), entityData);
                externalRecordIds.add(d.getId());
                StagedBatchRecord batchRecord = new StagedBatchRecord().setStagedBatchId(stageBatchId).setExternalRecordId(entityData.getId())
                        .setEntityData(entityData).setExternalEntityDefinitionId(externalEntityDefinition.getId());
                return batchRecord;
            }));
            externalRecordRepo.upsert(helper.toExternal(saved, graph.getId()), externalEntityDefinition);
            log.info("Saved event data records to StagedBatch ID {}", stageBatchId);
            totalEventsPulled = CollectionUtils.isNotEmpty(content) ? content.size() : 0;
            EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),externalEntityDefinition.getApiName(),Instant.ofEpochMilli(watermark),(float)timer.getTimeTakenUntilNow(),
                    totalEventsPulled, 0, totalEventsPulled);
            long totalDurationtillNow = Instant.now().toEpochMilli() - syncStartTime;
            syncDetailMetricService.findOrCreateSyncSourceDetails(entityName, graph.getTargetId(),entityName, syncStatusMetric, EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE,false, false, syncCycleId, (float)totalDurationtillNow,totalEventsPulled);
            timer.close();
        } while (numEvents < maxEventsPerConnector);

        // set watermark for this batch
        //TODO: Potentially a hack, look at this again. If the enitity does not have watermark then use the last modified from pulled data
        if (batches.containsKey(externalEntityDefinition.getId()) && externalEntityDefinition.getWatermarkField().isEmpty()) {
            var stagedBatch = batches.get(externalEntityDefinition.getId());
            Watermark wm = new Watermark().setStart(watermark).setEnd(watermark);
            stagedBatch.setWatermark(wm);
            stagingRepo.save(stagedBatch);
            currentBatch.setCurrentWatermark(externalEntityDefinition, wm);
        }
    }

    private List<EventData> readEventRecords(String connectorId, String graphId, String entityName, Pageable page, String syncCycleId) {

        var pageEvent = eventDataService.findAllByConnectorIdAndGraphId(connectorId, graphId, page);
        if (pageEvent.isEmpty()) return List.of();

        var events= pageEvent.getContent();
        // filter events by entity name
        var entityEvents = events.stream()
                .filter(e -> !StringUtils.isBlank(e.getData().getName()) && e.getData().getName().equals(entityName))
                .collect(Collectors.toList());

        var squishedEvents =  entityEvents.stream()
                .filter(e -> !StringUtils.isBlank(e.getData().getId()))
                .collect(Collectors.groupingBy(e -> e.getData().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream().map(l -> l.get(l.size() -1)).collect(Collectors.toList());

        if (!entityEvents.isEmpty()){
            log.info("Saving {} events by batchId {}", events.size(), syncCycleId);
            eventDataService.updateByBatchId(entityEvents, syncCycleId);
        }


        return squishedEvents;
    }

    protected Set<String> pullRequeuedRecords(EntityFetchResult entityFetchResult, String entityName, Map<String, AttributeDefinition> apiNameToAttrMap,
                                              DataService dataService, Map<String, StagedBatch> batches, String graphId, Set<String> externalRecordIds, String syncCycleId, long syncStartTime, Map<String, Map<String, Object>> sourceParams,
                                              Map<String, EntitySchema> mappedFieldsSchemaMap, MappingGraph entityGraph, Map<String, Map<String, Object>> additionalParams) {

        EntityDefinition externalEntityDefinition = entityFetchResult.getEntityDefinition();
        Connector connector = entityFetchResult.getConnector();
        EntitySchema entitySchema = entityFetchResult.getSchema();
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
        schemaService.getSyncariEntityByName(entityName).ifPresent(syncariEntity -> {
            Long watermark = Instant.now().toEpochMilli();
            SyncRequest syncRequest = new SyncRequest().setConnector(connectorInfo);
            syncRequest.setEntitySchema(entitySchema);
            //syncRequest.setEntitySchemaWithMappedFields(transformer.toEntitySchema(schemaService.getSourceEntityWithMappedAndSystemFields(entityName, externalEntityDefinition.getId(), true), connector));
            syncRequest.setEntitySchemaWithMappedFields(mappedFieldsSchemaMap.get(externalEntityDefinition.getId()));
            syncRequest.setSourceParams(sourceParams.getOrDefault(entitySchema.getId(), Map.of()));
            syncRequest.setAdditionalParams(additionalParams.getOrDefault(entitySchema.getId(), Map.of()));
            RequeuedSourcePage currentPage = getSourceRequeueRequests(graphId, externalEntityDefinition);
            Set<String> processedSoFar = new HashSet<>();
            int total=0;
            Timer timer = new Timer("EntitySource::pullRequeuedRecords::pageiterator");
            while (currentPage.hasContent()) {
                syncRequest.getData().clear();
                log.info("Found {} records to retry {} on connector {}", currentPage.getNumberOfElements(), externalEntityDefinition.getDisplayName(), connectorInfo.getName());
                Map<String, RequeueRequest> recordIdToRequeueRequest = new HashMap<>();
                currentPage.getCurrentPage().forEach(requeueRequest -> {
                    //exclude dupes from both current page and all resolved refs so far
                    if (!externalRecordIds.contains(requeueRequest.getRecordId()) && !processedSoFar.contains(requeueRequest.getRecordId())) {
                        syncRequest.addData(connectorInfo.getId(),
                                new EntityData(externalEntityDefinition.getApiName())
                                        .setId(requeueRequest.getRecordId())
                                        .setConnectorId(connectorInfo.getId()));
                        recordIdToRequeueRequest.put(requeueRequest.getRecordId(), requeueRequest);
                    }

                    processedSoFar.add(requeueRequest.getRecordId());
                });
                List<EntityData> recordsById = new ArrayList<>();
                try {
                    if (!syncRequest.getIds().isEmpty()) {
                        recordsById.addAll(dataService.getByIds(syncRequest));
                    }
                } catch (NotSupportedException e) {
                    log.error(e.getMessage(), e);
                } catch (NonRetriableException e) {
                    // this is a hack. works only for custom synapses that send this message back. Need more uniform handling
                    if (e.getMessage() != null && (e.getMessage().contains("not supported for entity") || e.getMessage().contains("Invalid response type for request"))) {
                        log.error(e.getMessage(), e);
                    } else {
                        PipelineException exception = new PipelineException(e).setGraphId(entityGraph.getId()).setScope(entityGraph.getScope());
                        entityGraph.getSourceNode(externalEntityDefinition.getId()).ifPresent(node -> exception.setNodeId(node.getId()));
                        throw exception;
                    }
                }
                if(!batches.containsKey(externalEntityDefinition.getId())){
                    batches.put(externalEntityDefinition.getId(),
                            stagingRepo.save(new StagedBatch(entityName).setConnectorId(connector.getId())
                                            .setCurrentBatchId(syncCycleId).setWatermark(entityFetchResult.getWatermark()).setSourceEntityName(externalEntityDefinition.getApiName()))
                                    .setSourceEntityDefinitionId(externalEntityDefinition.getId())
                    );
                    watermark = entityFetchResult.getWatermark().getEnd();
                }
                String stageBatchId = batches.get(externalEntityDefinition.getId()).getId();

                List<StagedBatchRecord> saved = recordRepo.saveAll(map(recordsById, d -> {
                    EntityData entityData = helper.fixDatatypes(apiNameToAttrMap, d);
                    entityData.setConnectorId(connectorInfo.getId());
                    log.debug("Getting records to retry for {} entity {} record {}", connectorInfo.getName(), externalEntityDefinition.getApiName(),
                            entityData);
                    //side effect!
                    externalRecordIds.add(d.getId());
                    List<AttributeDefinition> fileLinks = entityFetchResult.getEntityDefinition().getFileLinkAttributes();
                    fileLinks.forEach(fileLink -> {
                        //We don't want to persist deleted records
                        if (!entityData.isDeleted()) {
                            DocumentRequest docReq = new DocumentRequest(transformer.toConnectorInfo(connector), entityFetchResult.getSchema(), entityData);
                            if(fileLink.isMultiValueField()){ {
                                List<String> filePaths = new ArrayList<>();
                                Map<String, InputStream> fileContents = dataService.getFileContents(docReq).getContentMap();
                                for (Map.Entry<String, InputStream> entry : fileContents.entrySet()) {
                                    String filePath = String.format("%s/%s_%s_%s_%s_%s", SyncariContext.getInstance().getSyncariId(),
                                            connector.getId(), entitySchema.getApiName(), fileLink.getApiName(), entityData.getId(), entry.getKey());
                                    storage.write(entry.getValue(), filePath);
                                    filePaths.add(filePath);
                                }
                                entityData.addValue(fileLink.getApiName(), filePaths);
                            }} else {
                                String filePath = String.format("%s/%s_%s_%s_%s", SyncariContext.getInstance().getSyncariId(),
                                        connector.getId(), entitySchema.getApiName(), fileLink.getApiName(), entityData.getId());
                                storage.write(dataService.getFileContents(docReq).getContents(), filePath);
                                entityData.addValue(fileLink.getApiName(), filePath);
                            }
                        }
                    });
                    return new StagedBatchRecord().setStagedBatchId(stageBatchId)
                            .setExternalRecordId(entityData.getId())
                            .setEntityData(entityData)
                            .setExternalEntityDefinitionId(externalEntityDefinition.getId())
                            .setRequeued(true)
                            .setRequeueRequest(recordIdToRequeueRequest.get(entityData.getId()));

                }));
                externalRecordRepo.upsert(helper.toExternal(saved, null), externalEntityDefinition);
                total+=currentPage.getNumberOfElements();

                currentPage = currentPage.hasNext() && total < MAX_REQUEUED_RECORDS_PER_CYCLE ? getSourceRequeueRequests(graphId, externalEntityDefinition, currentPage) : RequeuedSourcePage.EMPTY;
            }
            EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(connector.getId(), connector.getName(), externalEntityDefinition.getApiName(), Instant.ofEpochMilli(watermark), (float) timer.getTimeTakenUntilNow(),
                    total, 0, total);
            long totalDurationtillNow = Instant.now().toEpochMilli() - syncStartTime;
            syncDetailMetricService.findOrCreateSyncSourceDetails(entityName, syncariEntity.getId(), entityName, syncStatusMetric, EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, syncCycleId, (float) totalDurationtillNow, total);
            timer.close();
        });
        return externalRecordIds;
    }

    /**
     * Composite fetcher for requeuerequests that have not expired, and the ones that have expired, but are flagged to be processed thru the pipeline
     *
     * @param graphId
     * @param externalEntityDefinition
     * @param currentPage
     * @return RequeuedSourcePage - a wrapper Page with a flag to switch over to expired, flagged requests once we
     * exhaust unexpired requeued requests
     */
    protected RequeuedSourcePage getSourceRequeueRequests(String graphId, EntityDefinition externalEntityDefinition, RequeuedSourcePage currentPage) {
        if (!currentPage.fetchExpired) {
            final Page<RequeueRequest> curr = currentPage.currentPage;
            if (curr.hasNext()) {
                return new RequeuedSourcePage(requeueService.findSourceRequeueRequests(externalEntityDefinition.getId(), graphId, currentPage.getCurrentPage().nextPageable()));
            } else {
                return new RequeuedSourcePage(true, requeueService.findExpiredSourceRequeueRequestsToProcess(externalEntityDefinition.getId(), graphId));
            }
        } else {
            return new RequeuedSourcePage(true, requeueService.findExpiredSourceRequeueRequestsToProcess(externalEntityDefinition.getId(), graphId, currentPage.getCurrentPage().nextPageable()));
        }
    }

    protected RequeuedSourcePage getSourceRequeueRequests(String graphId, EntityDefinition externalEntityDefinition) {
        final RequeuedSourcePage requeuedSourcePage = new RequeuedSourcePage(requeueService.findSourceRequeueRequests(externalEntityDefinition.getId(), graphId));
        if (requeuedSourcePage.hasContent()) {
            return requeuedSourcePage;
        }
        return new RequeuedSourcePage(true, requeueService.findExpiredSourceRequeueRequestsToProcess(externalEntityDefinition.getId(), graphId));
    }

    /**
     * An api which does the post processing required after the batchOld pipeline
     * processing is complete - updates the watermark for all the entities and their
     * end systems - deletes the batchOld from the persistent store
     */
    public void closeSource(GraphContext graphContext) {
        CurrentBatch batch = graphContext.getCurrentBatch();
        final String entityName = batch.getSyncariEntityName();
        EntityDefinition syncariEntity = schemaService.getEntityByName(connectorService.getSyncariConnector().getId(), entityName)
                .orElseThrow(() -> new RuntimeException(format("No syncari entity found by name %s", entityName)));
        Optional<ResyncDetail> resyncDetail = resyncService.findProcessingOrCancelRequestedResync(syncariEntity.getId());
        List<StagedBatch> findById = stagingRepo.findByCurrentBatchId(batch.getCurrentBatchId());
        if (findById.isEmpty()) {
            log.warn("Empty Batch for {}", batch.getSyncariEntityName());
        }

        if(tempProcessedIdCache.containsKey(graphContext.getCurrentBatch().getCurrentBatchId())) {
            tempProcessedIdCache.get(graphContext.getCurrentBatch().getCurrentBatchId()).forEach(key -> processedIdCache.put(key, key));
            tempProcessedIdCache.remove(graphContext.getCurrentBatch().getCurrentBatchId());
        }
        if(resyncDetail.isPresent() && ResyncStatus.CANCEL_REQUESTED.equals(resyncDetail.get().getStatus())) {
            resyncService.cancel(syncariEntity, true);
            batch.getCurrentWatermark().forEach((entity, b) -> {
                batchJobService.removeConsumed(entity.getConnectorId(), entity.getApiName());
            });
        } else {
            batch.getCurrentWatermark().forEach((entity, b) -> {
                resyncDetail.ifPresent(resync -> {
                    boolean isResyncFinished = resyncService.isComplete(resync, b);
                    // if resyncFinished for the SYNCING sourceEntity then reset flags and mark it success
                    if (ResyncStatus.PROCESSING.equals(resync.getEntitiesToResync().get(entity.getId())) && isResyncFinished) {
                        // tip over point from historical to incremental sync.
                        // We need to always set the watermark as endTime of resync to make sure there is no gap during switchover.
                        b.setInitial(false).setResync(false)
                                // SYN-6196 tipover from historic to incremental sync sometimes have startWm greater than resync end time which causes issue. Explicitly set the startWm to min of startWm and resync endTime
                                .setStart(resync.getEndTime().toEpochMilli())
                                .setEnd(resync.getEndTime().toEpochMilli());
                        resyncService.success(entityName, entity.getId());
                        log.info("Switching to incremental sync for sourceEntity {} with Id {}", entity.getApiName(), entity.getId());

                        // reset ongoing sync if resync finished successfully
                        var syncDetail = syncService.findUpstreamWatermark(entityName, entity.getId());
                        syncDetail.ifPresent(s -> {
                            if (s.isOnGoingSync()) {
                                s.setOnGoingSync(false);
                                s.setStartTime(0);
                                s.setEndTime(0);
                                syncService.save(s);
                            }
                        });
                    }
                });

                Optional<SyncDetail> syncDetail = syncService.findUpstreamWatermark(entityName, entity.getId());
                syncDetail.ifPresent(s -> {
                    boolean isSyncFinished = syncService.isSyncDone(s, b);
                    if(isSyncFinished) {
                        b.setStart(Math.min(b.getStart(), s.getEndTime()));
                        b.setEnd(s.getEndTime());
                        s.setOnGoingSync(false);
                        s.setStartTime(0);
                        s.setEndTime(0);
                        log.info("Ongoing Sync finished for external entity {} watermark start {} end {}", b.getStart(), b.getEnd());
                        syncService.save(List.of(s));
                    }
                });
                Watermark updatedWm = syncService.updateWatermark(entity, entityName, b);
                log.info(format("Successfully updated watermark for %s with %s", entity.getApiName(), updatedWm));

                closeDataService(graphContext, entity, updatedWm);

                batchJobService.removeConsumed(entity.getConnectorId(), entity.getApiName());
            });
        }
        eventDataService.deleteByBatchId(batch.getCurrentBatchId());
        batch.getEntityBatches().forEach((entity, b) -> {
            stagingRepo.save(b);
        });
    }

    private void closeDataService(GraphContext graphContext, EntityDefinition entity, Watermark updatedWm) {
        try {
            if(graphContext.getGraph() == null) return;
            Pipeline pipeline = new Pipeline(graphContext.getGraph().getName(), graphContext.getGraph().getDraftStatus().name(),
                    SyncariContext.getSyncariId());
            connectorService.find(entity.getConnectorId()).map(connector -> {
                CommonDataService dataService = (CommonDataService) factory.getDataService(connector.getMetadata());
                dataService.close(new CloseContext(transformer.toConnectorInfo(connector), transformer.toWatermarkInfo(updatedWm), pipeline, entity.getApiName()));
                return  connector;
            });
        } catch (Exception e) {
            log.error("Exception while closing service {}", ExceptionUtils.getStackTrace(e));
            throw e;
        }
    }

    private void sendCompletionEmail(Connector connector, EntityDefinition entity, String userId) {
        String syncariLogoUrl = String.format(GlobalConstants.SYNCARI_LOGO, appConfig.getCloudCdnHost());
        Map<String, Object> context = Map.of("connectorName", connector.getName(), "entityName",
                entity.getDisplayName(), "dashboardUrl", appConfig.getSpectrumServerHost(), "syncariLogoUrl", syncariLogoUrl);
        String body = renderer.render(SYNC_COMPLETION_TEMPLATE_PATH, context);
        String subject = format(i18n("initial_sync_complete"), entity.getDisplayName());
        emailService.sendHtml(List.of(userService.getUserById(userId).getEmail()), subject, body);
    }

    protected Map<Connector, List<EntityDefinition>> getEndSystemEntityMap(List<EntityDefinition> entities) {
        return entities.stream()
                // group entities by connectorId
                .collect(Collectors.groupingBy(e -> e.getConnectorId()))
                // create pairs of connector object and entities in that connector
                .entrySet().stream().map(e -> Pair.of(connectorService.get(e.getKey()), e.getValue()))
                .filter(p -> p.x.getStatus() == ConnectorStatus.ACTIVE)
                // collect the pairs into a map
                .collect(Collectors.toMap(e -> e.x, e -> e.y));
    }

    <T> Iterable<T> toIterable(Stream<T> stream) {
        return () -> stream.iterator();
    }

}

@Data
@AllArgsConstructor
class RequeuedSourcePage {

    public static final RequeuedSourcePage EMPTY = new RequeuedSourcePage(Page.empty());
    boolean fetchExpired = false;
    Page<RequeueRequest> currentPage;

    public RequeuedSourcePage(Page<RequeueRequest> currentPage) {
        this.currentPage = currentPage;
    }

    public boolean hasContent() {
        return currentPage.hasContent();
    }

    public int getNumberOfElements() {
        return currentPage.getNumberOfElements();
    }

    public boolean hasNext() {
        //either there is a next page in the current iterator,
        //or we have only exhausted unexpired ones, and there are potential expired ones
        //the next page of expired ones might also be empty, and that's okay
        return currentPage.hasNext() || !fetchExpired;
    }
}