package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.lang.String.format;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.service.SalesforceService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.Notification;
import com.syncari.core.model.ResyncDetail;
import com.syncari.core.model.SyncDetail;
import com.syncari.core.model.SyncStream;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.ErrorCategory;
import com.syncari.core.model.ErrorPriority;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.repositories.customer.NotificationRepo;
import com.syncari.core.repositories.customer.ResyncDetailRepo;
import com.syncari.core.repositories.customer.StreamRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;

@DirtiesContext
public class ResyncServiceTest extends AbstractSyncariTest {

    @MockBean
    private SalesforceService salesforceService;

    @MockBean
    private ErrorNotificationService errorNotificationService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    EndSystemConfig config;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    MappingGraphRepo mappingGraphRepo;

    @Autowired
    ResyncService resyncService;

    @Autowired
    ResyncDetailRepo resyncDetailRepo;

    @Autowired
    ConnectorRepo connectorRepo;

    @Autowired
    NotificationRepo notificationRepo;

    @Autowired
    SyncDetailRepo syncRepo;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    StreamService streamService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    WatermarkService watermarkService;

    @Autowired
    StreamRepo streamRepo;

    @Autowired
    MappingNodeRepo nodeRepo;

    private Connector sfdcConnector;
    private EntityDefinition sfdcEntity;
    private EntityDefinition syncariEntity;

