package com.syncari.viper;

import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.KillSwitches;
import akka.stream.Materializer;
import akka.stream.UniqueKillSwitch;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.WebhookRequest;
import com.syncari.connector.exception.InternalRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.DataSourceRequest;
import com.syncari.core.DataTransformer;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Publisher;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.model.*;
import com.syncari.core.model.SyncStream.Status;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.util.SchedulingType;
import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.RealtimeSyncContext;
import com.syncari.core.pipeline.TestContext;
import com.syncari.core.pipeline.TestResultProcessor;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;
import com.syncari.core.repositories.syncari.GlobalConfigurationRepo;
import com.syncari.core.service.*;
import com.syncari.core.sync.*;
import com.syncari.core.utils.ScheduleUtils;
import com.syncari.core.webhook.receiver.WebhookReceiverService;
import com.syncari.utils.Timer;
import com.syncari.utils.Timers;
import com.syncari.viper.streams.PipelineExecutionFactory;
import com.syncari.viper.streams.PipelineStages;
import com.syncari.viper.streams.operators.CollectWhile;
import com.syncari.viper.streams.stages.PipelineUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.viper.ViperUtil.withPipelineException;


@Component
@Slf4j
public class GraphRunner {
    public static final int POLLING_INTERVAL = 60;
    public static final long CHECKIN_INTERVAL = Duration.ofMinutes(2).getSeconds() * 1000;
    private static final long SYNC_CYCLE_ALERT_THRESHOLD = Duration.ofMinutes(60).getSeconds();
    private static final String SCHEDULED_SOURCES = "scheduledSources";

    Materializer materializer;
    EntitySource entitySource;
    SampleEntitySource sampleEntitySource;

    RealTimeEntitySource realTimeEntitySource;
    SchemaService schemaService;
    EventStore eventStore;
    StreamService streamService;
    GlobalConfigurationRepo globalConfigurationRepo;
    InstanceConfigurationService instanceConfigurationService;
    MappingGraphService graphService;
    ConnectorService conService;
    EntityRepo entityRepo;
    DatastoreService dataStoreService;
    PipelineTestService pipelineTestService;
    NotificationService notifyService;
    WatermarkService watermarkService;
    UserService userService;
    BatchJobService batchJobService;
    Publisher publisher;
    PipelineExecutionFactory pipelineExecutionFactory;
    SyncDetailRepo syncDetailRepo;
    TestResultProcessor testResultProcessor;
    IdMappingService idMappingService;
    EmailService emailService;
    AppConfig appConfig;
    SyncDetailMetricService syncDetailMetricService;
    ResyncService resyncService;
    SubscriptionService subService;
    ErrorNotificationService errorNotificationService;
    DataServiceFactory factory;
    DataTransformer transformer;
    EncryptionService encryptionService;
    ApplicationContext applicationContext;
    ServiceCredentialService serviceCredentialService;

    PipelineUtil pipelineUtil;

    PipelineNodeAuditService nodeAuditService;

    WebhookReceiverService webhookReceiverService;

    DataTransformer dataTransformer;

    @Autowired
    public GraphRunner(Materializer materializer, EntitySource entitySource, SampleEntitySource sampleEntitySource, RealTimeEntitySource realTimeEntitySource, SchemaService schemaService,
                       EventStore eventStore, StreamService streamService,
                       GlobalConfigurationRepo globalConfigurationRepo, InstanceConfigurationService instanceConfigurationService,
                       MappingGraphService graphService, ConnectorService conService, EntityRepo entityRepo, DatastoreService dataStoreService,
                       PipelineTestService pipelineTestService, NotificationService notifyService,
                       WatermarkService watermarkService, UserService userService, BatchJobService batchJobService,
                       Publisher publisher, PipelineExecutionFactory pipelineExecutionFactory, SyncDetailRepo syncDetailRepo,
                       TestResultProcessor testResultProcessor, IdMappingService idMappingService, @Qualifier("defaultEmailService") EmailService emailService, AppConfig appConfig,
                       SyncDetailMetricService syncDetailMetricService, ResyncService resyncService, SubscriptionService subService,
                       ErrorNotificationService errorNotificationService, DataServiceFactory dataServiceFactory, DataTransformer transformer,
                       EncryptionService encryptionService, ApplicationContext applicationContext, ServiceCredentialService serviceCredentialService,
                       PipelineNodeAuditService nodeAuditService, WebhookReceiverService webhookReceiverService, DataTransformer dataTransformer) {

        this.materializer = materializer;
        this.entitySource = entitySource;
        this.sampleEntitySource = sampleEntitySource;
        this.realTimeEntitySource = realTimeEntitySource;
        this.schemaService = schemaService;
        this.eventStore = eventStore;
        this.streamService = streamService;
        this.globalConfigurationRepo = globalConfigurationRepo;
        this.instanceConfigurationService = instanceConfigurationService;
        this.graphService = graphService;
        this.conService = conService;
        this.entityRepo = entityRepo;
        this.dataStoreService = dataStoreService;
        this.pipelineTestService = pipelineTestService;
        this.notifyService = notifyService;
        this.watermarkService = watermarkService;
        this.userService = userService;
        this.batchJobService = batchJobService;
        this.publisher = publisher;
        this.pipelineExecutionFactory = pipelineExecutionFactory;
        this.syncDetailRepo = syncDetailRepo;
        this.testResultProcessor = testResultProcessor;
        this.idMappingService = idMappingService;
        this.emailService = emailService;
        this.appConfig = appConfig;
        this.syncDetailMetricService = syncDetailMetricService;
        this.resyncService = resyncService;
        this.subService = subService;
        this.errorNotificationService = errorNotificationService;
        this.factory = dataServiceFactory;
        this.transformer = transformer;
        this.encryptionService = encryptionService;
        this.serviceCredentialService = serviceCredentialService;
        this.nodeAuditService = nodeAuditService;
        this.webhookReceiverService = webhookReceiverService;
        this.dataTransformer = dataTransformer;
    }

