package com.syncari.core.sync;

import com.google.common.collect.Lists;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.EntityDataBatchIterator;
import com.syncari.connector.data.iterator.Offset;
import com.syncari.connector.data.iterator.Offset.OffsetType;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataSourceRequest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.IntegrationTest;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.exceptions.PipelineException;
import com.syncari.core.model.EventData;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ConnectorStatus;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.service.*;
import com.syncari.core.utils.GraphHelper;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Category(IntegrationTest.class)
public class EntitySourceTest extends AbstractSyncariTest {
	private Connector connector;
	private Connector syncariConnector;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	SyncDetailRepo syncRepo;
	@Autowired
	EndSystemConfig config;
	@Autowired
	AttributeRepo attributeProxyRepo;
	@Autowired
	ConnectorRepo connectorRepo;
	@Autowired
	EntityDefinitionRepo entityProxyRepo;
	@Autowired
	StagedBatchRepo stagedBatchRepo;
	@Autowired
	StagedBatchRecordRepo stagedBatchRecordRepo;
	@Autowired
	StagedExternalRecordRepo stagedExternalRecordRepo;
	@Autowired
	EntitySource entitySource;
	@MockBean(name="eventStore")
	EventStore eventStore;
	@Autowired
	SchemaService schemaService;
	@Autowired
	NotificationRepo notificationRepo;
	@Autowired
	WatermarkService syncService;
	@Autowired
	DataServiceFactory factory;
	@Autowired
	ResyncService resyncService;
	@Autowired
	ResyncDetailRepo resyncDetailRepo;
	@Autowired
	JobDetailRepo jobDetailRepo;
	@Autowired
	RequeueRequestRepo requeueRequestRepo;
	@Autowired
	EventDataRepo eventDataRepo;
	@Autowired
	EventDataService eventDataService;
	@Autowired
	MappingGraphService graphService;

	@After
	public void tearDown() {
		resetRepos(syncRepo, stagedBatchRepo,
				stagedBatchRecordRepo, notificationRepo,jobDetailRepo,requeueRequestRepo);
		entitySource.factory = factory;
		entitySource.syncService = syncService;
		entitySource.resyncService = resyncService;
		entitySource.connectorService = connectorService;
		entitySource.schemaService = schemaService;
	}

	@Before
	public void setUp() {
		super.setUp();
		connectorService.publisher = publisher;
		if(connector == null) {
			connector = new Connector("zendesk3", connectorService.describe("zendesk").getId(), "https://d3v-syncari.zendesk.com");
			connector.setAuthConfig(getAuthCOnfig());
			connector = connectorService.save(connector);
			connectorService.authenticated(connector.getId());
			connectorService.activate(connector.getId());
		}

		syncariConnector = connectorService.getSyncariConnector();
	}

