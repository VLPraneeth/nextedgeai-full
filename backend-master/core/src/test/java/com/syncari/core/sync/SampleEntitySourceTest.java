package com.syncari.core.sync;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.MappingGraph;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.connector.EntityData;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataSourceRequest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.IntegrationTest;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.repositories.customer.StagedBatchRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.SchemaService;
import com.syncari.core.sync.EntitySourceHelper;
import com.syncari.core.utils.GraphHelper;

@Category(IntegrationTest.class)
public class SampleEntitySourceTest extends AbstractSyncariTest {
	private Connector connector;
	@Autowired
	private ConnectorService connectorService;
	@Autowired
	private EndSystemConfig config;
	@Autowired
	private ConnectorRepo connectorRepo;
	@Autowired
	private StagedBatchRepo stagedBatchRepo;
	@Autowired
	private StagedBatchRecordRepo stagedBatchRecordRepo;
	@Autowired
	private SampleEntitySource entitySource;

	private Connector connector2;

	@After
	public void tearDown() {
		resetRepos(connectorRepo,  stagedBatchRepo,
				stagedBatchRecordRepo);
	}

	@Before
	public void setUp() {
		super.setUp();
		connectorService.publisher = publisher;
		connector = new Connector("sfdc1", connectorService.describe("salesforce").getId(), config.getSalesforceUrl(),
				config.getUser(), config.getPassword());
		connector.getAuthConfig().setToken(config.getToken());
		connector = connectorService.save(connector);
		connector2 = new Connector("sfdc2", connectorService.describe("salesforce").getId(), config.getSalesforceUrl(),
				config.getUser(), config.getPassword());
		connector2.getAuthConfig().setToken(config.getToken());
		connector2 = connectorService.save(connector2);
	}

	@Test
	public void byIdsForSelectedSources() {
		assertEquals(0, stagedBatchRepo.count());
		assertEquals(0, stagedBatchRecordRepo.count());

		List<EntityDefinition> sources = new ArrayList<>();
		EntityDefinition syncariEntity = new EntityDefinition("account", "account");
		MappingGraph graph = GraphHelper.createGraph("t", Scope.ENTITY);
		EntityDefinition source1 = new EntityDefinition("Account", "Account").setConnectorId(connector.getId());
		sources.add(source1);
		source1.setId(ObjectId.get().toHexString());

		EntityDefinition source2 = new EntityDefinition("Account", "Account").setConnectorId(connector2.getId());
		sources.add(source2);
		source2.setId(ObjectId.get().toHexString());
		// create initial sync watermark
		entitySource.factory = mock(DataServiceFactory.class);
		entitySource.schemaService = mock(SchemaService.class);
		when(entitySource.schemaService.getSourceEntityWithMappedAndSystemFields("account",source1.getId(),false)).thenReturn(source1);
		when(entitySource.schemaService.getSourceEntityWithMappedAndSystemFields(syncariEntity, source1, graph)).thenReturn(source1);
		when(entitySource.schemaService.getSourceEntityWithMappedAndSystemFields("account",source2.getId(),false)).thenReturn(source2);
		when(entitySource.schemaService.getSourceEntityWithMappedAndSystemFields(syncariEntity, source2, graph)).thenReturn(source2);
		DataService dataService = mock(DataService.class);
		when(entitySource.factory.getDataService(any())).thenReturn(dataService);
		when(dataService.getByIds(any())).thenReturn(List.of(new EntityData().setId("1234").setName("Account")));

		entitySource.helper = mock(EntitySourceHelper.class);
		when(entitySource.helper.fixDatatypes(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
		when(entitySource.helper.toExternal(any(), any())).thenReturn(new ArrayList<>());
		CurrentBatch currentBatch = entitySource.fetchSourceById(new DataSourceRequest().setSourceEntities(sources)
				.setSyncariEntity(syncariEntity)
				.setRecordIds(Map.of(source1.getId(), List.of("1234")))
				.setGraph(graph));

		assertEquals(1, currentBatch.getEntityBatches().size());
		assertEquals(1, stagedBatchRepo.count());
		assertEquals(1,stagedBatchRecordRepo.count());

		CurrentBatch currentBatch2 = 
				entitySource.fetchSourceById(new DataSourceRequest().setSourceEntities(sources)
						.setSyncariEntity(syncariEntity)
						.setRecordIds(Map.of(source1.getId(), List.of("1234"),source2.getId(), List.of("4444")))
						.setGraph(graph));

		assertEquals(2, currentBatch2.getEntityBatches().size());
		assertEquals(3, stagedBatchRepo.count());
		assertEquals(3,stagedBatchRecordRepo.count());

	}

}