    public GraphRunner() {

    }

/*    private SyncContext synContext() {
        var context = ViperContext.fromCurrentContext();
        if (context != null) {
            return context.isSimulationMode() || context.isTestMode() ? SyncContext.LIVETEST : SyncContext.LIVE;
        }
        return null;
    };*/

    private <T> T handleExceptions(Supplier<T> executable, RetryStrategy retryStrategy, Consumer<Throwable> unretriableErrorHandler) {
        try {
            return executable.get();
        } catch (RetriableException retriable) {
            if (retryStrategy.exhausted()) {
                throw new RetryException(retryStrategy, retriable);
            }
            retryStrategy.apply();
            return handleExceptions(executable, retryStrategy.next(), unretriableErrorHandler);
        } catch (Exception e) {
            unretriableErrorHandler.accept(e);
            throw e;
        }
    }

    private void handleErrors(Throwable t) {
        //TODO: Emit events
    }

    private Long getPollingInterval(MappingGraph entityGraph, long pollingInterval) {
        return Optional.ofNullable(entityGraph).map(g -> {
            CoreEntityNodeConfig coreEntityNodeConfig = g.getCoreNode().getTypedConfiguration();
            boolean continuousPipeline = coreEntityNodeConfig.isRealtime() || (entityGraph.getSettings() != null && entityGraph.getSettings().isContinuousPipeline());
            return continuousPipeline ? 1l : pollingInterval;
        }).orElse(pollingInterval);
    }


    public Pair<UniqueKillSwitch, CompletionStage<Done>> start(MappingGraph entityGraph, ViperContext context, SyncStream syncStream, String processorId) {
        long pollingInterval = globalConfigurationRepo.findByKey(GlobalConfiguration.SYNC_INTERVAL_SECONDS).map(g -> {
            int interval = g.cast();
            return interval;
        }).orElse(POLLING_INTERVAL);
        //Custom source to control API limits and other things
        Function<Long, Long> checkinHandler = getCheckinHandler(streamService, syncStream, processorId);
        Supplier<Long> pollingIntervalSupplier = () -> getPollingInterval(entityGraph, pollingInterval);
        ViperContext contextCopy = context.copy();
        EntityStream entityStream = new EntityStream(syncStream.getId(), pollingIntervalSupplier, checkinHandler, contextCopy);
        var src = Source.fromGraph(entityStream);
        Sink<Iterable<SinkSet>, CompletionStage<Done>> finalSink = Sink.foreach(pair -> contextCopy.with(() -> {
            try {
                log.debug("Commit watermarks");
                Iterator<SinkSet> sinksets = pair.iterator();
                Set<String> scheduledSources = getScheduledSources(pair);
                updateScheduledSources(entityGraph, scheduledSources, true);
                if (sinksets.hasNext()) {
                    runSink(sinksets, entitySource);
                    SyncStream updatedSyncStream = streamService.updateLastSuccessfulSync(entityGraph.getId());
                    if (updatedSyncStream != null) {
                        publisher.publishToViperQueue(new Event().setType(EventTypes.SYNC_SUCCESS)
                                .setLoggedTime(new Date())
                                .setDetails(Map.of("targetId", entityGraph.getTargetId(),
                                        "lastSuccessfulSync", updatedSyncStream.getLastSuccessfulSync().toString())));
                    }
                } else {
                    log.warn("No Sinks for this graph");
                }
                boolean isSlowSyncCycle = captureAndAlertSyncDuration(contextCopy, Optional.of(entityGraph));
                Optional.of(entityGraph).ifPresent(eGraph -> {
                    updateSyncProcessingStage(eGraph, contextCopy);
                });
                nodeAuditService.flushAndRemove(contextCopy.getCurrentSyncCycleId());
                // All done reset contextSyncRunId
                context.setContextSyncRunId("");
                return null;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                throw e;
            } finally {
                entityStream.grantToken();
            }

        }));
        GraphContext graphContext = new GraphContext().setGraph(entityGraph);
        return runGraph(entityGraph, contextCopy, syncStream, processorId, src, finalSink, true, entitySource,
                null, null, pipelineExecutionFactory.getPipelineStages(), new TestContext(), null);
    }

    private Set<String> getScheduledSources(Iterable<SinkSet> pair) {
        Set<String> scheduledSources = new HashSet<>();
        pair.forEach(sinkSet -> {
            GraphContext graphContext = sinkSet.getGraphContext();
            if (graphContext.cached(SCHEDULED_SOURCES) != null) {
                scheduledSources.addAll(graphContext.cached(SCHEDULED_SOURCES));
                graphContext.removeFromCache(SCHEDULED_SOURCES);
            }
            return;
        });
        return scheduledSources;
    }

    protected void updateScheduledSources(MappingGraph entityGraph, Set<String> scheduledSources) {
        updateScheduledSources(entityGraph, scheduledSources, false);
    }

    protected void updateScheduledSources(MappingGraph entityGraph, Set<String> scheduledSources, boolean reload) {
        Optional<MappingGraph> reloadedGraph = reload ? graphService.retrieveWithoutLayout(entityGraph.getId())
                : Optional.ofNullable(entityGraph);
        reloadedGraph.ifPresent(graph -> {
            CoreEntityNodeConfig coreNodeConfig = graph.getCoreNode().getTypedConfiguration();
            graph.getSources().forEach(source -> {
                EntitySourceNodeConfig sourceNodeConfig = source.getTypedConfiguration();
                String schedule = sourceNodeConfig.getSchedule();
                if (!StringUtils.isBlank(schedule) && ScheduleUtils.isValidCronExpression(schedule) && scheduledSources.contains(sourceNodeConfig.getEntityDefinition().getId())) {
                    Optional<SyncDetail> watermark = syncDetailRepo.findWatermark(sourceNodeConfig.getEntityDefinition().getId(), coreNodeConfig.getEntityDefinition().getApiName(), SyncDirection.INBOUND);
                    watermark.ifPresent(w -> {
                        Date now = new Date();
                        Date lastSync = new Date(w.getNextSyncAt());
                        if (now.after(lastSync)) {
                            Date nextSyncAt = ScheduleUtils.next(schedule, lastSync);
                            w.setNextSyncAt(Math.max(now.getTime(), nextSyncAt.getTime()));
                            syncDetailRepo.save(w);
                            log.info("Updated Next Sync for source {} from {} to {}. Computed next sync was {}. Using schedule {}", source.getName(), lastSync, new Date(w.getNextSyncAt()), nextSyncAt, schedule);
                        } else {
                            log.info("Next Sync for source {} is in the future, at {}. Not updating", source.getName(), lastSync);
                        }
                    });
                }
            });
        });
    }

