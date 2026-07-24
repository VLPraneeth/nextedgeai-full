package com.syncari.core.service;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.service.SalesforceService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.model.Connector;
import com.syncari.core.model.misc.EntitySyncErrorMetric;
import com.syncari.core.model.misc.EntitySyncStatusMetric;
import com.syncari.core.model.misc.EntitySynchStatusMetricSummary.Stage;
import com.syncari.core.model.misc.ErrorType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.SyncDetailMetric;
import org.bson.BsonMaximumSizeExceededException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SyncDetailMetricServiceTest extends AbstractSyncariTest {

    @MockBean
    private SalesforceService salesforceService;

    @Autowired
    SyncDetailMetricService syncDetailMetricService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    EndSystemConfig config;

    private Connector connector;

    @Override
    @Before
    public void setUp(){
        super.setUp();
        if(connector == null) {
            createConnector();
        }
    }


    private void createConnector() {
        EntitySchema entitySchema = new EntitySchema("Account", "Account");
        entitySchema.addField(new AttributeSchema("Name","string").setDisplayName("Name"));
        entitySchema.addField(new AttributeSchema("Id","id").setDisplayName("Id"));
        when(salesforceService.describeAll(any())).thenReturn(List.of(entitySchema));
        when(salesforceService.getName()).thenReturn("salesforce");
        connector = new Connector("sfdc1", connectorService.describe("salesforce"),
                config.getSalesforceUrl(), config.getUser(), config.getPassword());
        connector.getAuthConfig().setToken(config.getToken());
        connector = connectorService.save(connector);
        connectorService.authenticated(connector.getId());
        connectorService.activate(connector.getId());
        verify(salesforceService).describeAll(any());
    }


    // For one source of all stages test cases
    @Test
    public void testSourceStage(){
        String entityName = "test1";
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric,
                Stage.READING_SOURCE_SAVES_STAGE, false, false, "testSourceStageSyncCycleId", 20f, 10);
        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric("testId","testSourceStageSyncCycleId");
        assertTrue(metric.isPresent());
        assertTrue(metric.get().getSummary().getSources().keySet().contains(connector.getId() + "_" + entityName));

    }

    @Test
    public void testEntityPipelineStage(){
        String entityName = "test1";
        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric1,
                Stage.READING_SOURCE_SAVES_STAGE, false, false,"testEntityPipelineStageSyncCycleId", 20f, 10);
        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric("testId","testEntityPipelineStageSyncCycleId");
        assertTrue(metric.isPresent());

        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,10,
                10, 0,0);
        syncDetailMetricService.updateSyncDetailMetric("testId",syncStatusMetric, Stage.PROCESSING_SOURCE_ENTITY_PIPELINE,"testEntityPipelineStageSyncCycleId", 20f);
        Optional<SyncDetailMetric> metric1 = syncDetailMetricService.findLatestSyncDetailMetric("testId","testEntityPipelineStageSyncCycleId");
        assertTrue(metric1.isPresent());
        assertNotNull(metric1.get().getSummary().getSourceEp());
        assertTrue(metric1.get().getSummary().getSourceEp().keySet().contains(connector.getId() + "_" + entityName));

    }

    @Test
    public void testFieldPipelineStage(){
        String entityName = "test1";
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric, Stage.READING_SOURCE_SAVES_STAGE,false,false,"testFieldPipelineStageSyncCycleId", 20f, 10);
        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric("testId","testFieldPipelineStageSyncCycleId");
        assertTrue(metric.isPresent());

        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric("testId",syncStatusMetric1, Stage.PROCESSING_SOURCE_FIELD_PIPELINE,"testFieldPipelineStageSyncCycleId", 20f);
        Optional<SyncDetailMetric> metric1 = syncDetailMetricService.findLatestSyncDetailMetric("testId","testFieldPipelineStageSyncCycleId");
        assertTrue(metric1.isPresent());
        assertNotNull(metric1.get().getSummary().getSourceFp());
        assertTrue(metric1.get().getSummary().getSourceFp().keySet().contains(connector.getId() + "_" + entityName));
    }

    @Test
    public void testDSWritesStage(){
        String entityName = "test1";
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 2,8);

        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric, Stage.READING_SOURCE_SAVES_STAGE,false,false,"testFieldPipelineStageSyncCycleId", 20f, 10);
        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric("testId","testFieldPipelineStageSyncCycleId");
        assertTrue(metric.isPresent());

        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric("testId",syncStatusMetric1, Stage.PROCESSING_DATASTORE_WRITES,"testFieldPipelineStageSyncCycleId", 20f);
        Optional<SyncDetailMetric> metric1 = syncDetailMetricService.findLatestSyncDetailMetric("testId","testFieldPipelineStageSyncCycleId");
        assertTrue(metric1.isPresent());
        assertNotNull(metric1.get().getSummary().getSourceDsWrites());
        assertTrue(metric1.get().getSummary().getSourceDsWrites().keySet().contains(connector.getId() + "_" + entityName));
    }

    @Test
    public void testSaveToSinkStage(){
        String entityName = "test1";
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric, Stage.READING_SOURCE_SAVES_STAGE,false,false,"testSaveToSinkStageSyncCycleId", 20f, 10);
        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric("testId","testSaveToSinkStageSyncCycleId");
        assertTrue(metric.isPresent());

        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric("testId",syncStatusMetric1, Stage.PROCESSING_SINK_ENTITY_PIPELINE,"testSaveToSinkStageSyncCycleId", 20f);
        Optional<SyncDetailMetric> metric1 = syncDetailMetricService.findLatestSyncDetailMetric("testId","testSaveToSinkStageSyncCycleId");
        assertTrue(metric1.isPresent());
        assertNotNull(metric1.get().getSummary().getSinksEp());
        assertTrue(metric1.get().getSummary().getSinksEp().keySet().contains(connector.getId() + "_" + entityName));
    }

    @Test
    public void testDeleteSyncDetailMetric(){
        String entityName = "test1";
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric, Stage.READING_SOURCE_SAVES_STAGE,false,false,"testSaveToSinkStageSyncCycleId", 20f, 10);
        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric("testId","testSaveToSinkStageSyncCycleId");
        assertTrue(metric.isPresent());
        syncDetailMetricService.deleteSyncDetailMetric(metric.get().getSyncariEntityId());
        metric = syncDetailMetricService.findLatestSyncDetailMetric("testId","testSaveToSinkStageSyncCycleId");
        assertFalse(metric.isPresent());
    }

    @Test
    public void testWriteToDestinationSyncDetailMetric(){
        String entityName = "test1";
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric, Stage.READING_SOURCE_SAVES_STAGE,false,false,"testSaveToSinkStageSyncCycleId", 20f, 10);
        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetric("testId","testSaveToSinkStageSyncCycleId");
        assertTrue(metric.isPresent());

        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric("testId",syncStatusMetric1, Stage.WRITING_DATA_TO_DESTINATION,"testSaveToSinkStageSyncCycleId", 20f);
        Optional<SyncDetailMetric> metric1 = syncDetailMetricService.findLatestSyncDetailMetric("testId","testSaveToSinkStageSyncCycleId");
        assertTrue(metric1.isPresent());
        assertNotNull(metric1.get().getSummary().getSinkWrites());
    }

    @Test
    public void testLatestSyncDetailMetricWithProcessed(){
        String entityName = "test1";
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                0, 0,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric, Stage.READING_SOURCE_SAVES_STAGE,false,false,"testSaveToSinkStageSyncCycleId", 20f, 0);
        Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed("testId");
        assertFalse(metric.isPresent());

        EntitySyncStatusMetric  syncStatusMetric1 = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                0, 0,0, 2, 3, 0, 0, ChronoUnit.MILLIS);
        syncDetailMetricService.updateSyncDetailMetric("testId",syncStatusMetric1, Stage.WRITING_DATA_TO_DESTINATION,"testSaveToSinkStageSyncCycleId", 20f);
        Optional<SyncDetailMetric> metric1 = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed("testId");
        assertFalse(metric1.isPresent());

        EntitySyncStatusMetric syncStatusMetric2 = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric, Stage.READING_SOURCE_SAVES_STAGE,false,false,"testSaveToSinkStageSyncCycleId1", 20f, 10);
        Optional<SyncDetailMetric> metric2 = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed("testId");
        assertTrue(metric2.isPresent());
    }

    @Test
    public void testUpdateSyncariErrorMetric() {

        String entityName = "test1";
        EntitySyncStatusMetric  syncStatusMetric = new EntitySyncStatusMetric(connector.getId(),connector.getName(),entityName, Instant.now(),10f,
                10, 2,8);
        SyncDetailMetric syncDetailMetric = syncDetailMetricService.findOrCreateSyncSourceDetails(entityName,"testId",entityName,syncStatusMetric, Stage.READING_SOURCE_SAVES_STAGE,
                false,false,"testSaveToSinkStageSyncCycleId", 20f, 10).get();

        assertEquals("testSaveToSinkStageSyncCycleId", syncDetailMetric.getSyncCycleId());

        EntitySyncErrorMetric errorMetric1 = new EntitySyncErrorMetric("testError1", "testErrorDetails1", "testNodeId1", "testTargetId", Scope.ENTITY, 10, 20, ErrorType.ACTION);
        EntitySyncErrorMetric errorMetric2 = new EntitySyncErrorMetric("testError2", "testErrorDetails2", "testNodeId2", "testTargetId", Scope.ENTITY, 10, 20, ErrorType.ACTION);
        syncDetailMetricService.updateSyncErrorMetric("testId", "testSaveToSinkStageSyncCycleId", List.of(errorMetric1, errorMetric2));

        final Optional<SyncDetailMetric> metric = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed("testId");
        assertTrue(metric.isPresent());
        assertEquals("testSaveToSinkStageSyncCycleId", metric.get().getSyncCycleId());
        assertEquals(2, metric.get().getSummary().getErrors().size());
        assertEquals("testError1", metric.get().getSummary().getErrors().get(0).getErrorMessage());
        assertEquals("testNodeId1", metric.get().getSummary().getErrors().get(0).getNodeId());
        assertEquals("testError2", metric.get().getSummary().getErrors().get(1).getErrorMessage());
        assertEquals("testNodeId2", metric.get().getSummary().getErrors().get(1).getNodeId());

        EntitySyncErrorMetric errorMetric3 = new EntitySyncErrorMetric("testError3", "testErrorDetails3", "testNodeId3", "testTargetId", Scope.ENTITY, 10, 20, ErrorType.ACTION);
        EntitySyncErrorMetric errorMetric4 = new EntitySyncErrorMetric("testError4", "testErrorDetails4", "testNodeId4", "testTargetId", Scope.ENTITY, 10, 20, ErrorType.ACTION);
        syncDetailMetricService.updateSyncErrorMetric("testId", "testSaveToSinkStageSyncCycleId", List.of(errorMetric3, errorMetric4));

        final Optional<SyncDetailMetric> metric1 = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed("testId");
        assertTrue(metric1.isPresent());
        assertEquals("testSaveToSinkStageSyncCycleId", metric1.get().getSyncCycleId());
        assertEquals(4, metric1.get().getSummary().getErrors().size());
        assertEquals("testError3", metric1.get().getSummary().getErrors().get(2).getErrorMessage());
        assertEquals("testNodeId3", metric1.get().getSummary().getErrors().get(2).getNodeId());
        assertEquals("testError4", metric1.get().getSummary().getErrors().get(3).getErrorMessage());
        assertEquals("testNodeId4", metric1.get().getSummary().getErrors().get(3).getNodeId());

        List<EntitySyncErrorMetric> errorMetrics = IntStream.range(0, 2500).mapToObj(i ->
                new EntitySyncErrorMetric("testError" + i, "testErrorDetails" + i, "testNodeId" + i, "testTargetId", Scope.ENTITY, 10, 20, ErrorType.ACTION))
                .collect(Collectors.toList());
        syncDetailMetricService.updateSyncErrorMetric("testId", "testSaveToSinkStageSyncCycleId", errorMetrics);
        final Optional<SyncDetailMetric> metric2 = syncDetailMetricService.findLatestSyncDetailMetricWithRecordsProcessed("testId");
        assertEquals(2004, metric2.get().getSummary().getErrors().size());
    }

    @Test
    public void testUpdateSyncariErrorMetricMaxSize() {

        var originCustomerMongoTemplate = syncDetailMetricService.customerMongoTemplate;

        try {
            var mockMongoTemplate = mock(MongoTemplate.class);
            syncDetailMetricService.customerMongoTemplate =mockMongoTemplate;

            List<EntitySyncErrorMetric> errorMetrics = IntStream.range(0, 2000).mapToObj(i ->
                            new EntitySyncErrorMetric("testError" + i, "testErrorDetails" + i, "testNodeId" + i, "testTargetId", Scope.ENTITY, 10, 20, ErrorType.ACTION))
                    .collect(Collectors.toList());

            when(mockMongoTemplate.findAndModify(any(), any(), any(), any(Class.class))).thenThrow(BsonMaximumSizeExceededException.class);

            syncDetailMetricService.updateSyncErrorMetric("testId", "testSaveToSinkStageSyncCycleId", errorMetrics);
            verify(mockMongoTemplate, times(11  )).findAndModify(any(), any(), any(), any(Class.class));

            mockMongoTemplate = mock(MongoTemplate.class);
            syncDetailMetricService.customerMongoTemplate =mockMongoTemplate;

            errorMetrics = IntStream.range(0, 4096).mapToObj(i ->
                            new EntitySyncErrorMetric("testError" + i, "testErrorDetails" + i, "testNodeId" + i, "testTargetId", Scope.ENTITY, 10, 20, ErrorType.ACTION))
                    .collect(Collectors.toList());

            when(mockMongoTemplate.findAndModify(any(), any(), any(), any(Class.class))).thenThrow(BsonMaximumSizeExceededException.class);

            syncDetailMetricService.updateSyncErrorMetric("testId", "testSaveToSinkStageSyncCycleId", errorMetrics);

            verify(mockMongoTemplate, times(13  )).findAndModify(any(), any(), any(), any(Class.class));
        } finally {
            syncDetailMetricService.customerMongoTemplate = originCustomerMongoTemplate;
        }



    }

    /*
    For one source of all stages test cases
    1) Test to sync metrics for save stage batch results
    2) Test to sync metrics for execute entity pipeline
    3) Test to sync metrics for execute field pipeline
    4) Test to sync metrics for save to sink metrics

    For one multiple sources of all stages test cases
    1) Test to sync metrics for save stage batch results
    2) Test to sync metrics for execute entity pipeline
    3) Test to sync metrics for execute field pipeline
    4) Test to sync metrics for save to sink metrics

    For one source resync of all stages test case
    1) Test to sync metrics for save stage batch results
    2) Test to sync metrics for execute entity pipeline
    3) Test to sync metrics for execute field pipeline
    4) Test to sync metrics for save to sink metrics

    For one source resync and another sync of all stages test case
    1) Test to sync metrics for save stage batch results
    2) Test to sync metrics for execute entity pipeline
    3) Test to sync metrics for execute field pipeline
    4) Test to sync metrics for save to sink metrics

    For one source sync and another sync of all stages test case
    1) Test to sync metrics for save stage batch results
    2) Test to sync metrics for execute entity pipeline
    3) Test to sync metrics for execute field pipeline
    4) Test to sync metrics for save to sink metrics

    For one source sync of all stages with last sync did not go through all the stages
    1) Test to sync metrics for save stage batch results
    2) Test to sync metrics for execute entity pipeline
    3) Test to sync metrics for execute field pipeline
    4) Test to sync metrics for save to sink metrics

     */
}
