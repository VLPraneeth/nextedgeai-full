package com.syncari.core.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.SyncDetail;
import com.syncari.core.model.util.SyncDirection;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.ResyncDetailRepo;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.TestConfig;
import com.syncari.connector.service.SalesforceService;
import com.syncari.utils.DateUtil;
import com.syncari.core.model.Connector;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.SyncDetailRepo;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class WatermarkServiceTest extends AbstractSyncariTest {
	@Autowired
	WatermarkService service;
	@Autowired
	ConnectorService connectorService;
	@Autowired
	SyncDetailRepo syncRepo;
	Connector connector;
	@Mock
	DataServiceFactory factory;
	@Mock
	SalesforceService sfdcMock;
	@Autowired
	ConnectorRepo connectorRepo;
	@Autowired
	ResyncDetailRepo resyncDetailRepo;
	@Autowired
	SchemaService schemaService;
	@Autowired
	EntityDefinitionRepo entityProxyRepo;

	EntityDefinition account = new EntityDefinition("account","account");
	EntityDefinition contact = new EntityDefinition("contact","contact");
	@Override
	public void setUp() {
		super.setUp();
		connector = new Connector("test", connectorService.describe("salesforce").getId(), "http://test.salesforce.com");
		connectorService.save(connector);
		account = entityProxyRepo.save(account);
		contact = entityProxyRepo.save(contact);
	}
	
	@Override
	public void tearDown() {
		Mockito.reset(factory, sfdcMock);
		resetRepos(connectorRepo, syncRepo);
		super.tearDown();
	}

	@Test
	public void whenNoWatermarkExistsInitializeWatermarkToDefaultStartTime() {
		assertTrue(syncRepo.findAll().isEmpty());

		Watermark watermark = service.getOrCreateWatermark(connector, "account", account);

		assertTrue(syncRepo.findAll().size() == 1);
		assertEquals(Instant.parse(WatermarkService.INITIAL_SYNC_START_DATE).toEpochMilli(), watermark.getStart());
		assertEquals(Instant.parse(WatermarkService.INITIAL_SYNC_START_DATE).toEpochMilli(), watermark.getEnd());
		assertEquals(true, watermark.isInitial());
		assertEquals(0, watermark.getOffset());
	}
	
	@Test
	public void whenNoWatermarkExistsInitializeWatermarkToFirstEntityCreatedTime() {
		assertTrue(syncRepo.findAll().isEmpty());
		long start = Instant.now().toEpochMilli() - (DateUtil.MINUTES_IN_A_DAY * 60 * 1000);
		when(sfdcMock.getFirstCreatedTime(any())).thenReturn(start);
		when(factory.getDataService(any())).thenReturn(sfdcMock);
		service.factory = factory;
		Watermark watermark = service.getOrCreateWatermark(connector, "account", account);

		assertTrue(syncRepo.findAll().size() == 1);
		assertEquals(start, watermark.getStart());
		assertEquals(start, watermark.getEnd());
		assertEquals(true, watermark.isInitial());
		assertEquals(0, watermark.getOffset());
	}
	
	@Test
	public void whenNoWatermarkExistsSetsDefaultStartIfFirstEntityCreatedTimeLessThan0() {
		assertTrue(syncRepo.findAll().isEmpty());
		when(sfdcMock.getFirstCreatedTime(any())).thenReturn(-2L);
		when(factory.getDataService(any())).thenReturn(sfdcMock);
		service.factory = factory;
		Watermark watermark = service.getOrCreateWatermark(connector, "account", account);

		assertTrue(syncRepo.findAll().size() == 1);
		assertEquals(Instant.parse(WatermarkService.INITIAL_SYNC_START_DATE).toEpochMilli(), watermark.getStart());
		assertEquals(Instant.parse(WatermarkService.INITIAL_SYNC_START_DATE).toEpochMilli(), watermark.getEnd());
		assertEquals(true, watermark.isInitial());
		assertEquals(0, watermark.getOffset());
	}
	
	@Test
	public void whenWatermarkExistsMovesByWindowForIncremental() {
		assertTrue(syncRepo.findAll().isEmpty());
		when(sfdcMock.getFirstCreatedTime(any())).thenReturn(-2L);
		when(factory.getDataService(any())).thenReturn(sfdcMock);
		service.factory = factory;
		Watermark watermark = service.getOrCreateWatermark(connector, "account", account);

		assertTrue(syncRepo.findAll().size() == 1);
		long start = watermark.getStart();
		assertEquals(Instant.parse(WatermarkService.INITIAL_SYNC_START_DATE).toEpochMilli(), start);
		assertEquals(Instant.parse(WatermarkService.INITIAL_SYNC_START_DATE).toEpochMilli(), watermark.getEnd());
		assertEquals(true, watermark.isInitial());
		assertEquals(0, watermark.getOffset());
		watermark.setInitial(false);
		//set end to a minute before now
		long end = Instant.now().toEpochMilli() - 60*1000;
		watermark.setEnd(end);
		watermark.setStart(end);
		service.updateWatermark(account, "account", watermark);
		
		watermark = service.getOrCreateWatermark(connector, "account", account);
		assertTrue(syncRepo.findAll().size() == 1);
		assertEquals(end, watermark.getStart());
		assertEquals(end, watermark.getEnd());
		assertFalse(watermark.isInitial());
		assertEquals(0, watermark.getOffset());
	}

	@Test
	public void updateWatermarkValidations() {
		assertTrue(syncRepo.findAll().isEmpty());
		Watermark watermark = service.getOrCreateWatermark(connector, "account", account);
		assertTrue(syncRepo.findAll().size() == 1);
		assertEquals(Instant.parse(WatermarkService.INITIAL_SYNC_START_DATE).toEpochMilli(), watermark.getStart());
		assertEquals(Instant.parse(WatermarkService.INITIAL_SYNC_START_DATE).toEpochMilli(), watermark.getEnd());
		assertEquals(true, watermark.isInitial());
		assertEquals(0, watermark.getOffset());
		
		try {
			service.updateWatermark(account, "account", null);
			fail();
		} catch (Exception e) {
			assertEquals("Watermark cannot be null", e.getMessage());
		}
		try {
			service.updateWatermark(account, "account", new Watermark(-1, -1, true, 0));
			fail();
		} catch (Exception e) {
			assertEquals("Watermark should have start", e.getMessage());
		}
		try {
			service.updateWatermark(account, "account", new Watermark(12346, 12345, true, 0));
			fail();
		} catch (Exception e) {
			assertEquals("End cannot be less than start for watermark", e.getMessage());
		}
		try {
			EntityDefinition nonexistent = new EntityDefinition("Something", "something");
			nonexistent.setId(ObjectId.get().toHexString());
			nonexistent.setConnectorId("nonexistant");
			service.updateWatermark(nonexistent, "account", new Watermark(12344, 12345, true, 0));
			fail();
		} catch (Exception e) {
			assertEquals("Sync details for entity account and connector nonexistant not found", e.getMessage());
		}
	}

	@Test
	public void resetSourceWatermarkWithFirstCreated(){
		assertTrue(syncRepo.findAll().isEmpty());
		long start = Instant.now().toEpochMilli() - (DateUtil.MINUTES_IN_A_DAY * 60 * 1000);
		when(sfdcMock.getFirstCreatedTime(any())).thenReturn(start);
		when(factory.getDataService(any())).thenReturn(sfdcMock);
		service.factory = factory;

		Watermark watermark = service.getOrCreateWatermark(connector, "account", account);
		assertTrue(syncRepo.findAll().size() == 1);
		watermark.setStart(watermark.getStart() - 100l);
		watermark.setEnd(watermark.getEnd() - 100l);

		var watermarkCopy = new Watermark(watermark.getStart(), watermark.getEnd(), watermark.isInitial(), watermark.getOffset());
		// if provided watermark is before end system's firstCreatedDate, it will be reset to firstCreatedDate
		service.resetSourceWatermark(connector, account, "account", watermark);
		var newWatermark = service.getOrCreateWatermark(connector, "account", account);
		assertTrue(newWatermark.getStart() > watermarkCopy.getStart());
		assertTrue(newWatermark.getStart() - watermarkCopy.getStart() == 100);
		assertTrue(newWatermark.getEnd() > watermarkCopy.getEnd());
		assertTrue(newWatermark.getEnd() - watermarkCopy.getEnd() == 100);

		// if provided watermark is after end system's firstCreatedDate, it will be reset to latest date
		watermark.setStart(newWatermark.getStart() + 100);
		watermark.setEnd(newWatermark.getEnd() + 100l);
		watermarkCopy = new Watermark(watermark.getStart(), watermark.getEnd(), watermark.isInitial(), watermark.getOffset());
		service.resetSourceWatermark(connector, account, "account", watermark);
		newWatermark = service.getOrCreateWatermark(connector, "account", account);
		assertEquals(newWatermark.getStart(), watermarkCopy.getStart());
		assertEquals(newWatermark.getEnd(), watermarkCopy.getEnd());
	}

	@Test
	public void resetSourceWatermarkOutboundWatermark(){
		var watermark = new Watermark(0l, 0l, true, 0l).setDirection(SyncDirection.OUTBOUND);
		try{
			service.resetSourceWatermark(connector, account, "account", watermark);
			fail();
		} catch (RuntimeException e){
			assertEquals("Cannot reset OUTBOUND watermark", e.getMessage());
		}
	}

	@Test
	public void updateNextSyncAtForAllEntitiesOfConnector(){
		var orgGraphService = service.graphService;
		try{
			Instant now = Instant.now();
			var mockGraphService = mock(MappingGraphService.class);
			doReturn(Map.of(account.getId(), Set.of(), contact.getId(), Set.of())).when(mockGraphService).getMappedEntities(connector.getId());
			service.graphService = mockGraphService;

			var sourceAccWm = syncRepo.save(new SyncDetail(account.getId(), "account",
					new Watermark(0, 0,true,0).setDirection(SyncDirection.INBOUND)));
			var sinkAccWm = syncRepo.save(new SyncDetail(account.getId(), "account",
					new Watermark(0, 0,true,0).setDirection(SyncDirection.OUTBOUND)));

			var sourceContactWm = syncRepo.save(new SyncDetail(contact.getId(), "contact",
					new Watermark(0, 0,true,0).setDirection(SyncDirection.INBOUND)));

			List<SyncDetail> syncDetails = syncRepo.findAll();
			assertEquals(3, syncDetails.size());
			syncDetails.forEach( s -> {
				assertEquals(0, s.getNextSyncAt());
			});

			service.updateNextSyncAtForAllEntitiesOfConnector(connector.getId(), now.getEpochSecond(), true);
			verify(mockGraphService).getMappedEntities(connector.getId());
			syncDetails = syncRepo.findAll();
			assertEquals(3, syncDetails.size());
			syncDetails.forEach( s -> {
				assertEquals(now.getEpochSecond(), s.getNextSyncAt());
			});

		} finally {
			service.graphService = orgGraphService;
		}
	}
}