    Function<Long, Long> getCheckinHandler(StreamService service, SyncStream syncStream, String processorId) {
        return lastCheckin -> {
            long now = System.currentTimeMillis();
            if (now - lastCheckin >= CHECKIN_INTERVAL) {
                log.info("Checkin in for stream {}", syncStream.getId());
                service.checkin(processorId, syncStream.getId());
                return now;
            }
            return lastCheckin;
        };
    }
    //TODO: switch to PipelineStages, instead of creating the stages through StreamExecutionFactory
    public Pair<UniqueKillSwitch, CompletionStage<Done>> runGraph(MappingGraph entityGraph, ViperContext context,
                                                                  SyncStream syncStream, String processorId, Source<Long, NotUsed> src,
                                                                  Sink<Iterable<SinkSet>, CompletionStage<Done>> finalSink, boolean needsCheckin, DataSource entitySource,
                                                                  Watermark watermark, PipelineTest test, PipelineStages pipelineStages, TestContext testContext, RealtimeSyncContext realtimeContext) {

        int numDestinations = Math.max(1, (int) entityGraph.getSinks().count());
        return src
                .map(d -> context.with(() -> {
                            boolean debugMode = instanceConfigurationService.isDebugModeEnabled();
                            if (!subService.isActiveInstance(context.getInstance().getSyncariId())) {
                                throw new RuntimeException(String.format("Instance with SyncariId %s is not active", context.getInstance().getSyncariId()));
                            }
                            if (!context.isRealTimeMode()) {
                                context.setContextSyncRunId("(" + entityGraph.getName() + ":" + RandomStringUtils.randomAlphanumeric(8) + ")");
                            }
                            context.setDebugMode(debugMode);
                            context.setSyncStartTime(Instant.now().toEpochMilli());
                            log.info("Started sync cycle for Entity Graph {}", entityGraph.getName());
                            var reloadedGraph = graphService.retrieveWithoutLayout(entityGraph.getId()).orElseThrow();
                    if (graphService.isGraphLocked(reloadedGraph)) {
                              log.info("Graph {}({}) is locked. Skipping execution", reloadedGraph.getName(), reloadedGraph.getId());
                              throw new InternalRetriableException("GRAPH_LOCKED",
                                  String.format("Pipeline %s is being published, Will retry in the next cycle",
                                      reloadedGraph.getName()),
                                  "GRAPH_LOCKED");
                            }
                            var reloadedStream = syncStream == null ? null : streamService.getById(syncStream.getId());
                            MappingNode coreNode = reloadedGraph.getCoreNode();
                            EntityDefinition coreEntity = schemaService.getEntity(((CoreEntityNodeConfig) coreNode.getConfiguration()).getEntityDefinition().getId());
                            entityRepo.createCollection(coreEntity);
                            graphService.createIndexes(entityGraph.getId());
                            // Create the table in datastore for this core entity
                            dataStoreService.createEntity(coreEntity);


                            // Cancel Cancel Requested resyncs
                            Optional<ResyncDetail> resyncDetail = resyncService.findProcessingOrCancelRequestedResync(coreEntity.getId());
                            if (resyncDetail.isPresent() && ResyncStatus.CANCEL_REQUESTED.equals(resyncDetail.get().getStatus())) {
                                resyncService.cancel(coreEntity, true);
                            }

                            //cleanup fully disconnected records by deleting id mappings and marking the record as deleted
                            cleanupOrphans(coreEntity, reloadedStream);

                            // Refresh schema for all connector except for tests
                            List<Connector> activeSynapses = conService.getAllActive();

                            var allSources = reloadedGraph.getConnectedSources().collect(Collectors.toList());
                            if (!allSources.isEmpty() && !anyActiveSource(allSources, activeSynapses)) {
                                PipelineException exception = new PipelineException(new RuntimeException("No Active Sources for this Pipeline")).setGraphId(reloadedGraph.getId()).setScope(reloadedGraph.getScope());
                                throw exception;
                            }

                            Map<String, MappingNode> nodeMap = reloadedGraph.getConnectedSources().collect(Collectors.toMap(node -> {
                                EntitySourceNodeConfig sourceNodeConfig = node.getTypedConfiguration();
                                return sourceNodeConfig.getEntityDefinition().getId();
                            }, node -> node));

                            Stream<MappingNode> sources = reloadedGraph.getConnectedSources().filter(node -> reloadedStream == null
                                    || hasActiveResync(coreEntity, node)
                                    || isSchedulable(coreEntity, node, SyncDirection.INBOUND)
                                    || hasPendingJobs(node));

                            List<EntityDefinition> refreshedSources;
                            String syncCycleId = UUID.randomUUID().toString();
                            if (!context.isSimulationMode()) {
                                refreshedSources = refreshActiveSourcesInGraph(reloadedGraph, sources, context.getSyncStartTime(), syncCycleId, coreEntity, activeSynapses);
                                if (syncStream != null && streamService.getById(syncStream.getId()).getStatus() == Status.PAUSING) {
                                    throw new RuntimeException(String.format("Graph %s is being paused, aborting this run of the stream", syncStream.getGraphId()));
                                }
                                autoSyncSchema(coreEntity, context.getSyncStartTime(), syncCycleId, activeSynapses);
                            } else {
                                refreshedSources = sources.map(n ->
                                                schemaService.getEntity(((EntitySourceNodeConfig) n.getConfiguration()).getEntityDefinition().getId()))
                                        .collect(Collectors.toList());
                            }

                            Optional<Long> skewOpt = refreshedSources.stream().map(source -> getClockSkew(source, getConnector(source.getConnectorId(), activeSynapses))).max(Long::compare);
                            long maxClockSkew = skewOpt.isEmpty() ? Watermark.CLOCK_SKEW_TOLERANCE_SECONDS : skewOpt.get();

                            refreshedSources.forEach(source -> {
                                MappingNode node = nodeMap.get(source.getId());
                                setOngoingSyncWatermarks(coreEntity, node, maxClockSkew);
                            });

                            // get data based on date range or id(s)
                            CurrentBatch currentBatch;
                            long syncStartTime = context.getSyncStartTime();
                            if (context.isSimulationMode()) {
                                currentBatch = entitySource.fetchSourceFromTestInput(coreEntity, test);
                            } else if (context.isTestMode() && (!MapUtils.isEmpty(test.getRecordIds()) || !MapUtils.isEmpty(test.getWebhook()))) {
                                // by recordId list or webhook payload
                                currentBatch = handleExceptions(
                                        () -> entitySource.fetchSourceById(new DataSourceRequest()
                                                .setSourceEntities(refreshedSources).setSyncariEntity(coreEntity)
                                                .setSourceParamMap(getParams(reloadedGraph.getConnectedSources()))
                                                .setAdditionalParamMap(getAdditionalParams(reloadedGraph.getConnectedSources()))
                                                .setRecordIds(Optional.ofNullable(test.getRecordIds()).orElse(Map.of()))
                                                .setWebhook(Optional.ofNullable(test.getWebhook()).orElse(Map.of()))
                                                .setGraph(reloadedGraph).setSyncStartTime(syncStartTime).setSyncCycleId(syncCycleId)),
                                        RetryStrategy.defaultStrategy(), this::handleErrors);
                            } else if(realtimeContext != null) {
                                currentBatch = handleExceptions(() ->
                                                entitySource.fetchSource(new DataSourceRequest()
                                                        .setSourceEntities(refreshedSources).setSyncariEntity(coreEntity)
                                                        .setSourceParamMap(getParams(reloadedGraph.getConnectedSources()))
                                                        .setAdditionalParamMap(getAdditionalParams(reloadedGraph.getConnectedSources()))
                                                        .setWatermark(watermark).setGraph(reloadedGraph).setSyncStartTime(syncStartTime)
                                                        .setSyncCycleId(syncCycleId).setRealTimeSourceData(realtimeContext.getRecord())),
                                        RetryStrategy.defaultStrategy(), this::handleErrors);
                                context.setCurrentSyncCycleId(currentBatch.getCurrentBatchId());
                            }else {
                                // by date range
                                currentBatch = handleExceptions(() ->
                                                entitySource.fetchSource(new DataSourceRequest()
                                                        .setSourceEntities(refreshedSources).setSyncariEntity(coreEntity)
                                                        .setSourceParamMap(getParams(reloadedGraph.getConnectedSources()))
                                                        .setAdditionalParamMap(getAdditionalParams(reloadedGraph.getConnectedSources()))
                                                        .setWatermark(watermark).setGraph(reloadedGraph).setSyncStartTime(syncStartTime).setSyncCycleId(syncCycleId)),
                                        RetryStrategy.defaultStrategy(), this::handleErrors);
                                context.setCurrentSyncCycleId(currentBatch.getCurrentBatchId());
                            }

                            // set reloaded graph in context
                            var graphContext = new GraphContext();
                            graphContext.setCurrentBatch(currentBatch).setGraph(reloadedGraph).setTestMode(context.isTestMode())
                                    .setSyncariEntity(coreEntity)
                                    .setSimulationMode(context.isSimulationMode())
                                    .setRealtimeSyncContext(realtimeContext) // TODO: Set Real time context directly in the context
                                    .setTestContext(testContext.setSimulationMode(context.isSimulationMode())).initTempVariableNamespace();
                            Optional<ResyncDetail> resync = resyncService.findProcessingResync(coreEntity.getId());
                            resync.ifPresent(r -> graphContext.setResync(true));
                            // add datastore in graphContext and reuse it in pipeline
                            graphContext.setDatastore(dataStoreService.findActiveDatastore());
                            graphContext.cache(SCHEDULED_SOURCES, refreshedSources.stream().map(s -> s.getId()).collect(Collectors.toSet()));
                            loadSynapseConfigToCache(graphContext, activeSynapses);
                            graphContext.loadSynapseConfigFromCache();
                            loadServiceCredsToCache(graphContext, serviceCredentialService.getCredentials());
                            graphContext.loadServiceCredsFromCache();
                            return graphContext;
                        })
                )
                .viaMat(KillSwitches.single(), Keep.right())
                .map(graphContext -> context.with(() -> pipelineStages.getExecuteEntityPipeline().execute(context, graphContext)))
                .log("execute Entity Pipeline")
                //.map(graphContext -> context.with( () -> lookupExistingIds.execute(context, graphContext)))
                .log("lookupExistingIds")
                .map(graphContext -> context.with(() -> pipelineStages.getExecuteFieldPipeline().execute(context, graphContext)))
                .log("execute field Pipeline")
                //.via(new LogEvent<>(eventService, graphContext -> generateBatchLog(graphContext.getCurrentBatch()),context))
                .mapConcat(graphContext -> context.with(() ->
                        {
                            List<EntityDefinition> sinkEntities = retrieveActiveSinksInGraph(graphContext.getGraph(), graphContext.cache("allActiveSynapses", () -> conService.getAllActive()));
                            List<SinkSet> sinkSets = sinkEntities.stream()
                                    .map(entity -> new SinkSet(sinkEntities.size(), entity, graphContext.copy()))
                                    .collect(Collectors.toList());

                            log.debug("Found {} sinks ", sinkSets.size());
                            if (sinkSets.isEmpty()) {
                                log.debug("No sinks found. Returning a placeholder");
                                return List.of(new SinkSet(1, null, graphContext));
                            }
                            return sinkSets;
                        }
                ))
                .mapAsync(numDestinations, sinkSet ->
                        CompletableFuture.supplyAsync(() -> {
                                    var contextCopy = context.copy();
                                    return contextCopy.with(() -> {
                                        if (sinkSet.getSink() != null) {
                                            pipelineStages.getSaveToSink().execute(sinkSet.getSink(), contextCopy, sinkSet.getGraphContext());
                                        }
                                        return sinkSet;
                                    });
                                }
                                , materializer.executionContext()
                        )
                )
                .log("Generate SinkSets")
                .via(new CollectWhile<>(s -> s.getGraphContext().getCurrentBatch().getCurrentBatchId(), (state -> state.x.getTotalSinks() == state.y.size())))
                .toMat(finalSink, Keep.both())
                .run(materializer);
    }