    @Before
    public void setUp() {
        super.setUp();
        if(sfdcConnector == null) {
            sfdcConnector = createConnector();
            sfdcEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(), "Account").get();
            syncariEntity = schemaService.getSyncariEntityByName("account").get();
        }
    }

    @Override
    public void tearDown()
    {
        super.tearDown();
        resetRepos(connectorRepo, entityProxyRepo, resyncDetailRepo, notificationRepo, syncRepo, mappingGraphRepo, streamRepo, nodeRepo);

    }

    @Test
    public void createResynRequest_NoApprovedGraph(){

        // test fails in case of no graph
        try{
            resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
            fail();
        } catch (Exception e) {
            assertEquals(String.format("MappingGraph with syncariEntityId %s not found", syncariEntity.getId()), e.getMessage());
        }

        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));

        // test fails for graph in DRAFT as well
        try{
            resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
            fail();
        } catch (Exception e) {
            assertEquals(String.format("MappingGraph with syncariEntityId %s not found", syncariEntity.getId()), e.getMessage());
        }

    }

    @Test
    public void resyncChangeStatus(){
        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(Map.of("sourceEntityId", ResyncStatus.NEW))
                .setSyncariEntityId("syncariEntityId")
                .setSyncariEntityName("syncariEntityName")
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.NEW);
        resync = resyncDetailRepo.save(resync);

        try{
            resyncService.changeStatus(resync, ResyncStatus.SUCCESS);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Resync with Id %s is %s and cannot be %s", resync.getId(), resync.getStatus().name(), ResyncStatus.SUCCESS.name()),
                    e.getMessage());
        }
        resync = resyncService.changeStatus(resync, ResyncStatus.PROCESSING);
        assertEquals(ResyncStatus.PROCESSING, resync.getStatus());

        try{
            resyncService.changeStatus(resync, ResyncStatus.NEW);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Resync with Id %s is %s and cannot be %s", resync.getId(), resync.getStatus().name(), ResyncStatus.NEW.name()),
                    e.getMessage());
        }

        resync = resyncService.changeStatus(resync, ResyncStatus.SUCCESS);
        assertEquals(ResyncStatus.SUCCESS, resync.getStatus());

        // different statuses -> ERROR status
        resync.setStatus(ResyncStatus.NEW);
        resync = resyncDetailRepo.save(resync);
        resync = resyncService.changeStatus(resync, ResyncStatus.ERROR);
        assertEquals(ResyncStatus.ERROR, resync.getStatus());

        resync.setStatus(ResyncStatus.PROCESSING);
        resync = resyncDetailRepo.save(resync);
        resync = resyncService.changeStatus(resync, ResyncStatus.ERROR);
        assertEquals(ResyncStatus.ERROR, resync.getStatus());

        resync.setStatus(ResyncStatus.SUCCESS);
        resync = resyncDetailRepo.save(resync);
        try{
            resyncService.changeStatus(resync, ResyncStatus.ERROR);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Resync with Id %s is %s and cannot be %s", resync.getId(), resync.getStatus().name(), ResyncStatus.ERROR.name()),
                    e.getMessage());
        }

        resync.setStatus(ResyncStatus.CANCELLED);
        resync = resyncDetailRepo.save(resync);
        try{
            resyncService.changeStatus(resync, ResyncStatus.ERROR);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Resync with Id %s is %s and cannot be %s", resync.getId(), resync.getStatus().name(), ResyncStatus.ERROR.name()),
                    e.getMessage());
        }

        // different statuses to CANCEL_REQUESTED
        resync.setStatus(ResyncStatus.NEW);
        resync = resyncDetailRepo.save(resync);
        resync = resyncService.changeStatus(resync, ResyncStatus.CANCEL_REQUESTED);
        assertEquals(ResyncStatus.CANCEL_REQUESTED, resync.getStatus());

        resync.setStatus(ResyncStatus.PROCESSING);
        resync = resyncDetailRepo.save(resync);
        resync = resyncService.changeStatus(resync, ResyncStatus.CANCEL_REQUESTED);
        assertEquals(ResyncStatus.CANCEL_REQUESTED, resync.getStatus());

        resync.setStatus(ResyncStatus.SUCCESS);
        resync = resyncDetailRepo.save(resync);
        try{
            resyncService.changeStatus(resync, ResyncStatus.CANCEL_REQUESTED);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Resync with Id %s is %s and cannot be %s", resync.getId(), resync.getStatus().name(), ResyncStatus.CANCEL_REQUESTED.name()),
                    e.getMessage());
        }

        resync.setStatus(ResyncStatus.ERROR);
        resync = resyncDetailRepo.save(resync);
        try{
            resyncService.changeStatus(resync, ResyncStatus.CANCEL_REQUESTED);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Resync with Id %s is %s and cannot be %s", resync.getId(), resync.getStatus().name(), ResyncStatus.CANCEL_REQUESTED.name()),
                    e.getMessage());
        }
        
     // different statuses to CANCELLED
        resync.setStatus(ResyncStatus.CANCEL_REQUESTED);
        resync = resyncDetailRepo.save(resync);
        resync = resyncService.changeStatus(resync, ResyncStatus.CANCELLED);
        assertEquals(ResyncStatus.CANCELLED, resync.getStatus());

        resync.setStatus(ResyncStatus.CANCEL_REQUESTED);
        resync = resyncDetailRepo.save(resync);
        resync = resyncService.changeStatus(resync, ResyncStatus.ERROR);
        assertEquals(ResyncStatus.ERROR, resync.getStatus());

        resync.setStatus(ResyncStatus.SUCCESS);
        resync = resyncDetailRepo.save(resync);
        try{
            resyncService.changeStatus(resync, ResyncStatus.CANCELLED);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Resync with Id %s is %s and cannot be %s", resync.getId(), resync.getStatus().name(), ResyncStatus.CANCELLED.name()),
                    e.getMessage());
        }

        resync.setStatus(ResyncStatus.ERROR);
        resync = resyncDetailRepo.save(resync);
        try{
            resyncService.changeStatus(resync, ResyncStatus.CANCELLED);
            fail();
        } catch (RuntimeException e){
            assertEquals(String.format("Resync with Id %s is %s and cannot be %s", resync.getId(), resync.getStatus().name(), ResyncStatus.CANCELLED.name()),
                    e.getMessage());
        }
        
        resync.setStatus(ResyncStatus.NEW);
        resync = resyncDetailRepo.save(resync);
        resyncService.changeStatus(resync, ResyncStatus.CANCELLED);
        assertEquals(ResyncStatus.CANCELLED, resync.getStatus());

        resync.setStatus(ResyncStatus.PROCESSING);
        resync = resyncDetailRepo.save(resync);
        resyncService.changeStatus(resync, ResyncStatus.CANCELLED);
        assertEquals(ResyncStatus.CANCELLED, resync.getStatus());

    }

    @Test
    public void createResyncRequest_InvalidStreamStatus(){

        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
        mappingGraph.setDraftStatus(DraftStatus.APPROVED);
        mappingGraph = mappingGraphRepo.save(mappingGraph);

        SyncStream stream1 = streamRepo.save(new SyncStream()
                .setGraphId(mappingGraph.getId())
                .setStatus(SyncStream.Status.STOPPED)
                .setCheckin(Instant.now()));

        try{
            resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
            fail();
        } catch (Exception e) {
            assertEquals(String.format("Error creating resync request. Pipeline status is %s", stream1.getStatus().name()), e.getMessage());
        }
    }

    @Test
    public void createResyncRequest_Success(){

        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
        mappingGraph.setDraftStatus(DraftStatus.APPROVED);
        mappingGraph = mappingGraphRepo.save(mappingGraph);

        SyncStream stream1 = streamRepo.save(new SyncStream()
                .setGraphId(mappingGraph.getId())
                .setStatus(SyncStream.Status.RUNNING)
                .setCheckin(Instant.now()));

        ResyncDetail resync = resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));

        assertTrue(resyncService.getResyncDetail(resync.getId()) != null);
        assertEquals(1, resync.getEntitiesToResync().size());
        assertTrue(resync.getEntitiesToResync().containsKey(sfdcEntity.getId()));
        assertEquals(ResyncDetail.Mode.RESYNC, resync.getMode());
        assertEquals(syncariEntity.getId(),resync.getSyncariEntityId());
        assertEquals(0l,resync.getStartTime().toEpochMilli());
        assertEquals(1l,resync.getEndTime().toEpochMilli());
        assertEquals(ResyncStatus.NEW, resync.getStatus());
        assertEquals(ResyncStatus.NEW, resync.getEntitiesToResync().get(sfdcEntity.getId()));
    }

    @Test
    public void createResyncRequest_Success_OriginalWatermark(){

        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
        mappingGraph.setDraftStatus(DraftStatus.APPROVED);
        mappingGraph = mappingGraphRepo.save(mappingGraph);

        SyncStream stream1 = streamRepo.save(new SyncStream()
                .setGraphId(mappingGraph.getId())
                .setStatus(SyncStream.Status.RUNNING)
                .setCheckin(Instant.now()));

        Watermark wm = new Watermark(0, 0,true,0).setDirection(SyncDirection.INBOUND);
        syncRepo.save(new SyncDetail(sfdcEntity.getId(), syncariEntity.getApiName(), wm));

        List<EntityDefinition> sourceEntities = List.of(sfdcEntity.getId()).stream().map(schemaService::getEntity).collect(Collectors.toList());
        // set the watermark for each source entity's sync detail
        Map<String, Watermark> originalSyncWatermarks = new HashMap<>();
        sourceEntities.stream().forEach(source -> {
            SyncDetail existing = watermarkService.findUpstreamWatermark(syncariEntity.getApiName(), source.getId()).orElse(null);
            if (existing != null) originalSyncWatermarks.put(existing.getId(), existing.getWatermark());
        });

        ResyncDetail resync = resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));

        originalSyncWatermarks.forEach((k, v) -> {
            assertTrue(resync.getOriginalSyncWatermarks().containsKey(k));
            assertEquals(v.getStart(), resync.getOriginalSyncWatermarks().get(k).getStart());
            assertEquals(v.getEnd(), resync.getOriginalSyncWatermarks().get(k).getEnd());
            assertEquals(v.getDirection(), resync.getOriginalSyncWatermarks().get(k).getDirection());
        });
    }

    @Test
    public void createResyncRequest_InitialSync(){

        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
        mappingGraph.setDraftStatus(DraftStatus.APPROVED);
        mappingGraph = mappingGraphRepo.save(mappingGraph);

        SyncStream stream1 = streamRepo.save(new SyncStream()
                .setGraphId(mappingGraph.getId())
                .setStatus(SyncStream.Status.RUNNING)
                .setCheckin(Instant.now()));

        ResyncDetail resync = resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()),
                Instant.ofEpochMilli(0), Instant.ofEpochMilli(1), true);

        assertTrue(resyncService.getResyncDetail(resync.getId()) != null);
        assertEquals(1, resync.getEntitiesToResync().size());
        assertTrue(resync.getEntitiesToResync().containsKey(sfdcEntity.getId()));
        assertEquals(ResyncDetail.Mode.INITIALSYNC, resync.getMode());
        assertEquals(syncariEntity.getId(),resync.getSyncariEntityId());
        assertEquals(0l,resync.getStartTime().toEpochMilli());
        assertEquals(1l,resync.getEndTime().toEpochMilli());
        assertEquals(ResyncStatus.NEW, resync.getStatus());
        assertEquals(ResyncStatus.NEW, resync.getEntitiesToResync().get(sfdcEntity.getId()));
    }

    @Test
    public void processNewResync_NoWatermarkExist(){

        MappingGraph mappingGraph = mappingGraphRepo
            .save(new MappingGraph().setName("Account Map")
            .setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));

        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(Map.of(sfdcEntity.getId(), ResyncStatus.NEW))
                .setSyncariEntityId(syncariEntity.getId())
                .setSyncariEntityName(syncariEntity.getApiName())
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.NEW);
        resync = resyncDetailRepo.save(resync);

        assertEquals(ResyncStatus.NEW, resync.getStatus());

        try {
            resyncService.processNewResync(syncariEntity.getId());
            fail();
        } catch (Exception e){
            e.printStackTrace();
            assertEquals(String.format("No upstream watermark found for syncariEntity %s and sourceEntityId %s", syncariEntity.getApiName(), sfdcEntity.getId()), e.getMessage());
        }
        ResyncDetail erroredResync = resyncService.getResyncDetail(resync.getId());

        assertEquals(ResyncStatus.ERROR, erroredResync.getStatus());
        assertEquals(String.format("No upstream watermark found for syncariEntity %s and sourceEntityId %s", syncariEntity.getApiName(), sfdcEntity.getId()), erroredResync.getErrorMsg());

