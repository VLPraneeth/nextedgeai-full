package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.Constants;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.util.SyncDetailMetric;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.repositories.customer.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

@DirtiesContext
public class SyncStatusServiceTest extends AbstractSyncariTest {

    @Autowired
    MappingGraphService graphService;

    @Autowired
    StreamService streamService;

    @Autowired
    SyncStatusService syncStatusService;

    @Autowired
    ResyncService resyncService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    EndSystemConfig config;

    @Autowired
    StreamRepo streamRepo;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    SyncDetailRepo syncDetailRepo;

    @Autowired
    PipelineTestRepo pipelineTestRepo;

    @Autowired
    SyncDetailMetricService syncDetailMetricService;

    @Autowired
    private ObjectMapper mapper;

    @Before
    public void setUp(){
        super.setUp();
    }

    @After
    public void tearDown(){
        super.tearDown();
        resetRepos(streamRepo, entityProxyRepo, attributeProxyRepo, syncDetailRepo, pipelineTestRepo);
    }

    @Test
    public void getAllPipelineStreamStatus(){
        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();

        EntityDefinition syncariEntity = schemaService.getAllEntities(syncariConnector.getId()).get(0);
        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        List<StreamInfo> allPipelineStreamStatus = syncStatusService.getAllPipelineStreamStatus();
        assertFalse(allPipelineStreamStatus.isEmpty());
        StreamInfo pipelineStatus = allPipelineStreamStatus.get(0);
        assertEquals(StreamInfo.Status.QUEUED, pipelineStatus.getStatus());
        assertEquals(syncStream.lagInMillis()/1000, pipelineStatus.getLagTimeInSeconds());
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        assertEquals(syncStream.getLastSuccessfulSync(), pipelineStatus.getLastSyncTime());
        assertEquals(syncStream.getDetails(), pipelineStatus.getErrorDetails());
        // for all pipeline status individual entity status summaries are not pulled
        assertNull(pipelineStatus.getSummary());
    }

    @Test
    public void getAllPipelineStreamStatus_DraftOnly(){
        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();

        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        List<StreamInfo> allPipelineStreamStatus = syncStatusService.getAllPipelineStreamStatus();
        assertFalse(allPipelineStreamStatus.isEmpty());
        StreamInfo pipelineStatus = allPipelineStreamStatus.stream()
                .filter(info -> info.getSyncariEntityId().equals(syncariEntity.getId()))
                .findFirst().get();
        assertEquals(StreamInfo.Status.UNPUBLISHED, pipelineStatus.getStatus());
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        // for all pipeline status individual entity status summaries are not pulled
        assertNull(pipelineStatus.getSummary());
    }

    @Test
    public void getAllPipelineStreamStatus_DraftWithRunningTestPipeline(){
        Connector sfdcConnector = createConnector();

        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();

        // create a test pipeline
        PipelineTest test = new PipelineTest().setGraphId(graph.getId()).setStartTime(Instant.EPOCH).setEndTime(Instant.now())
                .setLimit(1).setUserId(SyncariContext.getUser().getId())
                .setRecordIds(null)
                .setOriginalStreamStatus(null);
        test.setStatus(Status.NEW);
        test = pipelineTestRepo.save(test);

        List<StreamInfo> allPipelineStreamStatus = syncStatusService.getAllPipelineStreamStatus();
        assertFalse(allPipelineStreamStatus.isEmpty());
        StreamInfo pipelineStatus = allPipelineStreamStatus.stream()
                .filter(info -> info.getSyncariEntityId().equals(syncariEntity.getId()))
                .findFirst().get();
        assertEquals(StreamInfo.Status.TEST, pipelineStatus.getStatus());
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        // for all pipeline status individual entity status summaries are not pulled
        assertNull(pipelineStatus.getSummary());
    }

    @Test
    public void getEntityPipelineStreamStatus() throws InterruptedException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();