    protected void loadSynapseConfigToCache(GraphContext graphContext, List<Connector> allActive) {
        graphContext.cache("allActiveSynapses", allActive);
        allActive.stream().forEach(connector -> {
            Map<String, Object> cachedConfigs = graphContext.cachedOrDefault("synapseConfigs", new HashMap<>());
            //TODO: Get rid of this after Token V2 migration
            final Map<String, Object> connectors = connector.transformConnectorData(encryptionService);
            cachedConfigs.put("synapse_" + connector.getName().replaceAll(" ", "_"), connectors);
            //This is for token V2
            cachedConfigs.put("synapse_" + connector.getName(), connectors);
            graphContext.cache("synapseConfigs", cachedConfigs);
        });
    }



    protected void loadServiceCredsToCache(GraphContext graphContext, List<ServiceCredential> creds) {
        creds.stream().forEach(credential -> {
            Map<String, Object> cachedConfigs = graphContext.cachedOrDefault("serviceCredentials", new HashMap<>());
            final Map<String, Object> credentials = credential.transformCredentials();
            cachedConfigs.put("serviceCredentials_" + credential.getName(), credentials);
            graphContext.cache("serviceCredentials", cachedConfigs);
        });
    }

    private Optional<Connector> getConnector(String connectorId, List<Connector> activeConnectors) {
        return activeConnectors.stream().filter(connector -> connector.getId().equals(connectorId)).findFirst();
    }