//        assertEquals(1, notificationRepo.findAll().size());
//        Notification notif = notificationRepo.findAll().get(0);
//        assertEquals(NotificationType.ERROR, notif.getType());
//        assertEquals("Resync Failed for Entity Account", notif.getSubject());

    }

    @Test
    public void processNewResync(){
        MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
        MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
        assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());
        var approved = mappingGraphService.approveDraft(defaultEntityGraph);
        assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());

        Watermark wm = new Watermark(0, 0,true,0).setDirection(SyncDirection.INBOUND);
        syncRepo.save(new SyncDetail(sfdcEntity.getId(), syncariEntity.getApiName(), wm));

        Optional<SyncStream> upStream = streamService.findStream(approved.getId());
        assertTrue(upStream.isPresent());
        SyncStream.Status originalSyncStatus = upStream.get().getStatus();
        List<Notification> notifications = notificationRepo.findAll();
        int beforeCalling = 0;
        if (CollectionUtils.isNotEmpty(notifications)){
            beforeCalling = notificationRepo.findAll().size();
        }

        ResyncDetail resync = resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()),
                Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
        int afterCalling = notificationRepo.findAll().size();

        assertEquals(ResyncStatus.NEW, resync.getStatus());
//        assertEquals(1, afterCalling-beforeCalling);
//        Notification notif = notificationRepo.findAll().get(afterCalling-1);
//        assertEquals(NotificationType.INFO, notif.getType());
//        assertEquals("Historical Sync initiated for Entity Account", notif.getSubject());

        resyncService.processNewResync(syncariEntity.getId());
        ResyncDetail processedResync = resyncService.getResyncDetail(resync.getId());

        assertEquals(ResyncStatus.PROCESSING, processedResync.getStatus());
        assertTrue(StringUtils.isBlank(processedResync.getErrorMsg()));
        assertEquals(ResyncDetail.Mode.RESYNC, processedResync.getMode());

        upStream = streamService.findStream(approved.getId());
        assertTrue(upStream.isPresent());
        assertEquals(SyncStream.Status.READY, upStream.get().getStatus());

        SyncDetail sourceSyncDetail = syncRepo.findWatermark(sfdcEntity.getId(), syncariEntity.getApiName(), SyncDirection.INBOUND).get();
        assertEquals(0l, sourceSyncDetail.getWatermark().getStart());
        assertEquals(0l, sourceSyncDetail.getWatermark().getEnd());
        assertTrue(sourceSyncDetail.getWatermark().isResync());
    }

    @Test
    public void resyncInProgressForSyncariEntity(){

        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));

        assertFalse(resyncService.findResyncDetailBySyncariEntityIdAndStatus(syncariEntity.getId(), ResyncStatus.PROCESSING).isPresent());

        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(Map.of(sfdcEntity.getId(), ResyncStatus.NEW))
                .setSyncariEntityId(syncariEntity.getId())
                .setSyncariEntityName(syncariEntity.getApiName())
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.NEW);
        resync = resyncDetailRepo.save(resync);

        // Resync with status new is also not returned
        assertFalse(resyncService.findResyncDetailBySyncariEntityIdAndStatus(syncariEntity.getId(), ResyncStatus.PROCESSING).isPresent());

        resync.setStatus(ResyncStatus.PROCESSING);
        resync = resyncDetailRepo.save(resync);

        assertTrue(resyncService.findResyncDetailBySyncariEntityIdAndStatus(syncariEntity.getId(), ResyncStatus.PROCESSING).isPresent());
    }

    @Test
    public void resyncRequest_PendingUnprocessed(){

        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
        mappingGraph.setDraftStatus(DraftStatus.APPROVED);
        mappingGraph = mappingGraphRepo.save(mappingGraph);
        SyncStream stream1 = streamRepo.save(new SyncStream()
                .setGraphId(mappingGraph.getId())
                .setStatus(SyncStream.Status.RUNNING)
                .setCheckin(Instant.now()));

        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(Map.of(sfdcEntity.getId(), ResyncStatus.NEW))
                .setSyncariEntityId(syncariEntity.getId())
                .setSyncariEntityName(syncariEntity.getApiName())
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.NEW);
        resync = resyncDetailRepo.save(resync);

        try {
            resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
            fail();
        } catch (RuntimeException e){
            assertEquals("There is an existing pending Resync request for entity Account.", e.getMessage());
        }

        resync.setStatus(ResyncStatus.PROCESSING);
        resync = resyncDetailRepo.save(resync);
        try {
            resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
            fail();
        } catch (RuntimeException e){
            assertEquals("There is an existing pending Resync request for entity Account.", e.getMessage());
        }

        resync.setStatus(ResyncStatus.SUCCESS);
        resync = resyncDetailRepo.save(resync);
        ResyncDetail newResync = resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
        assertTrue(resyncService.getResyncDetail(newResync.getId()) != null);
        assertEquals(1, newResync.getEntitiesToResync().size());
        assertTrue(newResync.getEntitiesToResync().containsKey(sfdcEntity.getId()));
        assertEquals(syncariEntity.getId(),newResync.getSyncariEntityId());
        assertEquals(0l,newResync.getStartTime().toEpochMilli());
        assertEquals(1l,newResync.getEndTime().toEpochMilli());
        assertEquals(ResyncStatus.NEW, newResync.getStatus());
        assertEquals(ResyncStatus.NEW, newResync.getEntitiesToResync().get(sfdcEntity.getId()));

    }

    @Test
    public void success(){
        EntityDefinition entity1 = new EntityDefinition("entity1", "entity1").setConnectorId(sfdcConnector.getId());
        entity1.setId("entity1");
        EntityDefinition entity2 = new EntityDefinition("entity2", "entity2").setConnectorId(sfdcConnector.getId());
        entity2.setId("entity2");
        SchemaService mockSchemaService = mock(SchemaService.class);
        when(mockSchemaService.getEntity("entity1")).thenReturn(entity1);
        when(mockSchemaService.getEntity("entity2")).thenReturn(entity2);
        when(mockSchemaService.getEntity(syncariEntity.getId())).thenReturn(syncariEntity);
        when(mockSchemaService.getEntityByName(syncariEntity.getConnectorId(), syncariEntity.getApiName()))
                .thenReturn(Optional.of(syncariEntity));
        var originalSchemaService = resyncService.schemaService;
        resyncService.schemaService = mockSchemaService;

        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(Map.of("entity1", ResyncStatus.PROCESSING, "entity2", ResyncStatus.PROCESSING))
                .setSyncariEntityId(syncariEntity.getId())
                .setSyncariEntityName(syncariEntity.getApiName())
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.PROCESSING);

        // SYNCING will changed to SUCCESS for the entityId provided
        resync = resyncDetailRepo.save(resync);

        Optional<ResyncDetail> details = resyncService.findLatestResyncDetailForEntity(syncariEntity.getId());
        assertTrue(details.isPresent());
        assertEquals(2, details.get().getEntitiesToResync().size());

        resyncService.success(syncariEntity.getApiName(), "entity1");
        Optional<ResyncDetail> retrieved = resyncService.findProcessingResync(syncariEntity.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(ResyncStatus.PROCESSING, retrieved.get().getStatus());
        assertEquals(ResyncStatus.SUCCESS, retrieved.get().getEntitiesToResync().get("entity1"));
        assertEquals(ResyncStatus.PROCESSING, retrieved.get().getEntitiesToResync().get("entity2"));

        // completion notification wont be sent as all entities didn't finish resyncing notification
        assertTrue(notificationRepo.findAll().isEmpty());

        // set success on all syncing entities and resync status will also be changed to success
        resyncService.success(syncariEntity.getApiName(), "entity2");
        retrieved = resyncService.findProcessingResync(syncariEntity.getId());
        assertTrue(retrieved.isEmpty());
        retrieved = resyncService.findResyncDetailBySyncariEntityIdAndStatus(syncariEntity.getId(), ResyncStatus.SUCCESS);
        assertTrue(retrieved.isPresent());
        assertEquals(ResyncStatus.SUCCESS, retrieved.get().getStatus());
        assertEquals(ResyncStatus.SUCCESS, retrieved.get().getEntitiesToResync().get("entity1"));
        assertEquals(ResyncStatus.SUCCESS, retrieved.get().getEntitiesToResync().get("entity2"));
        // success notification should be sent as all entities finished resync
//        assertEquals(1, notificationRepo.findAll().size());
//        Notification notif = notificationRepo.findAll().get(0);
//        assertEquals(NotificationType.INFO, notif.getType());
//        assertEquals("Historical sync Complete for Entity Account", notif.getSubject());


        ResyncDetail resyncNew = new ResyncDetail()
                .setEntitiesToResync(Map.of("entity1", ResyncStatus.SUCCESS))
                .setSyncariEntityId(syncariEntity.getId())
                .setSyncariEntityName(syncariEntity.getApiName())
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.SUCCESS);

        // New Resync
        resyncNew = resyncDetailRepo.save(resyncNew);

        Optional<ResyncDetail> detailsWithentity1 = resyncService.findLatestResyncDetailForEntityOfExistingMappings(syncariEntity.getId(), List.of("entity1"));
        assertTrue(detailsWithentity1.isPresent());
        assertEquals(1, detailsWithentity1.get().getEntitiesToResync().size());

        // restore mockService
        resyncService.schemaService = originalSchemaService;
    }

    @Test
    public void cancelInProgressResyncNotYetStarted() {
        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
        mappingGraph.setDraftStatus(DraftStatus.APPROVED);
        mappingGraph = mappingGraphRepo.save(mappingGraph);
        SyncStream stream1 = streamRepo.save(new SyncStream()
                .setGraphId(mappingGraph.getId())
                .setStatus(SyncStream.Status.RUNNING)
                .setCheckin(Instant.now()));

        Watermark wm = new Watermark(0, 0,true,0).setDirection(SyncDirection.INBOUND);
        syncRepo.save(new SyncDetail(sfdcEntity.getId(), syncariEntity.getApiName(), wm));
                
        ResyncDetail resync = resyncService.createResyncRequest(syncariEntity.getId(), 
            List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
        
        resyncService.cancelInProgress(syncariEntity);
        
        ResyncDetail cancelled = resyncService.getResyncDetail(resync.getId());
        assertNotNull(cancelled);
        assertEquals(ResyncStatus.CANCEL_REQUESTED, cancelled.getStatus());

        Optional<SyncStream> upStream = streamService.findStream(mappingGraph.getId());
        assertTrue(upStream.isPresent());
        assertEquals(SyncStream.Status.RUNNING, upStream.get().getStatus());

        SyncDetail sourceSyncDetail = syncRepo.findWatermark(sfdcEntity.getId(), syncariEntity.getApiName(), SyncDirection.INBOUND).get();
        assertEquals(0l, sourceSyncDetail.getWatermark().getStart());
        assertEquals(0l, sourceSyncDetail.getWatermark().getEnd());
        assertFalse(sourceSyncDetail.getWatermark().isResync());
    }

    @Test
    public void cancelInProgressResyncInProgress() {
        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
        mappingGraph.setDraftStatus(DraftStatus.APPROVED);
        mappingGraph = mappingGraphRepo.save(mappingGraph);
        SyncStream stream1 = streamRepo.save(new SyncStream()
                .setGraphId(mappingGraph.getId())
                .setStatus(SyncStream.Status.RUNNING)
                .setCheckin(Instant.now()));

        Watermark wm = new Watermark(0, 0,true,0).setDirection(SyncDirection.INBOUND);
        syncRepo.save(new SyncDetail(sfdcEntity.getId(), syncariEntity.getApiName(), wm));

        Optional<SyncStream> upStream = streamService.findStream(mappingGraph.getId());
        assertTrue(upStream.isPresent());
        SyncStream.Status originalSyncStatus = upStream.get().getStatus();

        ResyncDetail resync = resyncService.createResyncRequest(syncariEntity.getId(), 
            List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));

        resyncService.processNewResync(syncariEntity.getId());
        ResyncDetail processedResync = resyncService.getResyncDetail(resync.getId());
        assertEquals(ResyncStatus.PROCESSING, processedResync.getStatus());
        
        // Cancel the processing resync
        resyncService.cancelInProgress(syncariEntity);
        
        ResyncDetail cancelled = resyncService.getResyncDetail(resync.getId());
        assertNotNull(cancelled);
        assertEquals(ResyncStatus.CANCEL_REQUESTED, cancelled.getStatus());
        resyncService.cancel(syncariEntity);

        upStream = streamService.findStream(mappingGraph.getId());
        assertTrue(upStream.isPresent());
        assertEquals(originalSyncStatus, upStream.get().getStatus());

        // After cancellation, the original watermarks should be restored.
        SyncDetail sourceSyncDetail = syncRepo.findWatermark(sfdcEntity.getId(), syncariEntity.getApiName(), SyncDirection.INBOUND).get();
        assertEquals(0l, sourceSyncDetail.getWatermark().getStart());
        assertEquals(0l, sourceSyncDetail.getWatermark().getEnd());
        assertFalse(sourceSyncDetail.getWatermark().isResync());
    }

    @Test
    public void cancelCancelledResyncNoOp() {
        MappingGraph mappingGraph = mappingGraphRepo
                .save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
        mappingGraph.setDraftStatus(DraftStatus.APPROVED);
        mappingGraph = mappingGraphRepo.save(mappingGraph);
        SyncStream stream1 = streamRepo.save(new SyncStream()
                .setGraphId(mappingGraph.getId())
                .setStatus(SyncStream.Status.RUNNING)
                .setCheckin(Instant.now()));

        ResyncDetail resync = resyncService.createResyncRequest(syncariEntity.getId(), 
            List.of(sfdcEntity.getId()), Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));
        
        resyncService.cancelInProgress(syncariEntity);
        
        ResyncDetail cancelled = resyncService.getResyncDetail(resync.getId());
        assertNotNull(cancelled);
        assertEquals(ResyncStatus.CANCEL_REQUESTED, cancelled.getStatus());

        resyncService.cancelInProgress(syncariEntity);

        cancelled = resyncService.getResyncDetail(resync.getId());
        assertNotNull(cancelled);
        assertEquals(ResyncStatus.CANCEL_REQUESTED, cancelled.getStatus());
    }

    @Test
    public void updateResyncSources(){
        EntityDefinition entity1 = entityProxyRepo.save(new EntityDefinition("entity1", "Entity1").setConnectorId(sfdcConnector.getId()));
        EntityDefinition entity2 = entityProxyRepo.save(new EntityDefinition("entity2", "Entity2").setConnectorId(sfdcConnector.getId()));
        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(new HashMap<>(Map.of(entity1.getId(), ResyncStatus.PROCESSING, entity2.getId(), ResyncStatus.SUCCESS)))
                .setSyncariEntityId(syncariEntity.getId())
                .setSyncariEntityName(syncariEntity.getApiName())
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.PROCESSING);
        resync = resyncDetailRepo.save(resync);

        var updatedResync = resyncService.updateResyncSources(resync, List.of(entity2.getId()));

        // entities not provided in sources list of updateResyncSources are cancelled
        assertTrue(updatedResync.getEntitiesToResync().containsKey(entity1.getId()));
        assertEquals(ResyncStatus.CANCELLED, updatedResync.getEntitiesToResync().get(entity1.getId()));
        // entities passed in source list will remain as is
        assertTrue(updatedResync.getEntitiesToResync().containsKey(entity2.getId()));
        assertEquals(ResyncStatus.SUCCESS, updatedResync.getEntitiesToResync().get(entity2.getId()));
        // since cancelling the source sync made resync complete, it will be changed to success and notification will be sent
        assertEquals(ResyncStatus.SUCCESS, updatedResync.getStatus());