	@Test
	public void fetchSourceInitialSync() {
		assertEquals(0, syncRepo.count());
		assertEquals(0, stagedBatchRepo.count());
		assertEquals(0, stagedBatchRecordRepo.count());
		assertEquals(0, stagedExternalRecordRepo.count());

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		List<EntityDefinition> sources = schemaService.getEntities(connector.getId()).stream()
				.filter(e->e.getDisplayName().equals("Organization"))
				.collect(Collectors.toList());
		// create initial sync watermark
		long now = System.currentTimeMillis();
		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setInitial(true);
			syncRepo.save(new SyncDetail(e.getId(), syncariAccEntity.getApiName(), w));
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		MappingGraphService mockGraphService = mock(MappingGraphService.class);
		entitySource.graphService = mockGraphService;
		when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));

		// create Resync for INITALSYNC mode
		ResyncDetail resync = new ResyncDetail().setStartTime(Instant.EPOCH).setEndTime(Instant.ofEpochMilli(now))
				.setMode(ResyncDetail.Mode.INITIALSYNC).setStatus(ResyncStatus.NEW).setSyncariEntityId(syncariAccEntity.getId())
				.setSyncariEntityName(syncariAccEntity.getApiName())
				.setEntitiesToResync(sources.stream().collect(Collectors.toMap(EntityDefinition::getId, s -> ResyncStatus.NEW)));
		resync = resyncDetailRepo.save(resync);

		CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
                		.setSourceEntities(sources)
                		.setSyncariEntity(new EntityDefinition("account", "account"))
                		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
		assertEquals(1, syncRepo.count());
		assertEquals(1, stagedBatchRepo.count());
		assertTrue(stagedBatchRecordRepo.count() > 0);
		assertTrue(stagedExternalRecordRepo.count() > 0);
		long before = stagedExternalRecordRepo.count();
		currentBatch = entitySource.fetchSource(new DataSourceRequest()
				.setSourceEntities(sources)
				.setSyncariEntity(new EntityDefinition("account", "account"))
				.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
		assertTrue(stagedExternalRecordRepo.count() > 0);
		long after = stagedExternalRecordRepo.count();
		assertEquals(before, after);
	}

	@Test
	public void fetchSourceResyncOnTwoSources() {
		// Create src entities
		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		EntityDefinition srcAccEntity2 = new EntityDefinition("srcAccEntity2", "SourceAccEntity2").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity2 = entityProxyRepo.save(srcAccEntity2);
		srcAccEntity2.setDraftStatus(DraftStatus.APPROVED);
		var sources = List.of(srcAccEntity1, srcAccEntity2);

		// create resync and corresponding watermark
		long now = System.currentTimeMillis();
		ResyncDetail resync = new ResyncDetail().setStartTime(Instant.EPOCH).setEndTime(Instant.ofEpochMilli(now))
				.setMode(ResyncDetail.Mode.RESYNC).setStatus(ResyncStatus.NEW).setSyncariEntityId(syncariAccEntity.getId())
				.setSyncariEntityName(syncariAccEntity.getApiName())
				.setEntitiesToResync(sources.stream().collect(Collectors.toMap(EntityDefinition::getId, s -> ResyncStatus.NEW)));
		resync = resyncDetailRepo.save(resync);
		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(true);
			syncRepo.save(new SyncDetail(e.getId(), syncariAccEntity.getApiName(), w));
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});


		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		var originalGraphService = entitySource.graphService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));

			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			long lastWatermark = now - 5000l; // return lastWatermark returned by iterator as 5 second before now
			EntityData mockEntityData = new EntityData().setConnectorId(connector.getId()).setLastModified(lastWatermark);
			EntityDataBatchIterator mockIterator1 = mock(EntityDataBatchIterator.class);
			EntityDataBatchIterator mockIterator2 = mock(EntityDataBatchIterator.class);
			doReturn(true, true,false).when(mockIterator1).hasNext();
			doReturn(true, false).when(mockIterator2).hasNext();
			doReturn(lastWatermark).when(mockIterator1).getLastWatermark();
			doReturn(lastWatermark).when(mockIterator2).getLastWatermark();
			doReturn(new Stats()).when(mockIterator1).getStats();
			doReturn(new Stats()).when(mockIterator2).getStats();

			doReturn(List.of(mockEntityData)).when(mockIterator1).next();
			doReturn(List.of(mockEntityData)).when(mockIterator2).next();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator1);
			FetchResponse mockResponse2 = new FetchResponse(new WatermarkInfo(), mockIterator2);
			doReturn(mockResponse1, mockResponse2).when(mockDataService).getByWatermark(any());

			CurrentBatch currentBatch = 
					entitySource.fetchSource(new DataSourceRequest()
	                		.setSourceEntities(sources)
	                		.setSyncariEntity(syncariAccEntity)
	                		.setWatermark(new Watermark())
							.setGraph(graph)
							.setSyncStartTime(Instant.now().toEpochMilli()));
			assertEquals(2, syncRepo.count()); // one record each per source entity
			assertEquals(2, stagedBatchRepo.count()); // one record each per source entity
			assertEquals(2, stagedBatchRecordRepo.count()); // one record each per source entity

			verify(mockDataService, times(2)).getByWatermark(any());
			var syncDetails = syncRepo.findUpstreamWatermarks("account", List.of(srcAccEntity1.getId(), srcAccEntity2.getId()));
			syncDetails.forEach( sd -> {
				assertTrue(sd.getWatermark().isResync());
			});
			resync = resyncDetailRepo.findById(resync.getId()).get();
			assertEquals(ResyncStatus.PROCESSING, resync.getStatus());
			resync.getEntitiesToResync().forEach( (k, v) -> {
				assertEquals(ResyncStatus.PROCESSING, v);
			});

			// read offset in stagedBatchRecord has reached now
			currentBatch.getEntityBatches().forEach((k, v) -> {
				assertTrue(v.getWatermark().isResync());
				assertEquals(now, v.getWatermark().getStart());
				assertEquals(now, v.getWatermark().getEnd());
			});

			// test closeSource
			ResyncService mockResyncService = mock(ResyncService.class);
			doReturn(Optional.of(resync)).when(mockResyncService).findProcessingOrCancelRequestedResync(syncariAccEntity.getId());
			doReturn(true).when(mockResyncService).isComplete(any(), any());
			doNothing().when(mockResyncService).success(any(), any());
			entitySource.resyncService = mockResyncService;
			GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
			entitySource.closeSource(graphContext);
			verify(mockResyncService, times(1)).findProcessingOrCancelRequestedResync(any());
			verify(mockResyncService, times(2)).success(any(), any());
			verify(mockResyncService, times(2)).isComplete(any(), any());

		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}

	@Test
	public void fetchSourceResyncOnTwoSourcesEmptyBatches() {
		// Create src entities
		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		EntityDefinition srcAccEntity2 = new EntityDefinition("srcAccEntity2", "SourceAccEntity2").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity2 = entityProxyRepo.save(srcAccEntity2);
		srcAccEntity2.setDraftStatus(DraftStatus.APPROVED);
		var sources = List.of(srcAccEntity1, srcAccEntity2);

		// create resync and corresponding watermark
		long now = System.currentTimeMillis();
		ResyncDetail resync = new ResyncDetail().setStartTime(Instant.EPOCH).setEndTime(Instant.ofEpochMilli(now))
				.setMode(ResyncDetail.Mode.RESYNC).setStatus(ResyncStatus.NEW).setSyncariEntityId(syncariAccEntity.getId())
				.setSyncariEntityName(syncariAccEntity.getApiName())
				.setEntitiesToResync(sources.stream().collect(Collectors.toMap(EntityDefinition::getId, s -> ResyncStatus.NEW)));
		resync = resyncDetailRepo.save(resync);
		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(true);
			syncRepo.save(new SyncDetail(e.getId(), syncariAccEntity.getApiName(), w));
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		var originalGraphService = entitySource.graphService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));

			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			EntityDataBatchIterator mockIterator1 = mock(EntityDataBatchIterator.class);
			EntityDataBatchIterator mockIterator2 = mock(EntityDataBatchIterator.class);
			doReturn(false).when(mockIterator1).hasNext();
			doReturn(false).when(mockIterator2).hasNext();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator1);
			FetchResponse mockResponse2 = new FetchResponse(new WatermarkInfo(), mockIterator2);
			doReturn(mockResponse1, mockResponse2).when(mockDataService).getByWatermark(any());

			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(syncariAccEntity)
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			// For empty batches we would still update the watermark. Assert that we call to update the watermarks.
			assertEquals(2, syncRepo.count()); 
			assertEquals(0, stagedBatchRepo.count());
			assertEquals(0, stagedBatchRecordRepo.count()); 

			verify(mockDataService, times(2)).getByWatermark(any());
			var syncDetails = syncRepo.findUpstreamWatermarks("account", List.of(srcAccEntity1.getId(), srcAccEntity2.getId()));
			syncDetails.forEach( sd -> {
				assertTrue(sd.getWatermark().isResync());
			});
			resync = resyncDetailRepo.findById(resync.getId()).get();
			assertEquals(ResyncStatus.PROCESSING, resync.getStatus());
			resync.getEntitiesToResync().forEach( (k, v) -> {
				assertEquals(ResyncStatus.PROCESSING, v);
			});

			assertTrue(currentBatch.getEntityBatches().isEmpty());

			// test closeSource still marks the resync as success and unblocks the upstream
			ResyncService mockResyncService = mock(ResyncService.class);
			doReturn(Optional.of(resync)).when(mockResyncService).findProcessingOrCancelRequestedResync(syncariAccEntity.getId());
			doReturn(true).when(mockResyncService).isComplete(any(), any());
			doNothing().when(mockResyncService).success(any(), any());
			entitySource.resyncService = mockResyncService;
			GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
			entitySource.closeSource(graphContext);
			verify(mockResyncService, times(1)).findProcessingOrCancelRequestedResync(any());
			verify(mockResyncService, times(2)).success(any(), any());
			verify(mockResyncService, times(2)).isComplete(any(), any());

		} finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}

	@Test
	public void fetchSourceWithCanQueryFalseSkipsAllSources() {
		// Create src entities
		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		EntityDefinition srcAccEntity2 = new EntityDefinition("srcAccEntity2", "SourceAccEntity2").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity2 = entityProxyRepo.save(srcAccEntity2);
		srcAccEntity2.setDraftStatus(DraftStatus.APPROVED);
		var sources = List.of(srcAccEntity1, srcAccEntity2);

		// create resync and corresponding watermark
		long now = System.currentTimeMillis();
		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			Watermark w = new Watermark().setStart(Instant.now().minusSeconds(10).toEpochMilli()).setEnd(Instant.now().minusSeconds(10).toEpochMilli()).setResync(true);
			syncRepo.save(new SyncDetail(e.getId(), syncariAccEntity.getApiName(), w));
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		try {
			// Mock getByWatermark
			DataService mockDataService1 = mock(DataService.class);
			DataService mockDataService2 = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);

			// this is skew time tolerance for salesfore which is the connector here
			doReturn(5 * 60).when(mockDataService1).clockSkewTolerance(any());
			doReturn(5 * 60).when(mockDataService2).clockSkewTolerance(any());
			doReturn(mockDataService1).when(mockFactory).getDataService(any());
			doReturn(mockDataService2).when(mockFactory).getDataService(any());

			entitySource.factory = mockFactory;
			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(new EntityDefinition("account", "account"))
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			assertEquals(2, syncRepo.count()); // one record each per source entity
			assertEquals(0, stagedBatchRepo.count()); // one record each per source entity
			assertEquals(0, stagedBatchRecordRepo.count()); // one record each per source entity
			assertEquals(0, currentBatch.getEntityBatches().size()); // one record each per source entity


			verify(mockDataService1, times(0)).getByWatermark(any());
			verify(mockDataService2, times(0)).getByWatermark(any());
			var syncDetails = syncRepo.findUpstreamWatermarks("account", List.of(srcAccEntity1.getId(), srcAccEntity2.getId()));
			syncDetails.forEach( sd -> {
				assertTrue(sd.getWatermark().isResync());
			});

			// read offset in stagedBatchRecord has reached now

			currentBatch.getEntityBatches().forEach((k, v) -> {
				assertTrue(v.getWatermark().isResync());
				assertEquals(now, v.getWatermark().getStart());
				assertEquals(now, v.getWatermark().getEnd());
			});

			// test closeSource
			ResyncService mockResyncService = mock(ResyncService.class);
			entitySource.resyncService = mockResyncService;
			entitySource.syncService = mock(WatermarkService.class);
			GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
			entitySource.closeSource(graphContext);
			verify(mockResyncService, times(1)).findProcessingOrCancelRequestedResync(any());
			verify(mockResyncService, times(0)).success(any(), any());
			verify(entitySource.syncService, times(0)).updateWatermark(any(),any(),any());

		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
		}
	}

	@Test
	public void fetchSourceResyncOnOneSourceAndOneIncrementalSync() {
		// Create src entities
		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		EntityDefinition srcAccEntity2 = new EntityDefinition("srcAccEntity2", "SourceAccEntity2").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity2 = entityProxyRepo.save(srcAccEntity2);
		srcAccEntity2.setDraftStatus(DraftStatus.APPROVED);
		var sources = List.of(srcAccEntity1, srcAccEntity2);

		// create resync for srcAccEntity1 but keep srcAccEntity2 for incremental sync
		long now = System.currentTimeMillis();
		ResyncDetail resync = new ResyncDetail().setStartTime(Instant.EPOCH).setEndTime(Instant.ofEpochMilli(now))
				.setMode(ResyncDetail.Mode.RESYNC).setStatus(ResyncStatus.NEW).setSyncariEntityId(syncariAccEntity.getId())
				.setSyncariEntityName(syncariAccEntity.getApiName())
				.setEntitiesToResync(Map.of(srcAccEntity1.getId(), ResyncStatus.NEW));
		resync = resyncDetailRepo.save(resync);
		var wmSource1 = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(true);
		syncRepo.save(new SyncDetail(srcAccEntity1.getId(), syncariAccEntity.getApiName(), wmSource1));
		var wmSource2 = new Watermark().setStart(now).setEnd(now);
		syncRepo.save(new SyncDetail(srcAccEntity2.getId(), syncariAccEntity.getApiName(), wmSource2));

		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		var originalGraphService = entitySource.graphService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));

			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			long lastWatermark = now - 5000l; // return lastWatermark returned by iterator as 5 second before now
			EntityData mockEntityData = new EntityData().setConnectorId(connector.getId()).setLastModified(lastWatermark);
			EntityDataBatchIterator mockIterator = mock(EntityDataBatchIterator.class);
			doReturn(true, true, false).when(mockIterator).hasNext();
			doReturn(lastWatermark).when(mockIterator).getLastWatermark();
			doReturn(new Stats()).when(mockIterator).getStats();

			doReturn(List.of(mockEntityData)).when(mockIterator).next();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator);
			doReturn(mockResponse1).when(mockDataService).getByWatermark(any());

			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(syncariAccEntity)
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			assertEquals(2,syncRepo.count()); // one record each per source entity
			assertEquals(1,stagedBatchRepo.count() ); // one record each per processed source entity
			assertEquals(1,stagedBatchRecordRepo.count()); // record for only srcAccEntity1 is created

			// data fetched only for sourceEntities in INITIAL_SYNC/RESYNC, entity with incremental sync will wait for others to catchup
			verify(mockDataService, times(1)).getByWatermark(any());
			var syncDetailSource1 = syncRepo.findWatermark(srcAccEntity1.getId(), "account").get();
			var syncDetailSource2 = syncRepo.findWatermark(srcAccEntity2.getId(), "account").get();
			assertTrue(syncDetailSource1.getWatermark().isResync());
			assertFalse(syncDetailSource2.getWatermark().isResync());
			assertFalse(syncDetailSource2.getWatermark().isInitial());

			resync = resyncDetailRepo.findById(resync.getId()).get();
			assertEquals(ResyncStatus.PROCESSING, resync.getStatus());
			assertEquals(ResyncStatus.PROCESSING, resync.getEntitiesToResync().get(srcAccEntity1.getId()));
			assertNull(resync.getEntitiesToResync().get(srcAccEntity2.getId()));
			assertEquals(ResyncDetail.Mode.RESYNC, resync.getMode());

			// read offset in stagedBatchRecord has reached now for both sources (unchanged for source2)
			currentBatch.getEntityBatches().forEach((k, v) -> {
				assertTrue(v.getWatermark().isResync());
				assertEquals(now, v.getWatermark().getStart());
				assertEquals(now, v.getWatermark().getEnd());
			});

			// test closeSource
			ResyncService mockResyncService = mock(ResyncService.class);
			doReturn(Optional.of(resync)).when(mockResyncService).findProcessingOrCancelRequestedResync(syncariAccEntity.getId());
			doReturn(true).when(mockResyncService).isComplete(any(), any());
			doNothing().when(mockResyncService).success(any(), any());
			entitySource.resyncService = mockResyncService;
			GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
			entitySource.closeSource(graphContext);
			// resyncService.success executed for only srcAccEntity1
			verify(mockResyncService, times(1)).findProcessingOrCancelRequestedResync(any());
			verify(mockResyncService, times(1)).success(any(), any());
			verify(mockResyncService, times(1)).isComplete(any(), any());

		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}

	@Test
	public void fetchSourceDataMoreThanMaxAllowed(){

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		var sources = List.of(srcAccEntity1);

		// create resync and corresponding watermark
		long now = System.currentTimeMillis();
		long lastWatermark = now - 5000l; // return lastWatermark returned by iterator as 5 second before now
		ResyncDetail resync = new ResyncDetail().setStartTime(Instant.EPOCH).setEndTime(Instant.ofEpochMilli(now))
				.setMode(ResyncDetail.Mode.RESYNC).setStatus(ResyncStatus.NEW).setSyncariEntityId(syncariAccEntity.getId())
				.setSyncariEntityName(syncariAccEntity.getApiName())
				.setEntitiesToResync(sources.stream().collect(Collectors.toMap(EntityDefinition::getId, s -> ResyncStatus.NEW)));
		resync = resyncDetailRepo.save(resync);
		Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(true);
		syncRepo.save(new SyncDetail(srcAccEntity1.getId(), syncariAccEntity.getApiName(), w));

		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		var originalGraphService = entitySource.graphService;
		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			entitySource.schemaService = mock(SchemaService.class);

			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;

			doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
			doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
			sources.forEach(e -> {
				doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
			});
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));
			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			List<EntityData> mockEntityDataList = new ArrayList<>();
			for(int i =0; i < 10000; i++){
				mockEntityDataList.add(new EntityData().setConnectorId(connector.getId()).setLastModified(lastWatermark).setId(i+""));
			}
			EntityDataBatchIterator mockIterator1 = mock(EntityDataBatchIterator.class);
			doReturn(true, true,false).when(mockIterator1).hasNext();
			doReturn(lastWatermark).when(mockIterator1).getLastWatermark();
			doReturn(new Stats()).when(mockIterator1).getStats();
            doReturn(new Offset(OffsetType.NONE, 0)).when(mockIterator1).getOffsetInfo();

			doReturn(mockEntityDataList).when(mockIterator1).next();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator1);
			doReturn(mockResponse1).when(mockDataService).getByWatermark(any());

			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(new EntityDefinition("account", "account"))
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			verify(mockDataService, times(1)).getByWatermark(any());
			var syncDetailSource1 = syncRepo.findWatermark(srcAccEntity1.getId(), "account").get();
			assertTrue(syncDetailSource1.getWatermark().isResync());
			resync = resyncDetailRepo.findById(resync.getId()).get();
			assertEquals(ResyncStatus.PROCESSING, resync.getStatus());
			assertEquals(ResyncStatus.PROCESSING, resync.getEntitiesToResync().get(srcAccEntity1.getId()));
			//Skips the last record, but pulls 10k with same watermark, even thiugh max limit is just 2000
			assertEquals(10000,stagedBatchRecordRepo.findByStagedBatchIdIn(List.of(currentBatch.getEntityBatch(srcAccEntity1).getId()), Pageable.unpaged()).getNumberOfElements());
			// read offset in stagedBatchRecord has reached lastWatermark since it has reached max allowed limit
			currentBatch.getEntityBatches().forEach((k, v) -> {
				assertTrue(v.getWatermark().isResync());
				assertEquals(lastWatermark, v.getWatermark().getStart());
				assertEquals(lastWatermark, v.getWatermark().getEnd());
			});

			// test closeSource
			ResyncService mockResyncService = mock(ResyncService.class);
			doReturn(Optional.of(resync)).when(mockResyncService).findProcessingOrCancelRequestedResync(syncariAccEntity.getId());
			doNothing().when(mockResyncService).success(any(), any());
			entitySource.resyncService = mockResyncService;
			GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
			entitySource.closeSource(graphContext);
			verify(mockResyncService).findProcessingOrCancelRequestedResync(any());
			// success never called as watermark has not reached resync end time
			verify(mockResyncService, never()).success(any(), any());

		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}
	@Test
	public void fetchSourceStopsAt2kIncremental(){

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		var sources = List.of(srcAccEntity1);

		long now = System.currentTimeMillis();
		long lastWatermark = now - 5000l; // return lastWatermark returned by iterator as 5 second before now
		//incremental sync
		Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(false).setInitial(false);
		syncRepo.save(new SyncDetail(srcAccEntity1.getId(), syncariAccEntity.getApiName(), w));

		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		var originalGraphService = entitySource.graphService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));

			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			List<EntityData> mockEntityDataList = new ArrayList<>();
			long startWm = lastWatermark - 10000;
			for(int i =0; i < 10000; i++){
				mockEntityDataList.add(new EntityData().setConnectorId(connector.getId()).setLastModified(startWm++).setId(i+""));
			}
			EntityDataBatchIterator mockIterator1 = mock(EntityDataBatchIterator.class);
			long expectedWm = lastWatermark - 7501 - 1 ;
			//4 pages + the first hasNext - 5 trues
			doReturn(true, true,true, true, true, false).when(mockIterator1).hasNext();
			doReturn(expectedWm, expectedWm + 25000, expectedWm + 25000, expectedWm + 7500).when(mockIterator1).getLastWatermark();
			doReturn(new Stats()).when(mockIterator1).getStats();
            doReturn(new Offset(OffsetType.NONE, 0)).when(mockIterator1).getOffsetInfo();
            
			List<List<EntityData>> partitions = Lists.partition(mockEntityDataList, 2500);
			doReturn(partitions.get(0),partitions.get(1),partitions.get(2),partitions.get(3)).when(mockIterator1).next();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator1);
			doReturn(mockResponse1).when(mockDataService).getByWatermark(any());
			//First page returns 2500 records. expectedWm is the end of first page,instead of last watermark

			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(syncariAccEntity)
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			verify(mockDataService, times(1)).getByWatermark(any());
			//Pulls only 2.5k records, even though there are 10k records available
			assertEquals(2499,stagedBatchRecordRepo.findByStagedBatchIdIn(List.of(currentBatch.getEntityBatch(srcAccEntity1).getId()), Pageable.unpaged()).getNumberOfElements());
			// read offset in stagedBatchRecord has reached lastWatermark since it has reached max allowed limit
			currentBatch.getEntityBatches().forEach((k, v) -> {
				//watermarks are set to whatever the iterator is returning, because the iterators are not exhausted
				assertEquals(expectedWm, v.getWatermark().getStart());
				assertEquals(expectedWm, v.getWatermark().getEnd());
			});

			// test closeSource
			ResyncService mockResyncService = mock(ResyncService.class);
			doReturn(Optional.empty()).when(mockResyncService).findProcessingOrCancelRequestedResync(syncariAccEntity.getId());
			entitySource.resyncService = mockResyncService;
			GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
			entitySource.closeSource(graphContext);
			verify(mockResyncService).findProcessingOrCancelRequestedResync(any());
			// success never called as watermark has not reached resync end time
			verify(mockResyncService, never()).success(any(), any());

		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}
	@Test
	public void fetchSourcePullRequeuedSourceRecords(){

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		var sources = List.of(srcAccEntity1);

		long now = System.currentTimeMillis();
		long lastWatermark = now - 5000l; // return lastWatermark returned by iterator as 5 second before now
		//incremental sync
		Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(false).setInitial(false);
		syncRepo.save(new SyncDetail(srcAccEntity1.getId(), syncariAccEntity.getApiName(), w));

		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		var originalGraphService = entitySource.graphService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));


			String srcAccountId = srcAccEntity1.getId();
			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			long startWm = lastWatermark - 500;
			List<EntityData> mockEntityDataList = new ArrayList<>();
			for(int i =0; i < 500; i++){
				mockEntityDataList.add(new EntityData().setConnectorId(connector.getId()).setLastModified(startWm++).setId(i+""));
			}
			List<EntityData> mockByIds = new ArrayList<>();
			for(int i =0; i < 32; i++){
				mockByIds.add(new EntityData().setConnectorId(connector.getId()).setLastModified(startWm++).setId("externalRequeuedRecord"+i));
			}

			EntityDataBatchIterator mockIterator1 = mock(EntityDataBatchIterator.class);
			long expectedWm = lastWatermark - 500 ;
			//1 page + the first hasNext - 2 trues
			doReturn(true, true, false).when(mockIterator1).hasNext();
			doReturn(expectedWm, expectedWm + 500).when(mockIterator1).getLastWatermark();
			doReturn(new Stats()).when(mockIterator1).getStats();
			doReturn(mockEntityDataList).when(mockIterator1).next();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator1);
			doReturn(mockResponse1).when(mockDataService).getByWatermark(any());
			doReturn(mockByIds).when(mockDataService).getByIds(any());

			List<RequeueRequest> requeuedRecords = new ArrayList<>();
			for(int i=0;i<32;i++){
				requeuedRecords.add(
				new RequeueRequest()
						.setRecordType(RequeueRequest.RecordType.SOURCE)
						.setEntityDefinitionId(srcAccountId)
						.setGraphId(graph.getId())
						.setRecordId("externalRequeuedRecord"+i)
						.setRetryTimeLimit(ZonedDateTime.now().plusDays(1))
				);
			}
			requeueRequestRepo.saveAll(requeuedRecords);

			//expired records should not show up
			requeueRequestRepo.saveAll(
					IntStream.range(32, 44).mapToObj(i ->
							new RequeueRequest()
									.setRecordType(RequeueRequest.RecordType.SOURCE)
									.setEntityDefinitionId(srcAccountId)
									.setGraphId(graph.getId())
									.setRecordId("externalRequeuedRecord" + i)
									.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)))
							.collect(Collectors.toList()));
			assertEquals(44, requeueRequestRepo.count());
			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(syncariAccEntity)
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			verify(mockDataService, times(1)).getByWatermark(any());
			//Pulls 500 records from source 1 and 32 requeued records
			assertEquals(532,stagedBatchRecordRepo.findByStagedBatchIdIn(List.of(currentBatch.getEntityBatch(srcAccEntity1).getId()), Pageable.unpaged()).getNumberOfElements());
			//expired records are cleaned up
			assertEquals(32, requeueRequestRepo.count());
		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}

	@Test
	public void fetchSourcePullsMax1kRequeuedSourceRecords(){

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		var sources = List.of(srcAccEntity1);

		long now = System.currentTimeMillis();
		long lastWatermark = now - 5000l; // return lastWatermark returned by iterator as 5 second before now
		//incremental sync
		Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(false).setInitial(false);
		syncRepo.save(new SyncDetail(srcAccEntity1.getId(), syncariAccEntity.getApiName(), w));

		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		var originalGraphService = entitySource.graphService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));


			String srcAccountId = srcAccEntity1.getId();
			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			long startWm = lastWatermark - 500;
			List<EntityData> mockEntityDataList = new ArrayList<>();
			for(int i =0; i < 500; i++){
				mockEntityDataList.add(new EntityData().setConnectorId(connector.getId()).setLastModified(startWm++).setId(i+""));
			}
			List<EntityData> mockByIds = new ArrayList<>();
			for(int i =0; i < 1000; i++){
				mockByIds.add(new EntityData().setConnectorId(connector.getId()).setLastModified(startWm++).setId("externalRequeuedRecord"+i));
			}

			EntityDataBatchIterator mockIterator1 = mock(EntityDataBatchIterator.class);
			long expectedWm = lastWatermark - 500 ;
			//1 page + the first hasNext - 2 trues
			doReturn(true, true, false).when(mockIterator1).hasNext();
			doReturn(expectedWm, expectedWm + 500).when(mockIterator1).getLastWatermark();
			doReturn(new Stats()).when(mockIterator1).getStats();
			doReturn(mockEntityDataList).when(mockIterator1).next();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator1);
			doReturn(mockResponse1).when(mockDataService).getByWatermark(any());
			doReturn(mockByIds).when(mockDataService).getByIds(any());
			//1200 available requeued records
			requeueRequestRepo.saveAll(
					IntStream.range(0, 1200).mapToObj(i ->
							new RequeueRequest()
								.setRecordType(RequeueRequest.RecordType.SOURCE)
								.setEntityDefinitionId(srcAccountId)
								.setGraphId(graph.getId())
								.setRecordId("externalRequeuedRecord"+i)
								.setRetryTimeLimit(ZonedDateTime.now().plusDays(1)))
							.collect(Collectors.toList()));


			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(syncariAccEntity)
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			verify(mockDataService, times(1)).getByWatermark(any());
			//Pulls 500 records from source 1 and 1000 (out of 1200) requeued records
			assertEquals(1500,stagedBatchRecordRepo.findByStagedBatchIdIn(List.of(currentBatch.getEntityBatch(srcAccEntity1).getId()), Pageable.unpaged()).getNumberOfElements());
			assertEquals(1200, requeueRequestRepo.count());
		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}



	@Test
	public void exhaustedIteratorInIncrementalSycnSetsLatestWM(){
		Connector syncariConnector = connectorService.getSyncariConnector();
		entitySource.connectorService = mock(ConnectorService.class);
		Connector dummyConnector = new Connector();
		dummyConnector.setName("dummyconnectorName");
		dummyConnector.setId("dummyconnector");
		dummyConnector.setStatus(ConnectorStatus.ACTIVE);
		ConnectorMetadata dummysyanose = new ConnectorMetadata("dummysyanose");
		dummysyanose.setName("dummy");
		dummyConnector.setMetadata(dummysyanose);
		when(entitySource.connectorService.find("dummyconnector")).thenReturn(Optional.of(dummyConnector));
		when(entitySource.connectorService.get("dummyconnector")).thenReturn(dummyConnector);
        when(entitySource.connectorService.refreshAuthentication(any(Connector.class))).thenReturn(dummyConnector);
		when(entitySource.connectorService.getSyncariConnector()).thenReturn(syncariConnector);

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(dummyConnector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		var sources = List.of(srcAccEntity1);

		// create resync and corresponding watermark
		long now = System.currentTimeMillis();
		long lastWatermark = now - 60000l; // return lastWatermark returned by iterator as 60 seconds before now
		//incremental sync
		Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(false).setInitial(false);
		syncRepo.save(new SyncDetail(srcAccEntity1.getId(), syncariAccEntity.getApiName(), w));

		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		var originalGraphService = entitySource.graphService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));


			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			List<EntityData> mockEntityDataList = new ArrayList<>();
			long startWm = lastWatermark - 1000;
			for(int i =0; i < 1000; i++){
				mockEntityDataList.add(new EntityData().setConnectorId(dummyConnector.getId()).setLastModified(startWm++).setId(i+""));
			}
			EntityDataBatchIterator mockIterator1 = mock(EntityDataBatchIterator.class);
			long expectedWm = lastWatermark + 1;
			//1 pages + the first hasNext = 2 trues
			doReturn(true, true, false).when(mockIterator1).hasNext();
			doReturn(expectedWm).when(mockIterator1).getLastWatermark();
			doReturn(new Stats()).when(mockIterator1).getStats();
			doReturn(mockEntityDataList).when(mockIterator1).next();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator1);
			doReturn(mockResponse1).when(mockDataService).getByWatermark(any());
			//First page returns 2500 records. expectedWm is the end of first page,instead of last watermark

			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(syncariAccEntity)
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			verify(mockDataService, times(1)).getByWatermark(any());
			//Pulls only 2.5k records, even though there are 10k records available
			assertEquals(1000,stagedBatchRecordRepo.findByStagedBatchIdIn(List.of(currentBatch.getEntityBatch(srcAccEntity1).getId()), Pageable.unpaged()).getNumberOfElements());
			// read offset in stagedBatchRecord has reached lastWatermark since it has reached max allowed limit
			currentBatch.getEntityBatches().forEach((k, v) -> {
				//watermarks are set to "now" instead of whatever was returned by the iterator, because we exhausted everything
				assertTrue(v.getWatermark().getStart() > lastWatermark);
				assertTrue(v.getWatermark().getEnd() > lastWatermark);
			});

			// test closeSource
			ResyncService mockResyncService = mock(ResyncService.class);
			doReturn(Optional.empty()).when(mockResyncService).findProcessingOrCancelRequestedResync(syncariAccEntity.getId());
			entitySource.resyncService = mockResyncService;
			GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
			entitySource.closeSource(graphContext);
			verify(mockResyncService).findProcessingOrCancelRequestedResync(any());
			// success never called as watermark has not reached resync end time
			verify(mockResyncService, never()).success(any(), any());

		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}
	@Test
	public void fetchSourcePullsEverythingIncrementalWithBatchJobs(){

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		var sources = List.of(srcAccEntity1);

		// create resync and corresponding watermark
		long now = System.currentTimeMillis();
		long lastWatermark = now - 5000l; // return lastWatermark returned by iterator as 5 second before now
		//incremental sync
		Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(false).setInitial(false);
		SyncDetail syncDetail = new SyncDetail(srcAccEntity1.getId(), syncariAccEntity.getApiName(), w);
		//Set the next syncAt to a future time. EntitySOurce should NOT skip sources with pending/complet	ed batch jobs
		syncDetail.setNextSyncAt(Instant.now().plusSeconds(60*60*1).toEpochMilli());
		syncRepo.save(syncDetail);

		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		var originalGraphService = entitySource.graphService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));


			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			List<EntityData> mockEntityDataList = new ArrayList<>();
			long startWm = lastWatermark - 10000;
			for(int i =0; i < 10000; i++){
				mockEntityDataList.add(new EntityData().setConnectorId(connector.getId()).setLastModified(startWm++).setId(i+""));
			}
			EntityDataBatchIterator mockIterator1 = mock(EntityDataBatchIterator.class);
			long expectedWm = lastWatermark - 7500 + 1;
			//4 pages , twice true for each page, and then once false
			doReturn(true, true,true, true, true,true,true,true, false).when(mockIterator1).hasNext();
			doReturn(expectedWm, expectedWm + 25000, expectedWm + 25000, expectedWm + 7500).when(mockIterator1).getLastWatermark();
			doReturn(new Stats()).when(mockIterator1).getStats();
			List<List<EntityData>> partitions = Lists.partition(mockEntityDataList, 2500);
			doReturn(partitions.get(0),partitions.get(1),partitions.get(2),partitions.get(3)).when(mockIterator1).next();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator1);
			//Simulate a response with batch jobs
			mockResponse1.setBatchJobs(List.of(new BatchJob()));
			doReturn(mockResponse1).when(mockDataService).getByWatermark(any());

			//First page returns 2500 records. expectedWm is the end of first page,instead of last watermark

			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(syncariAccEntity)
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			verify(mockDataService, times(1)).getByWatermark(any());
			//Pulls all 10k records, becaause its a batch kob
			assertEquals(10000,stagedBatchRecordRepo.findByStagedBatchIdIn(List.of(currentBatch.getEntityBatch(srcAccEntity1).getId()), Pageable.unpaged()).getNumberOfElements());
			// read offset in stagedBatchRecord has reached lastWatermark since it has reached max allowed limit
			currentBatch.getEntityBatches().forEach((k, v) -> {
				assertEquals(lastWatermark - 1, v.getWatermark().getStart());
				assertEquals(lastWatermark - 1, v.getWatermark().getEnd());
			});

			// test closeSource
			ResyncService mockResyncService = mock(ResyncService.class);
			doReturn(Optional.empty()).when(mockResyncService).findProcessingOrCancelRequestedResync(syncariAccEntity.getId());
			entitySource.resyncService = mockResyncService;
			GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
			entitySource.closeSource(graphContext);
			verify(mockResyncService).findProcessingOrCancelRequestedResync(any());
			// success never called as watermark has not reached resync end time
			verify(mockResyncService, never()).success(any(), any());

		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}
	@Test
	public void batchJobSubmitsStored(){

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition srcAccEntity1 = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		srcAccEntity1.setDraftStatus(DraftStatus.APPROVED);
		srcAccEntity1 = entityProxyRepo.save(srcAccEntity1);
		var sources = List.of(srcAccEntity1);

		// create resync and corresponding watermark
		long now = System.currentTimeMillis();
		long lastWatermark = now - 5000l; // return lastWatermark returned by iterator as 5 second before now
		//incremental sync
		Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setResync(false).setInitial(false);
		SyncDetail syncDetail = new SyncDetail(srcAccEntity1.getId(), syncariAccEntity.getApiName(), w);
		//Set the next syncAt to a future time. EntitySOurce should NOT skip sources with pending/complet	ed batch jobs
		syncDetail.setNextSyncAt(Instant.now().plusSeconds(60*60*1).toEpochMilli());
		syncRepo.save(syncDetail);

		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		var originalFactory = entitySource.factory;
		var originalResyncService = entitySource.resyncService;
		var originalGraphService = entitySource.graphService;
		try {
			// Mock getByWatermark
			DataService mockDataService = mock(DataService.class);
			DataServiceFactory mockFactory = mock(DataServiceFactory.class);
			doReturn(mockDataService).when(mockFactory).getDataService(any());
			entitySource.factory = mockFactory;
			MappingGraphService mockGraphService = mock(MappingGraphService.class);
			entitySource.graphService = mockGraphService;
			when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));


			// mock FetchResponse for both srcAccEntity1 and secAccEntity2
			List<EntityData> mockEntityDataList = new ArrayList<>();
			long startWm = lastWatermark - 10000;
			for(int i =0; i < 10000; i++){
				mockEntityDataList.add(new EntityData().setConnectorId(connector.getId()).setLastModified(startWm++).setId(i+""));
			}
			EntityDataBatchIterator mockIterator1 = mock(EntityDataBatchIterator.class);
			long expectedWm = lastWatermark - 7500 + 1;
			//4 pages , twice true for each page, and then once false
			doReturn(false).when(mockIterator1).hasNext();
			doReturn(0l).when(mockIterator1).getLastWatermark();
			doReturn(new Stats()).when(mockIterator1).getStats();
			FetchResponse mockResponse1 = new FetchResponse(new WatermarkInfo(), mockIterator1);
			//Simulate a response with batch jobs
			BatchJob job1 = new BatchJob().setStatus(BatchJobStatus.PENDING).setJobId("jobId1").setConnectorId("con1").setExternalEntityName("e1");
			mockResponse1.setBatchJobs(List.of(job1));
			doReturn(mockResponse1).when(mockDataService).getByWatermark(any());

			//First page returns 2500 records. expectedWm is the end of first page,instead of last watermark

			CurrentBatch currentBatch = entitySource.fetchSource(new DataSourceRequest()
            		.setSourceEntities(sources)
            		.setSyncariEntity(syncariAccEntity)
            		.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
			verify(mockDataService, times(1)).getByWatermark(any());
			//No records
			assertNull(currentBatch.getEntityBatch(srcAccEntity1));
			List<JobDetail> jobs = jobDetailRepo.findAll();
			assertEquals(1,jobs.size());

		}finally {
			entitySource.factory = originalFactory;
			entitySource.resyncService = originalResyncService;
			entitySource.graphService = originalGraphService;
		}
	}

	@Test
	public void closeSource_ResyncComplete(){

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityDefinition source = new EntityDefinition("srcAccEntity1", "SourceAccEntity1").setConnectorId(connector.getId())
				.setStatus(Status.ACTIVE);
		source.setId("srcAccEntityId1");
		source.setDraftStatus(DraftStatus.APPROVED);
		var sources = List.of(source);

		var originalStagingRepo = entitySource.stagingRepo;
		var originalResyncService = entitySource.resyncService;
		try {
			// create resync and corresponding watermark
			long now = System.currentTimeMillis();
			long lastWatermark = now - 5000l; // return lastWatermark returned by iterator as 5 second before now
			ResyncDetail resync = new ResyncDetail().setStartTime(Instant.EPOCH).setEndTime(Instant.ofEpochMilli(now))
					.setMode(ResyncDetail.Mode.RESYNC).setStatus(ResyncStatus.PROCESSING).setSyncariEntityId(source.getId())
					.setSyncariEntityName(syncariAccEntity.getApiName())
					.setEntitiesToResync(sources.stream().collect(Collectors.toMap(EntityDefinition::getId, s -> ResyncStatus.PROCESSING)));
			resync = resyncDetailRepo.save(resync);
			Watermark w = new Watermark().setStart(now).setEnd(now).setResync(true);
			syncRepo.save(new SyncDetail(source.getId(), syncariAccEntity.getApiName(), w));

			StagedBatch stagedBatch = new StagedBatch().setEntityName(syncariAccEntity.getApiName()).setConnectorId(connector.getId())
					.setCurrentBatchId("123").setSourceEntityName(source.getApiName()).setSourceEntityDefinitionId(source.getId())
					.setWatermark(w);
			CurrentBatch currentBatch = new CurrentBatch(null).setSyncariEntityName("account").setCurrentBatchId("123");
			currentBatch.setEntityBatch(sources.get(0), stagedBatch);
			currentBatch.setCurrentWatermark(sources.get(0), stagedBatch.getWatermark());

			StagedBatchRepo mockStagedBatchRepo = mock(StagedBatchRepo.class);
			doReturn(List.of(new StagedBatch())).when(mockStagedBatchRepo).findByCurrentBatchId(any());
			entitySource.stagingRepo = mockStagedBatchRepo;

			ResyncService mockResyncService = mock(ResyncService.class);
			doReturn(Optional.of(resync)).when(mockResyncService).findProcessingOrCancelRequestedResync(syncariAccEntity.getId());
			doReturn(true).when(mockResyncService).isComplete(any(), any());
			doNothing().when(mockResyncService).success(any(), any());
			entitySource.resyncService = mockResyncService;
			GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
			entitySource.closeSource(graphContext);
			var syncDetail = syncRepo.findWatermark(source.getId(), "account").get();
			assertFalse(syncDetail.getWatermark().isResync());
			verify(mockResyncService).findProcessingOrCancelRequestedResync(any());
			verify(mockResyncService).success(any(), any());
			verify(mockResyncService).isComplete(any(), any());
		} finally {
			entitySource.resyncService = originalResyncService;
			entitySource.stagingRepo = originalStagingRepo;
		}

	}
	
	@Test
	public void pullEventDataFetches() {
		Map<String, AttributeDefinition> apiNameToAttrMap = new HashMap<>();
		Map<String, StagedBatch> batches = new HashMap<>();
		Set<String> externalRecordIds = new HashSet<>();
		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		EntityFetchResult entityFetchResult = new EntityFetchResult(syncariAccEntity, null, null, connector, null,
				new Watermark(), dbSteup);
		
		MappingGraph graph = new MappingGraph();
		graph.setId("123");
		entitySource.pullEventData(entityFetchResult, "account", apiNameToAttrMap, batches,
				externalRecordIds , "123", graph, new CurrentBatch(stagedBatchRecordRepo), 10l);
		assertEquals(0, externalRecordIds.size());
		assertEquals(0, batches.size());
		assertEquals(0, stagedBatchRecordRepo.count());
		
		eventDataRepo.save(new EventData().setConnectorId(connector.getId()).setEventId("123").setGraphId("123")
				.setData(new EntityData("account").setId("12345").setDeleted(true)).setOperation(Operation.delete));
		entitySource.pullEventData(entityFetchResult, "account", apiNameToAttrMap, batches,
				externalRecordIds , "123", graph, new CurrentBatch(stagedBatchRecordRepo), 10l);
		assertEquals(1, externalRecordIds.size());
		assertEquals(1, batches.size());
		assertNotNull(batches.get(syncariAccEntity.getId()));
		assertEquals(1, stagedBatchRecordRepo.count());
		assertTrue(stagedBatchRecordRepo.findAll().get(0).getEntityData().isDeleted());
		
	}


	@Test
	public void pullEventDataCompression() throws IOException  {
		String cycleId = "1234";
		try {
			Map<String, AttributeDefinition> apiNameToAttrMap = new HashMap<>();
			Map<String, StagedBatch> batches = new HashMap<>();
			Set<String> externalRecordIds = new HashSet<>();

			MappingGraph graph = new MappingGraph();
			graph.setId("123");

			EntityDefinition syncariAccEntity = new EntityDefinition().setApiName("AccountA").setConnectorId(connector.getId());
			syncariAccEntity.setId("account1");
			EntityFetchResult entityFetchResult = new EntityFetchResult(syncariAccEntity, null, null, connector, null,
					new Watermark(), dbSteup);

			// Set up events
			eventDataService.save(setupEvents(connector, graph));
			entitySource.pullEventData(entityFetchResult, "account", apiNameToAttrMap, batches,
					externalRecordIds , cycleId, graph, new CurrentBatch(stagedBatchRecordRepo), 10l);

			assertTrue(externalRecordIds.size() > 0);
			assertEquals(1, batches.size());
			assertEquals(100, stagedBatchRecordRepo.count());

			externalRecordIds = new HashSet<>();
			syncariAccEntity = new EntityDefinition().setApiName("AccountB").setConnectorId(connector.getId());
			syncariAccEntity.setId("account2");
			entityFetchResult = new EntityFetchResult(syncariAccEntity, null, null, connector, null,
					new Watermark(), dbSteup);

			entitySource.pullEventData(entityFetchResult, "account", apiNameToAttrMap, batches,
					externalRecordIds , cycleId, graph, new CurrentBatch(stagedBatchRecordRepo), 10l);

			assertTrue(externalRecordIds.size() > 0);
			assertEquals(2, batches.size());
			assertEquals(2, stagedBatchRepo.findByCurrentBatchId(cycleId).size());

			List<StagedBatchRecord> records = stagedBatchRecordRepo.findByStagedBatchIdIn(List.of("account1"), null, 1000);
			assertTrue(records.stream().allMatch(r -> r.getEntityData().getName().equals("AccountA")));
			assertTrue(records.stream().map(StagedBatchRecord::getEntityData)
					.collect(Collectors.groupingBy(e -> e.getId(), LinkedHashMap::new, Collectors.toList()))
					.values().stream().map(l -> l.get(l.size() -1)).allMatch(e -> e.getValue("val").equals(9)));

			records = stagedBatchRecordRepo.findByStagedBatchIdIn(List.of("account2"), null, 1000);
			assertTrue(records.stream().allMatch(r -> r.getEntityData().getName().equals("AccountB")));
			assertTrue(records.stream().map(StagedBatchRecord::getEntityData)
					.collect(Collectors.groupingBy(e -> e.getId(), LinkedHashMap::new, Collectors.toList()))
					.values().stream().map(l -> l.get(l.size() -1)).allMatch(e -> e.getValue("val").equals(9)));

		} finally {
			stagedBatchRecordRepo.reset();
			stagedBatchRepo.reset();
			eventDataService.deleteByBatchId(cycleId);
		}
	}

	public List<EventData> setupEvents(Connector connector, MappingGraph graph) throws IOException {
		String connectorId = connector.getId();
		String graphId = graph.getId();

		String currentBatchId = UUID.randomUUID().toString();
		return IntStream.range(0, 100).mapToObj(i -> {
			List<EventData> events = new ArrayList<>();
			String id = Integer.toString(i);
			var entityData = new EntityData().setId(id).setName("AccountA").addValue("val", 0);
			var inserEvent = new EventData().setGraphId(graphId).setConnectorId(connectorId).setBatchId(currentBatchId)
					.setOperation(Operation.create).setData(entityData);

			events.add(inserEvent);
			var updates = IntStream.range(1, 9).mapToObj(j -> {
				var ed = new EntityData().setId(id).setName("AccountA").addValue("val", j);
				return new EventData().setGraphId(graphId).setConnectorId(connectorId).setBatchId(currentBatchId)
						.setOperation(Operation.update).setData(ed);
			}).collect(Collectors.toList());
			events.addAll(updates);

			entityData = new EntityData().setId(id).setName("AccountB").addValue("val", 0);
			inserEvent = new EventData().setGraphId(graphId).setConnectorId(connectorId).setBatchId(currentBatchId)
					.setOperation(Operation.create).setData(entityData);

			events.add(inserEvent);
			updates = IntStream.range(1, 9).mapToObj(j -> {
				var ed = new EntityData().setId(id).setName("AccountB").addValue("val", j);
				return new EventData().setGraphId(graphId).setConnectorId(connectorId).setBatchId(currentBatchId)
						.setOperation(Operation.update).setData(ed);
			}).collect(Collectors.toList());

			events.addAll(updates);
			return events;
		}).flatMap(List::stream).collect(Collectors.toList());
	}

	@Test
	public void stopSyncAfterRecordsExhaustedTest() {
		long endTime = Instant.now().toEpochMilli();
		Watermark watermark = new Watermark(Instant.EPOCH.toEpochMilli(), endTime, false, 0);
		watermark.setDirection(SyncDirection.INBOUND);
		SyncDetail syncDetail = new SyncDetail("syncDetailTestId",
				"contact", watermark, 1655943499, endTime - 10, true);
		syncDetail.setNextSyncAt(Instant.now().toEpochMilli());
		syncRepo.save(syncDetail);
		CurrentBatch currentBatch = new CurrentBatch(stagedBatchRecordRepo);
		currentBatch.setSyncariEntityName("contact");
		currentBatch.setCurrentBatchId("currentBatchTestId");
		EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setId("syncDetailTestId");
		currentBatch.setCurrentWatermark(entityDefinition, watermark);
		GraphContext graphContext = new GraphContext();
		graphContext.setCurrentBatch(currentBatch);
		entitySource.closeSource(graphContext);
		Optional<SyncDetail> updatedSyncDetail = syncRepo.findWatermark("syncDetailTestId", "contact", SyncDirection.INBOUND);
		assertTrue(updatedSyncDetail.isPresent());
		assertFalse(updatedSyncDetail.get().isOnGoingSync());
		assertEquals(updatedSyncDetail.get().getStartTime(), 0);
		assertEquals(updatedSyncDetail.get().getEndTime(), 0);
	}

	@Test
	public void testOngoingSyncWithResync() {

		// setup ongoing sync in
		long endTime = Instant.now().toEpochMilli();
		Watermark watermark = new Watermark(Instant.EPOCH.toEpochMilli(), endTime, false, 0);
		watermark.setDirection(SyncDirection.INBOUND);

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();

		List<EntityDefinition> sources = schemaService.getEntities(connector.getId()).stream()
				.filter(e->e.getDisplayName().equals("Account"))
				.collect(Collectors.toList());
		// create initial sync watermark
		long now = System.currentTimeMillis();
		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setInitial(true);
			syncRepo.save(new SyncDetail(e.getId(), syncariAccEntity.getApiName(), w, 1655943499, endTime - 10, true));
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		MappingGraphService mockGraphService = mock(MappingGraphService.class);
		entitySource.graphService = mockGraphService;
		when(mockGraphService.findById(graph.getId())).thenReturn(Optional.of(graph));

		CurrentBatch currentBatch =
				entitySource.fetchSource(new DataSourceRequest()
						.setSourceEntities(sources)
						.setSyncariEntity(syncariAccEntity)
						.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));


		ResyncDetail resync = new ResyncDetail().setStartTime(Instant.EPOCH).setEndTime(Instant.ofEpochMilli(now))
				.setMode(ResyncDetail.Mode.RESYNC).setStatus(ResyncStatus.PROCESSING).setSyncariEntityId(syncariAccEntity.getId())
				.setSyncariEntityName(syncariAccEntity.getApiName())
				.setEntitiesToResync(sources.stream().collect(Collectors.toMap(EntityDefinition::getId, s -> ResyncStatus.PROCESSING)));

		ResyncService mockResyncService = mock(ResyncService.class);
		doReturn(Optional.of(resync)).when(mockResyncService).findProcessingOrCancelRequestedResync(syncariAccEntity.getId());
		doReturn(true).when(mockResyncService).isComplete(any(), any());
		doNothing().when(mockResyncService).success(any(), any());
		entitySource.resyncService = mockResyncService;
		GraphContext graphContext = new GraphContext().setCurrentBatch(currentBatch);
		entitySource.closeSource(graphContext);

		// retrieve sync repo
		sources.forEach(s -> {
			Optional<SyncDetail> syncDetail = syncService.findUpstreamWatermark(syncariAccEntity.getApiName(), s.getId());
			assertTrue(syncDetail.isPresent());
			assertFalse(syncDetail.get().isOnGoingSync());
			assertEquals(0L, syncDetail.get().getStartTime());
			assertEquals(0L, syncDetail.get().getEndTime());
		});
	}

	@Test
	public void fetchSourceWithException() {

		EntityDefinition syncariAccEntity = schemaService.getSyncariEntityByName("account").get();
		List<EntityDefinition> sources = schemaService.getEntities(connector.getId()).stream()
				.filter(e->e.getDisplayName().equals("Organization"))
				.collect(Collectors.toList());
		// create initial sync watermark
		long now = System.currentTimeMillis();
		GraphHelper helper = GraphHelper.newGraph(syncariAccEntity);
		MappingGraph graph = helper.src(sources.get(0)).connect(sources.get(0).getApiName(), syncariAccEntity.getApiName()).getGraph();
		entitySource.schemaService = mock(SchemaService.class);
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getSyncariEntityByName(syncariAccEntity.getApiName());
		doReturn(Optional.of(syncariAccEntity)).when(entitySource.schemaService).getEntityByName(syncariConnector.getId(), syncariAccEntity.getApiName());
		sources.forEach( e -> {
			Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.EPOCH.toEpochMilli()).setInitial(true);
			syncRepo.save(new SyncDetail(e.getId(), syncariAccEntity.getApiName(), w));
			doReturn(e).when(entitySource.schemaService).getSourceEntityWithMappedAndSystemFields(syncariAccEntity, e, graph);
		});

		DataService mockDataService = mock(DataService.class);
		DataServiceFactory mockFactory = mock(DataServiceFactory.class);
		doReturn(mockDataService).when(mockFactory).getDataService(any());
		entitySource.factory = mockFactory;

		when(mockDataService.getByWatermark(any())).thenThrow(new RuntimeException("Test Entity exception"));

		// create Resync for INITALSYNC mode

		Exception exception = null;
		try {
			entitySource.fetchSource(new DataSourceRequest()
					.setSourceEntities(sources)
					.setSyncariEntity(new EntityDefinition("account", "account"))
					.setWatermark(new Watermark()).setGraph(graph).setSyncStartTime(Instant.now().toEpochMilli()));
		} catch (Exception e) {
			exception = e;
		}
		assertNotNull(exception);
		assertTrue(exception instanceof PipelineException);
		assertEquals("java.lang.RuntimeException: Test Entity exception", exception.getMessage());
		assertTrue(!StringUtils.isBlank(((PipelineException) exception).getNodeId()));
		assertTrue(!StringUtils.isBlank(((PipelineException) exception).getGraphId()));
	}

	@Test
	public void getSourceRequestsIncludesUnexpiredAndExpiredMarkedForProcessing() {

		RequeueRequest unexpired1 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("ur1");
		RequeueRequest unexpired2 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("ur2");
		RequeueRequest unexpired3 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setRetryTimeLimit(ZonedDateTime.now().plusDays(1)).setRecordId("ur3");

		RequeueRequest r1 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r1");
		RequeueRequest r2 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r2");
		RequeueRequest r3 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r3");
		RequeueRequest r4 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r4");
		RequeueRequest r5 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r5");

		final List<RequeueRequest> requeueRequests = requeueRequestRepo.saveAll(List.of(unexpired1, unexpired2, unexpired3, r1, r2, r3, r4, r5));
		final EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setId("e1");
		RequeuedSourcePage page = entitySource.getSourceRequeueRequests("g1", entityDefinition);
		List<RequeueRequest> collectedRequests = new ArrayList<>();
		//Roughly mimick the while loop in the pullRequeuedRecords method
		while (page.hasContent()) {
			collectedRequests.addAll(page.getCurrentPage().getContent());
			page = page.hasNext() ? entitySource.getSourceRequeueRequests("g1", entityDefinition, page) : RequeuedSourcePage.EMPTY;
		}
		assertEquals(8, collectedRequests.size());
		final Set<String> actualRecordIds = collectedRequests.stream().map(c -> c.getRecordId()).collect(Collectors.toSet());
		final Set<String> expectedRecordIds = requeueRequests.stream().map(c -> c.getRecordId()).collect(Collectors.toSet());
		assertEquals(expectedRecordIds, actualRecordIds);
	}

	@Test
	public void getSourceRequestsFallsBackToExpiredMarkedForProcessing() {
		RequeueRequest r1 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r1");
		RequeueRequest r2 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r2");
		RequeueRequest r3 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r3");
		RequeueRequest r4 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r4");
		RequeueRequest r5 = new RequeueRequest()
				.setEntityDefinitionId("e1")
				.setGraphId("g1")
				.setRecordType(RequeueRequest.RecordType.SOURCE)
				.setProcessExpiredRecord(true)
				.setRetryTimeLimit(ZonedDateTime.now().minusDays(1)).setRecordId("r5");

		final List<RequeueRequest> requeueRequests = requeueRequestRepo.saveAll(List.of(r1, r2, r3, r4, r5));
		final EntityDefinition entityDefinition = new EntityDefinition();
		entityDefinition.setId("e1");
		RequeuedSourcePage page = entitySource.getSourceRequeueRequests("g1", entityDefinition);
		List<RequeueRequest> collectedRequests = new ArrayList<>();
		//Roughly mimick the while loop in the pullRequeuedRecords method
		while (page.hasContent()) {
			collectedRequests.addAll(page.getCurrentPage().getContent());
			page = page.hasNext() ? entitySource.getSourceRequeueRequests("g1", entityDefinition, page) : RequeuedSourcePage.EMPTY;
		}
		assertEquals(5, collectedRequests.size());
		final Set<String> actualRecordIds = collectedRequests.stream().map(c -> c.getRecordId()).collect(Collectors.toSet());
		final Set<String> expectedRecordIds = requeueRequests.stream().map(c -> c.getRecordId()).collect(Collectors.toSet());
		assertEquals(expectedRecordIds, actualRecordIds);
	}

	private AuthConfig getAuthCOnfig() {
		AuthConfig authConfig = new AuthConfig();
		authConfig.setToken("dev@syncari.com/token");
		authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
		return authConfig;
	}
}