    private Map<String, Map<String, Object>> getParams(Stream<MappingNode> sources) {
        Map<String, Map<String, Object>> paramMap = new HashMap<>();
        sources.forEach(s -> {
            String id = ((EntitySourceNodeConfig) s.getConfiguration()).getEntityDefinition().getId();
            Map<String, Object> predicate = ((EntitySourceNodeConfig) s.getConfiguration()).getSourceParams();
            paramMap.put(id, predicate);
        });
        return paramMap;
    }
    
    private Map<String, Map<String, Object>> getAdditionalParams(Stream<MappingNode> sources) {
    	Map<String, Map<String, Object>> paramMap = new HashMap<>();
    	sources.forEach(s -> {
    		String id = ((EntitySourceNodeConfig) s.getConfiguration()).getEntityDefinition().getId();
    		Map<String, Object> additionalParams = ((EntitySourceNodeConfig) s.getConfiguration()).getAdditionalParams();
    		paramMap.put(id, additionalParams);
    	});
    	return paramMap;
    }

    protected void cleanupOrphans(EntityDefinition coreEntity, SyncStream stream) {
        if (stream == null) {
            return;
        }
        Timers timer = new Timers(log);
        timer.time("cleanupOrphans", () -> {
            List<IdMapping> orphans;
            int counter = 0;
            while (!(orphans = idMappingService.findOrphans(coreEntity.getApiName(), stream.getLastCleanup())).isEmpty()) {
                final List<String> recordsToDelete = orphans.stream().map(o -> o.getSyncariId()).collect(Collectors.toList());
                entityRepo.markDeleted(recordsToDelete, coreEntity.getApiName());
                counter += orphans.size();
                idMappingService.deleteAll(orphans);
                log.info("Removed {} orphan records so far, with following syncariIds in the current loop {}", counter, recordsToDelete);
            }
            log.debug("Removed {} orphan records", counter);
        });
        timer.logDebug();
        streamService.updateLastCleanup(stream.getId(), Instant.now());
    }


    private boolean hasPendingJobs(MappingNode source) {
        EntitySourceNodeConfig config = source.getTypedConfiguration();
        EntityDefinition extternalEntity = config.getEntityDefinition();
        return !batchJobService.findUnprocessed(extternalEntity.getConnectorId(), extternalEntity.getApiName()).isEmpty();
    }

    protected boolean isSchedulable(EntityDefinition coreEntity, MappingNode source, SyncDirection syncDirection) {
        final EntityDefinition configEntity = (syncDirection == SyncDirection.INBOUND) ?
                ((EntitySourceNodeConfig) source.getTypedConfiguration()).getEntityDefinition() :
                ((EntitySinkNodeConfig) source.getTypedConfiguration()).getEntityDefinition();
        Optional<SyncDetail> watermark = syncDetailRepo.findWatermark(configEntity.getId(), coreEntity.getApiName(), syncDirection);
        return watermark.map(w -> {
            boolean exhaustAllRecords = false;
            if (syncDirection == SyncDirection.INBOUND) {
                EntitySourceNodeConfig sourceNodeConfig = source.getTypedConfiguration();
                exhaustAllRecords = sourceNodeConfig.getExhaustAllRecords() == SchedulingType.PROCESS_ALL
                        && StringUtils.isNotBlank(sourceNodeConfig.getSchedule());
            }
            Date nextSyncAt = new Date(w.getNextSyncAt());
            boolean ready = Instant.now().isAfter(Instant.ofEpochMilli(w.getNextSyncAt()));
            boolean forceSchedule = w.isForceSchedule();

            // if ready and forceSchedule is true then reset forceSchedule flag, this limits forceschedule to one schedulable cycle
            if (ready && forceSchedule) {
                w.setForceSchedule(false);
                syncDetailRepo.save(w);
            }

            if (ready && exhaustAllRecords && !w.isOnGoingSync()) {
                w.setOnGoingSync(true);
                syncDetailRepo.save(w);
            } else if (!exhaustAllRecords && w.isOnGoingSync()) {
                // Reset ongoingsync flag since the config has been updated to process single batch per cycle
                w.setStartTime(0);
                w.setEndTime(0);
                w.setOnGoingSync(false);
                syncDetailRepo.save(w);
            }
            log.info(" Next sync scheduled at {} for entity {}. Ready to process now - {}", nextSyncAt, configEntity.getApiName(), ready);
            // if not ready then schedule only if forceSchedule is false
            return ready || (!forceSchedule && w.isOnGoingSync());
        }).orElse(true);
    }