//        assertEquals(1, notificationRepo.findAll().size());
//        Notification notif = notificationRepo.findAll().get(0);
//        assertEquals(NotificationType.INFO, notif.getType());
//        assertEquals("Historical sync Complete for Entity Account", notif.getSubject());
    }

    @Test
    public void processNewPartialResync(){
        MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
        MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
        assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());
        var approved = mappingGraphService.approveDraft(defaultEntityGraph);
        assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());

        Watermark wm = new Watermark(0, 0,true,0).setDirection(SyncDirection.INBOUND);
        syncRepo.save(new SyncDetail(sfdcEntity.getId(), syncariEntity.getApiName(), wm));

        Optional<SyncStream> upStream = streamService.findStream(approved.getId());
        assertTrue(upStream.isPresent());
        SyncStream.Status originalSyncStatus = upStream.get().getStatus();

        ResyncDetail resync = resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()),
                Instant.ofEpochMilli(0), Instant.ofEpochMilli(1));

        assertEquals(ResyncStatus.NEW, resync.getStatus());

        resyncService.processNewResync(syncariEntity.getId());
        SyncDetail sourceSyncDetail = syncRepo.findWatermark(sfdcEntity.getId(), syncariEntity.getApiName(), SyncDirection.INBOUND).get();
        assertTrue(sourceSyncDetail.getWatermark().isResync());
        assertFalse(sourceSyncDetail.getWatermark().isPartialResync());

        resyncService.cancel(syncariEntity);

        resync = resyncService.createResyncRequest(syncariEntity.getId(), List.of(sfdcEntity.getId()),
                Instant.ofEpochMilli(1681219596000L), Instant.now());

        assertEquals(ResyncStatus.NEW, resync.getStatus());

        resyncService.processNewResync(syncariEntity.getId());
        sourceSyncDetail = syncRepo.findWatermark(sfdcEntity.getId(), syncariEntity.getApiName(), SyncDirection.INBOUND).get();
        assertTrue(sourceSyncDetail.getWatermark().isResync());
        assertTrue(sourceSyncDetail.getWatermark().isPartialResync());
    }

    @Test
    public void validateProcessingResyncSources_AllSourcesActive() {
        // Positive test: Create resync with PROCESSING status and active sources
        sfdcConnector.setStatus(ConnectorStatus.ACTIVE);
        sfdcEntity.setStatus(Status.ACTIVE);

        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(Map.of(sfdcEntity.getId(), ResyncStatus.PROCESSING))
                .setSyncariEntityId(syncariEntity.getId())
                .setSyncariEntityName(syncariEntity.getApiName())
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.PROCESSING);

        // Should not send notification when all sources are active
        resyncService.validateProcessingResyncSources(syncariEntity.getId(), resync);

        verify(errorNotificationService, never()).sendErrorNotification(any(), any(), any(), any(), any());
    }

    @Test
    public void validateProcessingResyncSources_InactiveEntity() {
        // Negative test: Set the source entity as inactive and persist it
        sfdcEntity.setStatus(Status.INACTIVE);
        entityProxyRepo.save(sfdcEntity);

        // Create resync with PROCESSING status
        ResyncDetail resync = new ResyncDetail()
                .setEntitiesToResync(Map.of(sfdcEntity.getId(), ResyncStatus.PROCESSING))
                .setSyncariEntityId(syncariEntity.getId())
                .setSyncariEntityName(syncariEntity.getApiName())
                .setStartTime(Instant.ofEpochMilli(0))
                .setEndTime(Instant.ofEpochMilli(1))
                .setStatus(ResyncStatus.PROCESSING);

        // Should send notification for inactive entity
        resyncService.validateProcessingResyncSources(syncariEntity.getId(), resync);

        verify(errorNotificationService, times(1)).sendErrorNotification(
                eq(ErrorCategory.PIPELINE),
                eq(ErrorPriority.P1),
                eq(syncariEntity.getId()),
                any(String.class),
                any(String.class)
        );

        // Reactivate entity for other tests
        sfdcEntity.setStatus(Status.ACTIVE);
        entityProxyRepo.save(sfdcEntity);
    }

    private Connector createConnector() {
        EntitySchema entitySchema = new EntitySchema("Account", "Account");
        entitySchema.addField(new AttributeSchema("Name","string").setDisplayName("Name"));
        entitySchema.addField(new AttributeSchema("Id","id").setDisplayName("Id"));
        when(salesforceService.describeAll(any())).thenReturn(List.of(entitySchema));
        when(salesforceService.getName()).thenReturn("salesforce");
        var connector = new Connector("sfdc1", connectorService.describe("salesforce"),
                config.getSalesforceUrl(), config.getUser(), config.getPassword());
        connector.getAuthConfig().setToken(config.getToken());
        connector = connectorService.save(connector);
        connectorService.authenticated(connector.getId());
        connectorService.activate(connector.getId());
        verify(salesforceService).describeAll(any());
        return connector;
    }
}