        EntityDefinition sfdcEntity = schemaService.getEntityByName(sfdcConnector.getId(), "account").get();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        StreamInfo pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.QUEUED, pipelineStatus.getStatus());
        assertEquals(0, pipelineStatus.getLagTimeInSeconds());
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        assertEquals(syncStream.getLastSuccessfulSync(), pipelineStatus.getLastSyncTime());
        assertEquals(syncStream.getDetails(), pipelineStatus.getErrorDetails());
        // for all pipeline status individual entity status summaries are not pulled
        assertEquals(1, pipelineStatus.getSummary().getSources().size());
        assertEquals(1, pipelineStatus.getSummary().getSinks().size());
        var source = pipelineStatus.getSummary().getSources().get(0);

        assertEquals(sfdcEntity.getDisplayName(), source.getEntityName());
        assertEquals(sfdcEntity.getId(), source.getEntityId());
        assertEquals("testSynapse", source.getConnectorName());
        assertEquals(Constants.TEST_SYNAPSE, source.getConnectorType());

        var sink = pipelineStatus.getSummary().getSinks().get(0);
        assertEquals(sfdcEntity.getDisplayName(), sink.getEntityName());
        assertEquals(sfdcEntity.getId(), sink.getEntityId());
        assertEquals("testSynapse", sink.getConnectorName());
        assertEquals(Constants.TEST_SYNAPSE, sink.getConnectorType());

        // Change stream status to PAUSED
        syncStream = streamRepo.save(syncStream.setStatus(SyncStream.Status.PAUSED));
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.PAUSED, pipelineStatus.getStatus());

        // change stream status to running and add lastSyncTime
        Instant now = Instant.now();
        syncStream.setStatus(SyncStream.Status.RUNNING);
        syncStream.setLastSuccessfulSync(now);
        syncStream = streamRepo.save(syncStream);
        Thread.sleep(1000);
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.RUNNING, pipelineStatus.getStatus());
        assertEquals(now.getEpochSecond(), pipelineStatus.getLastSyncTime().getEpochSecond());
        assertTrue(pipelineStatus.getLagTimeInSeconds() >= 1);

        ResyncDetail resync = resyncService.createResyncRequest(syncariEntity.getId(), 
            List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(ResyncStatus.NEW, resync.getStatus());
        assertEquals(StreamInfo.Status.RESYNCING, pipelineStatus.getStatus());

        resyncService.processNewResync(syncariEntity.getId());
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.RESYNCING, pipelineStatus.getStatus());

        resyncService.cancelInProgress(syncariEntity);
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.RUNNING, pipelineStatus.getStatus());

        // change watermarks and validate if its reflected in EntityStatus of source and sink
        var sourceWm = syncDetailRepo.findWatermark(sfdcEntity.getId(), syncariEntity.getApiName(), SyncDirection.INBOUND).get();
        sourceWm.getWatermark().setStart(now.toEpochMilli()).setEnd(now.toEpochMilli()).setInitial(true);
        sourceWm = syncDetailRepo.save(sourceWm);

        var sinkWm = syncDetailRepo.save(new SyncDetail(sfdcEntity.getId(), syncariEntity.getApiName(),
                new Watermark(now.toEpochMilli(), now.toEpochMilli(), true, 0).setDirection(SyncDirection.OUTBOUND)));
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        source = pipelineStatus.getSummary().getSources().get(0);
        assertEquals(sfdcEntity.getDisplayName(), source.getEntityName());
        assertEquals(sfdcEntity.getId(), source.getEntityId());
        assertEquals("testSynapse", source.getConnectorName());
        assertEquals(Constants.TEST_SYNAPSE, source.getConnectorType());
        assertNull(source.getProcessedUpTo());
        assertTrue(source.isHistoricalSync());

        sink = pipelineStatus.getSummary().getSinks().get(0);
        assertEquals(sfdcEntity.getDisplayName(), sink.getEntityName());
        assertEquals(sfdcEntity.getId(), sink.getEntityId());
        assertEquals("testSynapse", sink.getConnectorName());
        assertEquals(Constants.TEST_SYNAPSE, sink.getConnectorType());
        assertNull(sink.getProcessedUpTo());
        assertTrue(sink.isHistoricalSync());

        // validate stream error state
        syncStream.setStatus(SyncStream.Status.ERROR);
        syncStream.setDetails("Pipeline Error");
        syncStream = streamRepo.save(syncStream);
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.ERROR, pipelineStatus.getStatus());
        assertEquals("Pipeline Error", pipelineStatus.getErrorDetails());

        // validate LAGGING status
        syncStream.setStatus(SyncStream.Status.RUNNING);
        syncStream.setLastSuccessfulSync(now.minusSeconds(61*60)); // set last successful sync before 1 hr
        syncStream = streamRepo.save(syncStream);
        Thread.sleep(1000);
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.RUNNING, pipelineStatus.getStatus());
        assertEquals(syncStream.getLastSuccessfulSync().getEpochSecond(), pipelineStatus.getLastSyncTime().getEpochSecond());
        assertTrue(pipelineStatus.getLagTimeInSeconds() >= 3600);

        // validate RUNNING status in absence of lastSuccessfulSync (before completion of very first sync cycle)
        syncStream.setStatus(SyncStream.Status.RUNNING);
        syncStream.setLastSuccessfulSync(null); // set last successful sync as null (no sync cycle has run yet)
        syncStream = streamRepo.save(syncStream);
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertNull(syncStream.getLastSuccessfulSync());
        assertNull(pipelineStatus.getLastSyncTime());
        assertEquals(StreamInfo.Status.RUNNING, pipelineStatus.getStatus());
    }

    @Test
    public void getEntityPipelineSyncMetricTestWithNoStages() throws JsonProcessingException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        SyncMetric metric = syncStatusService.getEntityPipelineSyncMetric(syncariEntity.getId());
        assertEquals(syncariEntity.getId(), metric.getSyncariEntityId());
        List<Stage> stages = metric.getAllStages();
        assertNull(stages);
        System.out.println(mapper.writeValueAsString(metric));
    }

    @Test
    public void getEntityPipelineSyncMetricTestWithEmtyLastSyncFlag() throws JsonProcessingException, InterruptedException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        String entityName = syncariEntity.getApiName();
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId", 20f, 10);

        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId2", 20f, 10);


        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed(syncariEntity.getId());
        assertTrue(metric.isPresent());


        StreamInfo pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.QUEUED, pipelineStatus.getStatus());
        assertEquals(0, pipelineStatus.getLagTimeInSeconds());
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        assertEquals(syncStream.getLastSuccessfulSync(), pipelineStatus.getLastSyncTime());
        assertEquals(syncStream.getDetails(), pipelineStatus.getErrorDetails());

        Instant now = Instant.now();
        syncStream.setStatus(SyncStream.Status.RUNNING);
        syncStream.setLastSuccessfulSync(now);
        syncStream = streamRepo.save(syncStream);
        Thread.sleep(1000);
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.RUNNING, pipelineStatus.getStatus());
        assertEquals(now.getEpochSecond(), pipelineStatus.getLastSyncTime().getEpochSecond());
        assertTrue(pipelineStatus.getLagTimeInSeconds() >= 1);

        SyncMetric metricFetched = syncStatusService.getEntityPipelineSyncMetric(syncariEntity.getId());
        assertEquals(syncariEntity.getId(), metricFetched.getSyncariEntityId());
        List<Stage> stages = metricFetched.getAllStages();
        assertNotNull(stages);
        assertEquals(1,stages.size());
        assertNotNull(mapper.writeValueAsString(metricFetched));
        assertTrue(metricFetched.getLastSyncTime().compareTo(metricFetched.getLastProcessed()) > 0);
        //System.out.println(mapper.writeValueAsString(metricFetched));
    }

    @Test
    public void getEntityPipelineSyncMetricTestWithEmtyLastSyncAndNoMetric() throws JsonProcessingException, InterruptedException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        String entityName = syncariEntity.getApiName();

        StreamInfo pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.QUEUED, pipelineStatus.getStatus());
        assertEquals(0, pipelineStatus.getLagTimeInSeconds());
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        assertEquals(syncStream.getLastSuccessfulSync(), pipelineStatus.getLastSyncTime());
        assertEquals(syncStream.getDetails(), pipelineStatus.getErrorDetails());

        Instant now = Instant.now();
        syncStream.setStatus(SyncStream.Status.RUNNING);
        syncStream.setLastSuccessfulSync(now);
        syncStream = streamRepo.save(syncStream);
        Thread.sleep(1000);
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.RUNNING, pipelineStatus.getStatus());
        assertEquals(now.getEpochSecond(), pipelineStatus.getLastSyncTime().getEpochSecond());
        assertTrue(pipelineStatus.getLagTimeInSeconds() >= 1);

        SyncMetric metricFetched = syncStatusService.getEntityPipelineSyncMetric(syncariEntity.getId());
        assertEquals(syncariEntity.getId(), metricFetched.getSyncariEntityId());
        List<Stage> stages = metricFetched.getAllStages();
        assertNull(stages);
        assertTrue(metricFetched.isEmptyLastSync());
        assertEquals(syncariEntity.getDisplayName(), metricFetched.getEntityName());
        assertEquals(syncariEntity.getApiName(), metricFetched.getApiName());
    }

    @Test
    public void getEntityPipelineSyncMetricTestWithOnlySourceStage() throws JsonProcessingException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        String entityName = syncariEntity.getApiName();
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId", 20f, 10);

        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId2", 20f, 10);


        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed(syncariEntity.getId());
        assertTrue(metric.isPresent());

        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId2", 20f, 10);

        Optional<SyncDetailMetric> metric1 = syncDetailMetricService.findLatestSyncDetailMetric(syncariEntity.getId(),"testSourceStageSyncCycleId2");
        assertTrue(metric1.isPresent());


        SyncMetric metricFetched = syncStatusService.getEntityPipelineSyncMetric(syncariEntity.getId());
        assertEquals(syncariEntity.getId(), metricFetched.getSyncariEntityId());
        List<Stage> stages = metricFetched.getAllStages();
        assertNotNull(stages);
        assertEquals(1,stages.size());
        assertNotNull(mapper.writeValueAsString(metricFetched));
    }

    @Test
    public void getEntityPipelineSyncMetricTestWithOnlySourceStageTwoEntityData() throws JsonProcessingException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        EntityDefinition syncariContactEntity = schemaService.getSyncariEntityByName("contact").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        MappingGraph graph1 = graphService.retrieveEntityGraph(syncariContactEntity.getId()).get();
        var approvedGraph1 = graphService.approveDraft(graph1);
        SyncStream syncStream1 = streamService.findStream(approvedGraph1.getId()).get();

        String entityName = syncariEntity.getApiName();
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId", 20f, 10);

        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId2", 20f, 10);

        EntitySyncStatusMetric  syncStatusMetricContact = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),syncariContactEntity.getApiName(), Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(syncariContactEntity.getDisplayName(),syncariContactEntity.getId(),syncariContactEntity.getApiName(),syncStatusMetricContact,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageContactSyncCycleId", 20f, 10);

        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric(syncariEntity.getId());
        assertTrue(metric.isPresent());
        assertEquals("testSourceStageSyncCycleId2",metric.get().getSyncCycleId());

        Optional<SyncDetailMetric> metricContact = syncDetailMetricService.findLatestSyncDetailMetric(syncariContactEntity.getId());
        assertTrue(metricContact.isPresent());
        assertEquals("testSourceStageContactSyncCycleId",metricContact.get().getSyncCycleId());


        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId2", 20f, 10);

        Optional<SyncDetailMetric> metric1 = syncDetailMetricService.findLatestSyncDetailMetric(syncariEntity.getId(),"testSourceStageSyncCycleId2");
        assertTrue(metric1.isPresent());
        assertEquals("testSourceStageSyncCycleId2",metric1.get().getSyncCycleId());
        assertNotEquals(metric.get().getUpdatedAt(),metric1.get().getUpdatedAt());


        SyncMetric metricFetched = syncStatusService.getEntityPipelineSyncMetric(syncariEntity.getId());
        assertEquals(syncariEntity.getId(), metricFetched.getSyncariEntityId());
        List<Stage> stages = metricFetched.getAllStages();
        assertNotNull(stages);
        assertEquals(1,stages.size());
        assertNotNull(mapper.writeValueAsString(metricFetched));
        //System.out.println(mapper.writeValueAsString(metricFetched));
    }

    @Test
    public void getEntityPipelineSyncMetricTestWithSourceAndEpStage() throws JsonProcessingException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        String entityName = syncariEntity.getApiName();
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testEntityPipelineStageSyncCycleId", 20f, 10);

        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,10,
                10, 0);
        syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric1, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_ENTITY_PIPELINE,"testEntityPipelineStageSyncCycleId", 20f);

        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric(syncariEntity.getId(),"testEntityPipelineStageSyncCycleId");
        assertTrue(metric.isPresent());

        SyncMetric metricFetched = syncStatusService.getEntityPipelineSyncMetric(syncariEntity.getId());
        assertEquals(syncariEntity.getId(), metricFetched.getSyncariEntityId());
        List<Stage> stages = metricFetched.getAllStages();
        assertNotNull(stages);
        assertEquals(2,stages.size());
        assertNotNull(mapper.writeValueAsString(metricFetched));
    }

    @Test
    public void getEntityPipelineSyncMetricTestWithSourceEpAndFpStage() throws JsonProcessingException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        String entityName = syncariEntity.getApiName();
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testFieldPipelineStageSyncCycleId", 20f, 10);

        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,10,
                10, 0);
        syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric1, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_ENTITY_PIPELINE,"testFieldPipelineStageSyncCycleId", 20f);


        EntitySyncStatusMetric  syncStatusMetric2 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric2, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_FIELD_PIPELINE,"testFieldPipelineStageSyncCycleId", 20f);

        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric(syncariEntity.getId(),"testFieldPipelineStageSyncCycleId");
        assertTrue(metric.isPresent());

        SyncMetric metricFetched = syncStatusService.getEntityPipelineSyncMetric(syncariEntity.getId());
        assertEquals(syncariEntity.getId(), metricFetched.getSyncariEntityId());
        List<Stage> stages = metricFetched.getAllStages();
        assertNotNull(stages);
        assertEquals(3,stages.size());
        assertNotNull(mapper.writeValueAsString(metricFetched));
    }

    @Test
    public void getEntityPipelineSyncMetricTestWith4Stages() throws JsonProcessingException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        String entityName = syncariEntity.getApiName();
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,
                10, 2,8);
        Optional<SyncDetailMetric> metricsaved = syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSaveToSinkStageSyncCycleId", 20f, 10);

        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,10,
                10, 0);
        metricsaved = syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric1, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_ENTITY_PIPELINE,"testSaveToSinkStageSyncCycleId", 20f);


        EntitySyncStatusMetric  syncStatusMetric2 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        metricsaved = syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric2, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_FIELD_PIPELINE,"testSaveToSinkStageSyncCycleId", 20f);

        EntitySyncStatusMetric  syncStatusMetric3 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        metricsaved = syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric3, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_ENTITY_PIPELINE,"testSaveToSinkStageSyncCycleId", 20f);

        EntitySyncStatusMetric  syncStatusMetric4 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        metricsaved = syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric3, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_FIELD_PIPELINE,"testSaveToSinkStageSyncCycleId", 20f);

        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric(syncariEntity.getId(),"testSaveToSinkStageSyncCycleId");
        assertTrue(metric.isPresent());
        assertEquals(metric.get().getSyncCycleId(), "testSaveToSinkStageSyncCycleId");

        SyncMetric metricFetched = syncStatusService.getEntityPipelineSyncMetric(syncariEntity.getId());
        assertEquals(syncariEntity.getId(), metricFetched.getSyncariEntityId());
        List<Stage> stages = metricFetched.getAllStages();
        assertNotNull(stages);
        assertEquals(5,stages.size());
        assertNotNull(stages.get(4).getDetails());
        assertNotNull(mapper.writeValueAsString(metricFetched));
    }

    @Test
    public void getEntityPipelineSyncMetricTestWithAllStages() throws JsonProcessingException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        String entityName = syncariEntity.getApiName();
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSaveToSinkStageSyncCycleId", 20f, 10);

        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,10,
                10, 0);
        syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric1, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_ENTITY_PIPELINE,"testSaveToSinkStageSyncCycleId", 20f);


        EntitySyncStatusMetric  syncStatusMetric2 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric2, EntitySynchStatusMetricSummary.Stage.PROCESSING_SOURCE_FIELD_PIPELINE,"testSaveToSinkStageSyncCycleId", 20f);

        EntitySyncStatusMetric  syncStatusMetric3 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric3, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_ENTITY_PIPELINE,"testSaveToSinkStageSyncCycleId", 20f);

        EntitySyncStatusMetric  syncStatusMetric4 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric4, EntitySynchStatusMetricSummary.Stage.PROCESSING_SINK_FIELD_PIPELINE,"testSaveToSinkStageSyncCycleId", 20f);

        EntitySyncStatusMetric  syncStatusMetric5 = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(), entityName,Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(),syncStatusMetric5, EntitySynchStatusMetricSummary.Stage.WRITING_DATA_TO_DESTINATION,"testSaveToSinkStageSyncCycleId", 20f);

        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric(syncariEntity.getId(),"testSaveToSinkStageSyncCycleId");
        assertTrue(metric.isPresent());
        assertEquals(metric.get().getSyncCycleId(), "testSaveToSinkStageSyncCycleId");

        SyncMetric metricFetched = syncStatusService.getEntityPipelineSyncMetric(syncariEntity.getId());
        assertEquals(syncariEntity.getId(), metricFetched.getSyncariEntityId());
        List<Stage> stages = metricFetched.getAllStages();
        assertNotNull(stages);
        assertEquals(6,stages.size());
        assertNotNull(stages.get(5).getDetails());
        assertNotNull(mapper.writeValueAsString(metricFetched));
    }


    @Test
    public void getEntityPipelineStreamStatus_TestRunningOnDraft() throws InterruptedException {
        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();

        EntityDefinition sfdcEntity = schemaService.getAllEntities(sfdcConnector.getId()).get(0);
        EntityDefinition syncariEntity = schemaService.getAllEntities(syncariConnector.getId()).get(0);
        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();

        // create a test pipeline
        PipelineTest test = new PipelineTest().setGraphId(graph.getId()).setStartTime(Instant.EPOCH).setEndTime(Instant.now())
                .setLimit(1).setUserId(SyncariContext.getUser().getId())
                .setRecordIds(null)
                .setOriginalStreamStatus(null);
        test.setStatus(Status.NEW);
        test = pipelineTestRepo.save(test);

        StreamInfo pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.TEST, pipelineStatus.getStatus());
        assertEquals(0, pipelineStatus.getLagTimeInSeconds());
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        // summary will be null for draft graph with test running
        assertNull(pipelineStatus.getSummary());
    }

    @Test
    public void getEntityPipelineStreamStatus_TestRunningOnPublishedWithDraft() throws InterruptedException {
        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();

        EntityDefinition sfdcEntity = schemaService.getAllEntities(sfdcConnector.getId()).get(0);
        EntityDefinition syncariEntity = schemaService.getAllEntities(syncariConnector.getId()).get(0);
        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();
        var draftGraph = graphService.createDraftFor(approvedGraph);

        // create a test pipeline
        PipelineTest test = new PipelineTest().setGraphId(draftGraph.getId()).setStartTime(Instant.EPOCH).setEndTime(Instant.now())
                .setLimit(1).setUserId(SyncariContext.getUser().getId())
                .setRecordIds(null)
                .setOriginalStreamStatus(null);
        test.setStatus(Status.NEW);
        test = pipelineTestRepo.save(test);

        StreamInfo pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        // status comes from test running on draft
        assertEquals(StreamInfo.Status.TEST, pipelineStatus.getStatus());

        // other fields along with summary comes from approved graph
        assertEquals(0, pipelineStatus.getLagTimeInSeconds());
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        assertEquals(syncStream.getLastSuccessfulSync(), pipelineStatus.getLastSyncTime());
        assertEquals(syncStream.getDetails(), pipelineStatus.getErrorDetails());
        // for all pipeline status individual entity status summaries are not pulled
        assertEquals(1, pipelineStatus.getSummary().getSources().size());
        assertEquals(1, pipelineStatus.getSummary().getSinks().size());
        var source = pipelineStatus.getSummary().getSources().get(0);

        assertEquals(sfdcEntity.getDisplayName(), source.getEntityName());
        assertEquals(sfdcEntity.getId(), source.getEntityId());
        assertEquals("testSynapse", source.getConnectorName());
        assertEquals(Constants.TEST_SYNAPSE, source.getConnectorType());

        var sink = pipelineStatus.getSummary().getSinks().get(0);
        assertEquals(sfdcEntity.getDisplayName(), sink.getEntityName());
        assertEquals(sfdcEntity.getId(), sink.getEntityId());
        assertEquals("testSynapse", sink.getConnectorName());
        assertEquals(Constants.TEST_SYNAPSE, sink.getConnectorType());
    }

    @Test
    public void getEntityPipelineStreamStatus_MissingSyncStream(){
        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();

        EntityDefinition sfdcEntity = schemaService.getAllEntities(sfdcConnector.getId()).get(0);
        EntityDefinition syncariEntity = schemaService.getAllEntities(syncariConnector.getId()).get(0);
        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();
        StreamInfo pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.QUEUED, pipelineStatus.getStatus());
        //delete syncStream
        streamRepo.delete(syncStream);
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.QUEUED, pipelineStatus.getStatus());

    }

    private Connector createConnector() {
        /*EntitySchema entitySchema = new EntitySchema("Account", "Account");
        entitySchema.addField(new AttributeSchema("Name","string").setDisplayName("Name"));
        entitySchema.addField(new AttributeSchema("Id","id").setDisplayName("Id"));
        when(salesforceService.describeAll(any())).thenReturn(List.of(entitySchema));
        when(salesforceService.getEntityMappings()).thenReturn(new SalesforceService().getEntityMappings());
        when(salesforceService.getName()).thenReturn("salesforce");
        when(salesforceService.isSource()).thenReturn(true);
        when(salesforceService.isSink()).thenReturn(true);*/
        var connector = new Connector("testSynapse", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://someurl");
        connector = connectorService.save(connector);
        connectorService.authenticated(connector.getId());
        connectorService.activate(connector.getId());
        //verify(salesforceService).describeAll(any());
        return connector;
    }
    
    @Test
    public void getAllPipelineStatusDetails(){
        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();

        EntityDefinition syncariEntity = schemaService.getAllEntities(syncariConnector.getId()).get(0);
        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);

        List<EntityPipelineDetails> allPipelineStreamStatus = syncStatusService.getAllPipelineStatusDetails();
        assertFalse(allPipelineStreamStatus.isEmpty());
        EntityPipelineDetails pipelineStatus = allPipelineStreamStatus.get(0);
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        assertEquals(Long.valueOf(4L), pipelineStatus.getFieldsMapped());
        assertFalse(pipelineStatus.getMergeConfig());
    }
    
    @Test
    public void getAllPipelineStatusDetails_DraftOnly(){
        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();

        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        List<EntityPipelineDetails> allPipelineStreamStatus = syncStatusService.getAllPipelineStatusDetails();
        assertFalse(allPipelineStreamStatus.isEmpty());
        EntityPipelineDetails pipelineStatus = allPipelineStreamStatus.stream()
                .filter(info -> info.getSyncariEntityId().equals(syncariEntity.getId()))
                .findFirst().get();
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        assertEquals(Long.valueOf(4L), pipelineStatus.getFieldsMapped());
    }
    
    @Test
    public void getAllPipelineStatusDetailsSyncMetric() throws JsonProcessingException, InterruptedException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        String entityName = syncariEntity.getApiName();
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId", 20f, 10);

        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId2", 20f, 10);


        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed(syncariEntity.getId());
        assertTrue(metric.isPresent());


        StreamInfo pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.QUEUED, pipelineStatus.getStatus());
        assertEquals(0, pipelineStatus.getLagTimeInSeconds());
        assertEquals(syncariEntity.getId(), pipelineStatus.getSyncariEntityId());
        assertEquals(syncStream.getLastSuccessfulSync(), pipelineStatus.getLastSyncTime());
        assertEquals(syncStream.getDetails(), pipelineStatus.getErrorDetails());

        Instant now = Instant.now();
        syncStream.setStatus(SyncStream.Status.RUNNING);
        syncStream.setLastSuccessfulSync(now);
        syncStream = streamRepo.save(syncStream);
        Thread.sleep(1000);
        pipelineStatus = syncStatusService.getEntityPipelineStreamStatus(syncariEntity.getId());
        assertEquals(StreamInfo.Status.RUNNING, pipelineStatus.getStatus());
        assertEquals(now.getEpochSecond(), pipelineStatus.getLastSyncTime().getEpochSecond());
        assertTrue(pipelineStatus.getLagTimeInSeconds() >= 1);

        List<EntityPipelineSyncMetric> metricFetched = syncStatusService.getAllPipelineStatusDetailsSyncMetric();
        assertFalse(metricFetched.isEmpty());
        assertEquals(syncariEntity.getId(), metricFetched.get(0).getSyncariEntityId());
        assertEquals(null, metricFetched.get(0).getCurrentActivity());
        assertTrue(metricFetched.get(0).getLastCycleDuration().getDuration() > 0);
    }
    
    @Test
    public void getAllPipelineStatusDetailsSyncMetric2() throws JsonProcessingException {

        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream syncStream = streamService.findStream(approvedGraph.getId()).get();

        String entityName = syncariEntity.getApiName();
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(sfdcConnector.getId(),sfdcConnector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId", 20f, 10);

        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId2", 20f, 10);


        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed(syncariEntity.getId());
        assertTrue(metric.isPresent());

        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,syncariEntity.getId(),entityName,syncStatusMetric,
                EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId2", 20f, 10);

        List<EntityPipelineSyncMetric> metricFetched = syncStatusService.getAllPipelineStatusDetailsSyncMetric();
        assertFalse(metricFetched.isEmpty());
        assertEquals(syncariEntity.getId(), metricFetched.get(0).getSyncariEntityId());
        assertEquals("reading_sources", metricFetched.get(0).getCurrentActivity());
        assertTrue(metricFetched.get(0).getLastCycleDuration().getDuration() > 0);
    }

    @Test
    public void getPipelineErrorSummary() {
        // setup pipeline error
        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream stream = streamService.getOrCreateReadyStream(approvedGraph.getId());
        stream.setErrorDetail(new PipelineError().setMessage("Test Error in the Pipeline").setDetails("Test Error in Pipeline. Caused by NullPointerException")
                .setPausedByError(true).setCount(5).setNodeId("testNodeId").setGraphId(approvedGraph.getId()).setScope(Scope.ATTRIBUTE));
        stream.setStatus(SyncStream.Status.PAUSED);
        streamService.save(stream);

        PipelineErrorSummary pipelineErrorSummary = syncStatusService.getEntityPipelineErrorSummary(syncariEntity.getId());
        assertTrue(pipelineErrorSummary != null);
        assertEquals(syncariEntity.getId(), pipelineErrorSummary.getSyncariEntityId());
        assertTrue(pipelineErrorSummary.getError() != null);
        assertEquals("Test Error in the Pipeline", pipelineErrorSummary.getError().getErrorMessage());
        assertEquals("Test Error in Pipeline. Caused by NullPointerException", pipelineErrorSummary.getError().getErrorDetail());
        assertEquals("testNodeId", pipelineErrorSummary.getError().getNodeId());
        assertEquals(Scope.ATTRIBUTE.name(), pipelineErrorSummary.getError().getLevel().name());

        stream = streamService.getOrCreateReadyStream(approvedGraph.getId());
        stream.setErrorDetail(new PipelineError().setMessage("Test Error in the Pipeline").setDetails("Test Error in Pipeline. Caused by NullPointerException")
                .setPausedByError(true).setCount(10).setNodeId("testNodeId").setGraphId(approvedGraph.getId()).setScope(Scope.ATTRIBUTE));
        stream.setStatus(SyncStream.Status.RUNNING);
        streamService.save(stream);

        pipelineErrorSummary = syncStatusService.getEntityPipelineErrorSummary(syncariEntity.getId());
        assertTrue(pipelineErrorSummary != null);
        assertEquals(syncariEntity.getId(), pipelineErrorSummary.getSyncariEntityId());
        assertTrue(pipelineErrorSummary.getError() != null);
        assertEquals("Test Error in the Pipeline", pipelineErrorSummary.getError().getErrorMessage());
        assertEquals("Test Error in Pipeline. Caused by NullPointerException", pipelineErrorSummary.getError().getErrorDetail());
        assertEquals("testNodeId", pipelineErrorSummary.getError().getNodeId());
        assertEquals(Scope.ATTRIBUTE.name(), pipelineErrorSummary.getError().getLevel().name());
    }

    @Test
    public void getPipelineWarningSummary() {
        // setup pipeline error
        Connector sfdcConnector = createConnector();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();

        MappingGraph graph = graphService.retrieveEntityGraph(syncariEntity.getId()).get();
        var approvedGraph = graphService.approveDraft(graph);
        SyncStream stream = streamService.getOrCreateReadyStream(approvedGraph.getId());

        String syncCycleId = UUID.randomUUID().toString();
        List<EntitySyncErrorMetric> syncErrorMetrics = List.of(
                new EntitySyncErrorMetric().setErrorMessage("Error Message 1").setErrorDetails("Error Message Details 1").setNodeId("nodeId1").setTargetId("graphId1").setScope(Scope.ATTRIBUTE).setErrorCount(7).setTotalCount(10),
                new EntitySyncErrorMetric().setErrorMessage("Error Message 2").setErrorDetails("Error Message Details 2").setNodeId("nodeId1").setTargetId("graphId1").setScope(Scope.ATTRIBUTE).setErrorCount(10).setTotalCount(10),
                new EntitySyncErrorMetric().setErrorMessage("Error Message 3").setErrorDetails("Error Message Details 3").setNodeId("nodeId2").setTargetId("graphId2").setScope(Scope.ENTITY).setErrorCount(500).setTotalCount(2000),
                new EntitySyncErrorMetric().setErrorMessage("Error Message 4").setErrorDetails("Error Message Details 4").setNodeId("nodeId3").setTargetId("graphId3").setScope(Scope.ATTRIBUTE).setErrorCount(50).setTotalCount(100),
                new EntitySyncErrorMetric().setErrorMessage("Error Message 5").setErrorDetails("Error Message Details 5").setNodeId("nodeId3").setTargetId("graphId3").setScope(Scope.ATTRIBUTE).setErrorCount(2000).setTotalCount(2000)

        );

        EntitySyncStatusMetric metric = new EntitySyncStatusMetric();
        syncDetailMetricService.findOrCreateSyncSourceDetails(syncariEntity.getApiName(), syncariEntity.getId(), syncariEntity.getApiName(), new EntitySyncStatusMetric(), EntitySynchStatusMetricSummary.Stage.READING_SOURCE_SAVES_STAGE, false, false, syncCycleId, 20f, 10);
        syncDetailMetricService.updateSyncDetailMetric(syncariEntity.getId(), metric, EntitySynchStatusMetricSummary.Stage.FINISHED_PIPELINE_EXECUTION, syncCycleId, 20f);

        syncDetailMetricService.updateSyncErrorMetric(syncariEntity.getId(), syncCycleId, syncErrorMetrics);

        PipelineErrorSummary pipelineErrorSummary = syncStatusService.getEntityPipelineErrorSummary(syncariEntity.getId());
        assertTrue(pipelineErrorSummary != null);
        assertEquals(syncariEntity.getId(), pipelineErrorSummary.getSyncariEntityId());
        assertEquals(syncCycleId, pipelineErrorSummary.getSyncCycleId());
        assertEquals(5, pipelineErrorSummary.getWarnings().size());
        assertEquals("Error Message 5", pipelineErrorSummary.getWarnings().get(0).getErrorMessage());
        assertEquals("Error Message 2", pipelineErrorSummary.getWarnings().get(1).getErrorMessage());
        assertEquals("Error Message 1", pipelineErrorSummary.getWarnings().get(2).getErrorMessage());
        assertEquals("Error Message 4", pipelineErrorSummary.getWarnings().get(3).getErrorMessage());
        assertEquals("Error Message 3", pipelineErrorSummary.getWarnings().get(4).getErrorMessage());

        assertEquals(Scope.ATTRIBUTE.name(), pipelineErrorSummary.getWarnings().get(0).getLevel().name());
        assertEquals(Scope.ATTRIBUTE.name(), pipelineErrorSummary.getWarnings().get(1).getLevel().name());
        assertEquals(Scope.ATTRIBUTE.name(), pipelineErrorSummary.getWarnings().get(2).getLevel().name());
        assertEquals(Scope.ATTRIBUTE.name(), pipelineErrorSummary.getWarnings().get(3).getLevel().name());
        assertEquals(Scope.ENTITY.name(), pipelineErrorSummary.getWarnings().get(4).getLevel().name());
    }

}