    private void setOngoingSyncWatermarks(EntityDefinition coreEntity, MappingNode node, long maxClockSkew) {
        EntitySourceNodeConfig sourceNodeConfig = node.getTypedConfiguration();
        EntityDefinition entityDefinition = sourceNodeConfig.getEntityDefinition();
        Optional<SyncDetail> watermark = syncDetailRepo.findWatermark(entityDefinition.getId(), coreEntity.getApiName(), SyncDirection.INBOUND);
        if (watermark.isPresent()) {
            SyncDetail w = watermark.get();
            if (w.isOnGoingSync() && w.getStartTime() == 0 && w.getEndTime() == 0) {
                w.setStartTime(w.getWatermark().getEnd());
                long endTime = Instant.now().minus(maxClockSkew, ChronoUnit.SECONDS).toEpochMilli();
                w.setEndTime(Math.max(endTime, w.getWatermark().getEnd()));
                log.info("Ongoing sync starting with watermark from {} to {}", w.getStartTime(), w.getEndTime());
                syncDetailRepo.save(w);
            }

        }
        ;
    }

    private long getClockSkew(EntityDefinition configEntity, Optional<Connector> connectorMaybe) {
        String connectorId = configEntity.getConnectorId();
        var connectorOpt = !connectorMaybe.isEmpty() ? connectorMaybe : conService.find(connectorId);
        if (connectorOpt.isPresent()) {
            Connector connector = connectorOpt.get();
            DataService dataService = factory.getDataService(connector.getMetadata());
            return dataService.clockSkewTolerance(transformer.toConnectorInfo(connector));
        } else {
            return 0;
        }
    }

    protected boolean hasActiveResync(EntityDefinition syncariEntity, MappingNode sourceNode) {
        final EntityDefinition configEntity = ((EntitySourceNodeConfig) sourceNode.getTypedConfiguration()).getEntityDefinition();
        Optional<ResyncDetail> resync = resyncService.findActiveResync(syncariEntity.getId());
        return resync.map(r -> r.isSourceInProgress(configEntity.getId())).orElse(false);
    }

    protected boolean anyActiveSource(List<MappingNode> sources, List<Connector> activeConnectors) {
        var connectorsMap = activeConnectors.stream().collect(Collectors.toMap(Connector::getId, Function.identity()));
        return sources.stream().map(n -> schemaService.getEntity(((EntitySourceNodeConfig) n.getConfiguration()).getEntityDefinition().getId()))
                .filter(entity -> conService.getSyncariConnector().getId().equals(entity.getConnectorId()) || connectorsMap.containsKey(entity.getConnectorId())).findFirst().isPresent();
    }

    protected List<EntityDefinition> refreshActiveSourcesInGraph(MappingGraph graph, Stream<MappingNode> schedulableSources,long syncStartTime, String syncCycleId, EntityDefinition syncariEntity, List<Connector> activeConnectors){
        var connectorsMap = activeConnectors.stream().collect(Collectors.toMap(Connector::getId, Function.identity()));
        var syncariConnector = conService.getSyncariConnector();
        String syncariConnectorId = syncariConnector.getId();
        connectorsMap.put(syncariConnectorId, syncariConnector);
        return schedulableSources.map(n ->
                        schemaService.getEntity(((EntitySourceNodeConfig) n.getConfiguration()).getEntityDefinition().getId()))
                .filter(entity -> connectorsMap.containsKey(entity.getConnectorId()))
                .map(s -> {
                    if (s.getConnectorId().equalsIgnoreCase(syncariConnectorId)) {
                        Optional<EntityDefinition> syncariEntitityDef = schemaService.getSyncariEntityByName(s.getApiName());
                        if (syncariEntitityDef.isPresent()) {
                            return syncariEntitityDef.get();
                        }
                    }
                    com.syncari.utils.Timer timer = new Timer("GraphRunner::refreshActiveSourcesInGraph");
                    String lockOwnerId = graph.getId() + "_" + s.getId();

                    List<EntityDefinition> refreshedEntities = withPipelineException(() -> schemaService.refreshSynapseSchema(s.getConnectorId(), s, lockOwnerId), graph, s, true);
                    EntityDefinition result = refreshedEntities.stream().filter(e -> e.getApiName().equals(s.getApiName())).findFirst().orElse(s);
                    var con = connectorsMap.get(s.getConnectorId());
                    EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(con.getId(), con.getName(), s.getApiName(), Instant.ofEpochMilli(0), (float) timer.getTimeTakenUntilNow(),
                            0, 0, 0);

                    long totalDurationtillNow = Instant.now().toEpochMilli() - syncStartTime;
                    syncDetailMetricService.findOrCreateSourceRefresh(syncariEntity.getDisplayName(), syncariEntity.getId(), syncariEntity.getApiName(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.REFRESH_SOURCE_SCHEMA_STAGE,
                            false, false, syncCycleId, (float) totalDurationtillNow, 0);
                    timer.close();
                    return result;
                }).collect(Collectors.toList());

    }

    protected List<EntityDefinition> retrieveActiveSinksInGraph(MappingGraph graph, List<Connector> allActiveConnectors) {
        MappingNode coreNode = graph.getCoreNode();
        EntityDefinition coreEntity = schemaService.getEntity(((CoreEntityNodeConfig) coreNode.getConfiguration()).getEntityDefinition().getId());
        List<MappingNode> sinks = graph.getConnectedSinks()
                .filter(node -> isSchedulable(coreEntity, node, SyncDirection.OUTBOUND)).collect(Collectors.toList());
        var activeConnectors = allActiveConnectors.stream().map(Connector::getId).collect(Collectors.toList());
        return sinks.stream()
                .map(n -> ((EntitySinkNodeConfig) n.getConfiguration()).getEntityDefinition())
                .filter(entity -> activeConnectors.contains(entity.getConnectorId()))
                .filter(entity -> com.syncari.core.model.util.Status.ACTIVE.equals(entity.getStatus()))
                .collect(Collectors.toList());
    }


    public CompletableFuture<WebhookActionResponse> syncPipeline(String requestId, MappingGraph graph, Connector connector, String body, Map<String, Object> headers) throws JsonProcessingException {

        // create a viper context
        ViperContext context = new ViperContext(SyncariContext.getOrganziation(), SyncariContext.getInstance(), userService.getSystemUser());

        context.setApplicationContext(applicationContext);
        context.setUpdateWatermark(false);
        context.setRealTimeMode(true);
        context.setContextSyncRunId(String.format("(%s:%s)", requestId, graph.getName()));

        CompletableFuture<WebhookActionResponse> actionResponse = new CompletableFuture<>();
        // run the graph
        ViperContext contextCopy = context.copy();

        Sink<Iterable<SinkSet>, CompletionStage<Done>> finalSink = Sink.foreach(pair -> contextCopy.with(() -> {
            try {
                context.setContextSyncRunId("");
                return null;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                throw e;
            }
        }));
        var entities = schemaService.getEntities(connector.getId());
        List<String> activeEntities = entities.stream().filter(e -> e.isActive()).map(e -> e.getApiName()).collect(Collectors.toList());
        List<EntityData> entityData = webhookReceiverService.getRecords(new WebhookRequest().setBody(body).setConfig(dataTransformer.toConnectorInfo(connector)).setActiveEntities(activeEntities));
        RealtimeSyncContext realtimeSyncContext = new RealtimeSyncContext().setSyncResponse(actionResponse).setRecord(entityData.get(0));
        Pair<UniqueKillSwitch, CompletionStage<Done>> result = runGraph(graph, contextCopy, null, null,
                Source.single(1L), finalSink, false, realTimeEntitySource,
                null, null, pipelineExecutionFactory.getPipelineStages(),
                new TestContext(), realtimeSyncContext);
        var syncResponse = result.second().toCompletableFuture().thenApply(f -> {
            MultiValueMap<String, String> responseHeader = new LinkedMultiValueMap<>();
            responseHeader.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            return new WebhookActionResponse()
                    .setPayload(webhookReceiverService.getWebhookResponse(connector, body, headers)).setHeaders(responseHeader).setStatusCode(HttpStatus.OK);
        }).exceptionally(error -> {
            MultiValueMap<String, String> responseHeader = new LinkedMultiValueMap<>();
            try {
                ObjectMapper mapper = new ObjectMapper();
                responseHeader.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                return new WebhookActionResponse()
                        .setPayload(mapper.writeValueAsString(Map.of("status", "error", "errorMessage", error.getCause() != null ?
                                error.getCause().getMessage() : error.getMessage())))
                        .setHeaders(responseHeader).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            } catch (JsonProcessingException e) {
                responseHeader.set("Content-Type", MediaType.TEXT_PLAIN_VALUE);
                return new WebhookActionResponse()
                        .setPayload(e.getMessage()).setHeaders(responseHeader)
                        .setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });
        return CompletableFuture.anyOf(actionResponse, syncResponse).thenApply(o -> (WebhookActionResponse) o);
    }

    public void test(String pipelineTestId, String processorId) {
        Optional<PipelineTest> pipelineTest = pipelineTestService.getTestForProcessing(pipelineTestId);
        if (pipelineTest.isPresent()) {
            PipelineTest pipeline = pipelineTest.get();
            Optional<MappingGraph> graph = graphService.retrieveWithoutLayout(pipeline.getGraphId());
            if (graph.isPresent()) {
                if (pipeline.isPauseSync()) {
                    // Stop the original stream before starting the test
                    streamService.pauseStream(graph.get().getParentId());
                }
                log.debug("Starting pipeline test for {}", graph.get().getId());
                User user = userService.getUserById(pipeline.getUserId());
                ViperContext context = new ViperContext(SyncariContext.getOrganziation(), SyncariContext.getInstance(), user);
                context.setApplicationContext(applicationContext);
                context.setUpdateWatermark(false);
                context.setTestMode(true);

                // data by date range
                Watermark watermark = null;
                Map<String, List<String>> recordIds = null;
                Map<String, PipelineTestWebhook> webhook = null;
                if ((pipeline.getRecordIds() == null || pipeline.getRecordIds().isEmpty()) && MapUtils.isEmpty(pipeline.getWebhook())) {
                    watermark = new Watermark(pipeline.getStartTime().toEpochMilli(), pipeline.getEndTime().toEpochMilli(), false, 0);
                    watermark.setLimit(pipeline.getLimit());
                } else {
                    // data by ID
                    recordIds = pipeline.getRecordIds();
                    //by webhook payload
                    webhook = pipeline.getWebhook();
                }

                GraphContext graphContext = new GraphContext().setGraph(graph.get());
                // run the graph
                ViperContext contextCopy = context.copy();
                Pair<UniqueKillSwitch, CompletionStage<Done>> result = runGraph(graph.get(), contextCopy, null, null,
                        Source.single(1L), getTestSinks(contextCopy, sampleEntitySource), false, sampleEntitySource,
                        watermark, pipeline, pipelineExecutionFactory.getPipelineStages(),
                        new TestContext().setPipelineTestId(pipelineTestId), null);

                String testType = (recordIds != null && !recordIds.isEmpty()) ? "(by ID)" : "(by date range)";
                if(MapUtils.isNotEmpty(webhook)) {
                  testType = "(by Webhook Payload)";
                }
                log.info("Started pipeline test {} for {}", testType, graph.get().getId());
                String testType1 = testType;
                result.second().whenComplete((done, exception) -> {
                    contextCopy.with(() -> {
                        log.info("Notifying test result {} for {} with graphContext {}", testType1, graph.get().getId(), graphContext.getTestContext().toString());
                        pipelineTestService.finishTestRun(pipeline, graph.get(), exception);
                    });
                });
                log.info(graphContext.getTestContext().toString());
            } else {
                String subject = "Test for %s Failed";
                String body = "The pipeline for %s was not found.";
                notifyService.send(new Notification(subject, body, NotificationType.ERROR, pipeline.getUserId()));
                errorNotificationService.sendErrorNotification(ErrorCategory.PIPELINE, ErrorPriority.P1, pipeline.getGraphId(), subject, body);
            }
        } else {
            Optional<PipelineTest> pipeline = pipelineTestService.getTestById(pipelineTestId);
            if (!pipeline.isPresent()) {
                log.error("Test Pipeline with id " + pipelineTestId + " not found");
            } else {
                log.warn("Test pipeline with id " + pipelineTestId + " is probably being processed by another test processor instance.");
            }
        }
    }

    private Sink<Iterable<SinkSet>, CompletionStage<Done>> getTestSinks(ViperContext context, DataSource entitySource) {
        return Sink.foreach(pair -> context.with(() -> {
            log.info("Finalizing test pipeline");
            Iterator<SinkSet> sinksets = pair.iterator();
            if (sinksets.hasNext()) {
                runSink(sinksets, entitySource, true);
            } else {
                log.warn("No Sinks for this graph");
            }
            return null;
        }));
    }

    private void autoSyncSchema(EntityDefinition coreEntity, long syncStartTime, String syncCycleId, List<Connector> activeConnectors) {
        List<ConnectorSchemaSetting> settings = conService.getSettingBySyncariEntity(coreEntity);
        if (CollectionUtils.isNotEmpty(settings)) {
            com.syncari.utils.Timer timer = new Timer("GraphRunner::autoSyncSchema");
            settings.forEach(setting -> {
                schemaService.autoSyncSchemaFor(setting);
            });
            Optional<Connector> con = conService.find(coreEntity.getConnectorId());
            con.ifPresent(c -> {
                EntitySyncStatusMetric syncStatusMetric = new EntitySyncStatusMetric(c.getId(), c.getName(), coreEntity.getApiName(), Instant.ofEpochMilli(0), (float) timer.getTimeTakenUntilNow(),
                        0, 0, 0);
                long totalDurationtillNow = Instant.now().toEpochMilli() - syncStartTime;
                syncDetailMetricService.findOrCreateAutoSync(coreEntity.getDisplayName(), coreEntity.getId(), coreEntity.getApiName(), syncStatusMetric, EntitySynchStatusMetricSummary.Stage.AUTO_SYNC_STAGE,
                        false, false, syncCycleId, (float) totalDurationtillNow, 0);
            });
        }
    }

    private void runSink(Iterator<SinkSet> sinksets, DataSource entitySource) {
        runSink(sinksets, entitySource, false);
    }

    private void runSink(Iterator<SinkSet> sinksets, DataSource entitySource, boolean captureTestResults) {
        //graphContext is shared so we need to grab just one of the sinksets
        GraphContext graphContext = sinksets.next().getGraphContext();
        CurrentBatch currentBatch = graphContext.getCurrentBatch();
        if (captureTestResults) {
            testResultProcessor.populateEntityGraphLiveTestResult(graphContext, graphContext.getGraph(), currentBatch);
        }
        log.debug(String.format("Inserting syncLog for %s with size %s", currentBatch.getCurrentBatchId(),
                currentBatch.getSyncLogs().size()));
        eventStore.insertSyncLogs(currentBatch.getSyncLogs());
        List<PipelineStats> stats = graphContext.getAllStats();
        log.debug(String.format("Inserting Stats for %s with size %s", currentBatch.getCurrentBatchId(),
                stats.size()));
        entitySource.closeSource(graphContext);
        graphContext.clear();
    }

    protected boolean captureAndAlertSyncDuration(ViperContext context, Optional<MappingGraph> graph) {
        long syncCycleDuration = (Instant.now().toEpochMilli() - context.getSyncStartTime()) / 1000;
        log.info("The sync cycle with contextSyncRunId {} took {} seconds", context.getContextSyncRunId(), syncCycleDuration);
        if (syncCycleDuration >= SYNC_CYCLE_ALERT_THRESHOLD) {
            graph.ifPresent(grp -> {
                String pipelineInfo = String.format("graph %s in instance %s(%s) org %s with contextSyncRunId %s ", grp.getName(),
                        SyncariContext.getSyncariId(), SyncariContext.getInstance().getName(), SyncariContext.getOrganziation().getName(),
                        context.getContextSyncRunId());
                String slowPipelineBody = String.format("Pipeline for %s took %d seconds to complete a sync cycle.", pipelineInfo,
                        syncCycleDuration);
                log.warn(slowPipelineBody);
            });
            return true;
        }
        return false;
    }

    private void updateSyncProcessingStage(MappingGraph entityGraph, ViperContext context) {
        var coreNode = entityGraph.getCoreNode();
        var syncariEntityDefinition = schemaService.getEntity(coreNode.getConfiguration().getConfigMap().get("entityDefinition").toString());
        String syncarEntityId = syncariEntityDefinition.getId();
        String syncCycleId = context.getCurrentSyncCycleId();
        Optional<SyncDetailMetric> syncDetailMetricOptional = syncDetailMetricService.findLatestSyncDetailMetric(syncarEntityId, syncCycleId);
        syncDetailMetricOptional.ifPresent(syncDetailMetric -> {
            long totalDurationtillNow = Instant.now().toEpochMilli() - context.getSyncStartTime();
            syncDetailMetricService.updateSyncDetailMetric(syncarEntityId, null, EntitySynchStatusMetricSummary.Stage.FINISHED_PIPELINE_EXECUTION, syncDetailMetric.getSyncCycleId(), (float) totalDurationtillNow);
        });
    }

}

@Getter
@AllArgsConstructor
class SinkSet {
    private int totalSinks;
    private EntityDefinition sink;
    private GraphContext graphContext;
}
