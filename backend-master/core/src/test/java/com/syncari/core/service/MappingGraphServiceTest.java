package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.database.MysqlService;
import com.syncari.connector.service.SalesforceService;
import com.syncari.connector.service.TestSynapseService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.ExternalIdType;
import com.syncari.core.datatype.ReferenceType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.model.misc.ResyncStatus;
import com.syncari.core.model.misc.RoleConstants;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.model.versioning.ActionType;
import com.syncari.core.model.versioning.Version;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.schema.ClonePipelineEntityDef;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DirtiesContext
public class MappingGraphServiceTest extends AbstractSyncariTest {

	@Autowired
	MappingGraphService mappingGraphService;

	@Autowired
	PipelineTestService pipelineTestService;

	@Autowired
	MappingGraphRepo mappingGraphRepo;

	@Autowired
	private MappingNodeRepo nodeRepo;
	@Autowired
	private EdgeRepo edgeRepo;

	@Autowired
	private FunctionService functionDefinitionRepo;
	@Autowired
	private ConnectorRepo connectorRepo;
	@Autowired
	ConnectorService connectorService;

	@Autowired
	SchemaService schemaService;

	@Autowired
	EndSystemConfig config;

	@Autowired
	AttributeRepo attributeProxyRepo;

	@MockBean
	private SalesforceService salesforceService;

    @MockBean
	private MysqlService mysqlService;

	@Autowired
	StreamRepo streamRepo;
	@Autowired
	EntityDefinitionRepo entityProxyRepo;

	@Autowired
	LayoutService layoutService;

	@Autowired
	ComponentDependencyService dependencyService;
	
	@Autowired
	NotificationRepo notificationRepo;
	
	@Autowired
	UserService userService;

	@Autowired
	WatermarkService watermarkService;

	@Autowired
	StreamService streamService;

	@Autowired
	SyncDetailRepo syncDetailRepo;

	@Autowired
	ResyncService resyncService;

	@Autowired
	ResyncDetailRepo resyncRepo;

	@Autowired
	FunctionService functionService;

	@Autowired
	LockRepo lockRepo;

	@Autowired
	TestSynapseService testSynapseService;

	@Autowired
	FeatureService featureService;

	Connector testConnector = null;

    @Override
    public void setUp() {
        super.setUp();
		// Clean up syncDetailRepo at the start to ensure clean state
		syncDetailRepo.deleteAll();
        connectorService.publisher = publisher;
        User notificationUser = new User("notif@email.com", "NewPassw0rd", Status.ACTIVE, SyncariContext.getSyncariId());
        notificationUser.addAvailableInstance(SyncariContext.getSyncariId());
        userService.addUser(notificationUser);
        userService.assignRolesToUser(SyncariContext.getOrganziation(), SyncariContext.getInstance(), notificationUser, Set.of(RoleConstants.ORG_ADMIN));
        mappingGraphService.setResyncService(resyncService);
        mappingGraphService.setStreamService(streamService);

	}

	@Override
	public void tearDown() {
		super.tearDown();
		resetRepos(attributeProxyRepo, entityProxyRepo, nodeRepo, edgeRepo, mappingGraphRepo, streamRepo,
				connectorRepo, resyncRepo, lockRepo, notificationRepo, syncDetailRepo);
		userService.deleteUser(userService.getUser("notif@email.com").getId());
	}

	@Test
	public void creatingDraftClonesEdgesNodesDocsSettings() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());

		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));

		var node1 = nodeRepo.save(new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId()));

		var node2 = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId()));

		var edge1 = edgeRepo.save(new Edge().setGraphId(defaultEntityGraph.getId()).setInput(InputPort.any())
				.setOutput(OutputPort.any()).setDestinationStage(node2).setSourceStage(node1));
		final PipelineSettings settings = new PipelineSettings(true, true, true, false, false, "", "","");
		defaultEntityGraph.setSettings(settings);
		final Documentation docContent = new Documentation().setContent("doc content").setFormat(Format.PLAIN_TEXT);
		defaultEntityGraph.setDocumentation(docContent);
		mappingGraphService.saveGraph(defaultEntityGraph);
		assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());


		var approved = mappingGraphService.approveDraft(defaultEntityGraph);
		approved = mappingGraphService.retrieve(approved.getId()).get();
		assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
		assertTrue(approved.getSettings().isContinuousPipeline());
		assertTrue(approved.getSettings().isNodeLoggingEnabled());
		assertEquals("doc content", approved.getDocumentation().getContent());
		assertEquals(Format.PLAIN_TEXT, approved.getDocumentation().getFormat());

		var newDraft = mappingGraphService.createDraftFor(approved);

		assertEquals(DraftStatus.NEW, newDraft.getDraftStatus());

		Optional<MappingGraph> draft = mappingGraphService.findDraft(approved);
		assertTrue(draft.isPresent());
		assertEquals(3, draft.get().getNodes().size());
		assertEquals(1, draft.get().getEdges().size());
		assertTrue(draft.get().getSettings().isContinuousPipeline());
		assertTrue(draft.get().getSettings().isNodeLoggingEnabled());
		assertEquals("doc content", draft.get().getDocumentation().getContent());
		assertEquals(Format.PLAIN_TEXT, draft.get().getDocumentation().getFormat());

		mappingGraphService.discardDraft(draft.get());
		assertFalse(mappingGraphService.hasDraft(approved));
		assertTrue(nodeRepo.findByGraphId(draft.get().getId()).isEmpty());
		assertTrue(edgeRepo.findByGraphId(draft.get().getId()).isEmpty());
	}

	@Test
	public void cloneEntityPipelineTest(){
		var syncariConnector =connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		schemaService.activateMapping(sfdcConnector);
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		coreAccount = schemaService.getEntity(coreAccount.getId());

		ClonePipelineEntityDef invalidReq1 = new ClonePipelineEntityDef("", "dplay", "dplay", "desc", Set.of(), true);
		try {
			mappingGraphService.cloneEntityGraph(coreAccount.getId(), invalidReq1);
			fail();
		} catch (RuntimeException e){
			assertEquals("Api Name is mandatory", e.getMessage());
		}

		invalidReq1 = new ClonePipelineEntityDef("dplay", "", "dplay", "desc", Set.of(), true);
		try {
			mappingGraphService.cloneEntityGraph(coreAccount.getId(), invalidReq1);
			fail();
		} catch (RuntimeException e){
			assertEquals("Display Name is mandatory", e.getMessage());
		}

		ClonePipelineEntityDef validReq = new ClonePipelineEntityDef("dplay", "dplay", "dplay", "desc", Set.of(), false);
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		Optional<MappingGraph> srcGraphExists = mappingGraphService.retrieveEntityGraph(syncariEntity.getId());

		assertTrue(srcGraphExists.isPresent());
		MappingGraph clonedGraph = mappingGraphService.cloneEntityGraph(syncariEntity.getId(), validReq);
		MappingGraph srcGraph = srcGraphExists.get();

		assertTrue(clonedGraph.getCoreNode().getEntityDefinitionId().isPresent());
		String clonedEntityId = clonedGraph.getCoreNode().getEntityDefinitionId().get();
		Map<String, String> srcMap = new HashMap<>();
		Map<String, String> clonedMap = new HashMap<>();

		EntityDefinition clonedEntity = schemaService.getEntity(clonedEntityId);
		assertEquals(clonedGraph.getCoreNode().getApiName(), validReq.getApiName());

		Map<String, AttributeDefinition> clonedAttrMap = new HashMap<>();
		for(AttributeDefinition attr: clonedEntity.getAttributes()){
			clonedAttrMap.put(attr.getApiName(), attr);
		}
		for (AttributeDefinition srcAttr: syncariEntity.getAttributes()){
			assertTrue(clonedAttrMap.containsKey(srcAttr.getApiName()));
			AttributeDefinition newAttr = clonedAttrMap.get(srcAttr.getApiName());
			assertEquals(newAttr.getDisplayName(), srcAttr.getDisplayName());
			assertNotEquals(newAttr.getId(), srcAttr.getId());
			assertEquals(newAttr.getDataStoreName(), srcAttr.getDataStoreName());
		}
		for (MappingNode n : srcGraph.getNodes()){
			if (!n.isCoreNode())
				srcMap.put(n.getApiName(), n.getId());
		}
		for (MappingNode n : clonedGraph.getNodes()){
			if (!n.isCoreNode())
				clonedMap.put(n.getApiName(), n.getId());
		}

		assertEquals(srcMap.size(), clonedMap.size());
		for (String apiName: srcMap.keySet()){
			assertTrue(clonedMap.containsKey(apiName));
		}
		assertEquals(srcGraph.getEdges().size(), clonedGraph.getEdges().size());
	}

	@Test
	public void createDraftWhenExistingDraftExists(){
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId("entityId"));
		mappingGraph.setTargetId(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
		mappingGraphRepo.save(mappingGraph);

		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));

		var coreNode = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(syncariEntity))
				.setMappingGraphId(mappingGraph.getId()));

		var node1 = nodeRepo.save(new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(mappingGraph.getId()));

		var node2 = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(mappingGraph.getId()));

		var edge1 = edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.any())
				.setOutput(OutputPort.any()).setDestinationStage(node2).setSourceStage(node1));

		assertEquals(DraftStatus.NEW, mappingGraph.getDraftStatus());

		var approved = mappingGraphService.approveDraft(mappingGraph);
		approved = mappingGraphService.retrieve(approved.getId()).get();
		assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
		var newDraft = mappingGraphService.createDraftFor(approved);
		assertEquals(DraftStatus.NEW, newDraft.getDraftStatus());

		try{
			mappingGraphService.createDraftFor(approved);
			fail();
		} catch (RuntimeException e){
			assertEquals("Draft for graph Account Map already exists", e.getMessage());
		}
	}

	@Test
	public void graphLifeCycle() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());

		assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());

		var approved = mappingGraphService.approveDraft(defaultEntityGraph);
		approved = mappingGraphService.retrieve(approved.getId()).get();
		assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());

		var newDraft = mappingGraphService.createDraftFor(approved);

		assertEquals(DraftStatus.NEW, newDraft.getDraftStatus());

		assertTrue(mappingGraphService.findDraft(approved).isPresent());
		mappingGraphService.discardDraft(newDraft);
		assertFalse(mappingGraphService.hasDraft(approved));
	}

	@Test
	public void approvingEntityGraphApprovesFieldGraphs() {
		notificationRepo.reset();
		EntityDefinition syncariEntity = entityProxyRepo
				.findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());

		AttributeDefinition attributeDefinition = attributeProxyRepo.findByEntityId(syncariEntity.getId()).get(0);
		var draftAttributeGraph =mappingGraphService.createDefaultAttributeGraph(attributeDefinition.getId());
		assertEquals(DraftStatus.NEW, draftAttributeGraph.getDraftStatus());

		int notificationCount = notificationRepo.findAll().size();
		mappingGraphService.approveDraft(defaultEntityGraph);
		var retrievedEntityGraph = mappingGraphService.retrieveEntityGraph(syncariEntity.getId()).get();
		var retrievedAttributeGraph = mappingGraphService.retrieveAttributeGraph(attributeDefinition.getId()).get();

		assertEquals(DraftStatus.APPROVED, retrievedEntityGraph.getDraftStatus());
		assertEquals(DraftStatus.APPROVED, retrievedAttributeGraph.getDraftStatus());
		assertTrue(notificationRepo.findAll().size() > notificationCount);
	}

	@Ignore
	@Test
    public void approvingGraphMultipleTimes() {
        EntityDefinition syncariEntity = entityProxyRepo
                .findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
        MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
        assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());
		// Lock the graph using LockRepo
		String lockId = "entity_" + defaultEntityGraph.getTargetId();
		String lockOwner = "test_approvingGraphMultipleTimes";
		lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));
        try {
            mappingGraphService.approveDraft(defaultEntityGraph);
        } catch (Exception e) {
            assertEquals(e.getMessage(), "This draft is currently being approved. Please wait for it to complete.");
		} finally {
			lockRepo.unlock(lockId, lockOwner);
        }
    }

    @Test
	public void creatingDraftGraphTwoTimes() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
		MappingGraph approved = mappingGraphService.approveDraft(defaultEntityGraph);
		assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
		try {
			mappingGraphService.createDraftFor(approved);
			mappingGraphService.createDraftFor(approved);
			fail();
		} catch (Exception e) {
			assertEquals(e.getMessage(), String.format("Draft for graph %s already exists", approved.getName()));
		}
	}

	@Test
	public void creatingDraftGraphMultipleTimes() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
		MappingGraph approved = mappingGraphService.approveDraft(defaultEntityGraph);
		assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
		var lockId = "createDraftFor_"+approved.getTargetId();
		var lockOwner = "createDraftFor_"+ UUID.randomUUID().toString();
		lockRepo.lock(lockId, lockOwner, Duration.ofSeconds(5));
		try {
			mappingGraphService.createDraftFor(approved);
			fail();
		} catch (Exception e) {
			assertEquals(e.getMessage(), String.format("There is draft currently being created for %s. Please wait for it to complete.", approved.getName()));
		}finally {
			lockRepo.unlock(lockId, lockOwner);
		}
	}
	
	@Test
	public void creatingDraftLayout() {
		approvingEntityGraphApprovesFieldGraphs();
		EntityDefinition syncariEntity = entityProxyRepo
				.findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
		var approved = mappingGraphService.retrieveEntityGraph(syncariEntity.getId()).get();
		MappingGraph draft = mappingGraphService.createDraftFor(approved);

		int i = 0;
		for (int j = 0; j < approved.getEdges().size(); j++) {
			Edge e = approved.getEdges().get(i);
			Optional<Layout> approvedLayout = layoutService.findEdgeLayout(e.getId());
			Optional<Layout> draftLayout = layoutService.findEdgeLayout(draft.getEdges().get(i).getId());
			i++;
			assertEquals(approvedLayout.get().getLayoutProperties().get("srcAnchor"), draftLayout.get().getLayoutProperties().get("srcAnchor"));
			assertEquals(approvedLayout.get().getLayoutProperties().get("destAnchor"), draftLayout.get().getLayoutProperties().get("destAnchor"));
		}
	}

	@Test
	public void creatingDraftGraphParallelThreads() throws InterruptedException {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
		mappingGraphService.approveDraft(defaultEntityGraph);
		MappingGraph approved = mappingGraphService.retrieve(defaultEntityGraph.getId()).get();
		assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());

		var user = SyncariContext.getUser();
		var org = SyncariContext.getOrganziation();
		var instance = SyncariContext.getInstance();

		List<Thread> processors = IntStream.range(0, 2).mapToObj(i -> new Thread(() -> {
			SyncariContext.setUser(user);
			SyncariContext.setInstance(instance);
			SyncariContext.setOrganziation(org);
			System.out.println("Processor Thread# "+Thread.currentThread().getName());
			mappingGraphService.createDraftFor(approved);
			SyncariContext.resetAll();
		}
		)).collect(Collectors.toList());
		processors.parallelStream().forEach(t -> t.start());
		processors.forEach(t -> {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});

		SyncariContext.setUser(user);
		SyncariContext.setInstance(instance);
		SyncariContext.setOrganziation(org);

		// successful call means there are no duplicates and only one draft is created
		assertTrue(mappingGraphService.findDraft(approved).isPresent());
	}
    
    @Test
    public void approvingGraphFailureHandledGracefully() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
        assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());
        MappingGraph approved = mappingGraphService.approveDraft(defaultEntityGraph);
		approved = mappingGraphService.retrieve(approved.getId()).get();
        assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
        defaultEntityGraph = mappingGraphService.createDraftFor(approved);

        try {
            StreamService mockStreamService = mock(StreamService.class);
            when(mockStreamService.getOrCreateReadyStream(any())).thenThrow(RuntimeException.class);
            mappingGraphService.setStreamService(mockStreamService);
            mappingGraphService.approveDraft(defaultEntityGraph);
            fail();
        } catch (Exception e) {
			Optional<MappingGraph> mappingGraph =  mappingGraphService.retrieve(defaultEntityGraph.getId());
            assertTrue(mappingGraph.isPresent());
            assertEquals(DraftStatus.ARCHIVED, mappingGraph.get().getDraftStatus());
			assertTrue(mappingGraph.get().getName().contains("DELETED"));
        } finally {
        }
    }
	
    @Ignore
	@Test
	public void approvingEntityGraphTwiceErrorsOut() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
	    assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());
		// Lock the graph using LockRepo
		String lockId = "entity_" + defaultEntityGraph.getTargetId();
		String lockOwner = "test_approvingEntityGraphTwiceErrorsOut";
		lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));

	    try {
	        mappingGraphService.approveDraft(defaultEntityGraph);
        } catch (Exception e) {
            assertEquals("This draft is currently being approved. Please wait for it to complete.", e.getMessage());
        }

		// Unlock the graph using LockRepo
		lockRepo.unlock(lockId, lockOwner);
        mappingGraphService.approveDraft(defaultEntityGraph);
        var retrievedEntityGraph = mappingGraphService.retrieveEntityGraph(syncariEntity.getId()).get();
        assertEquals(DraftStatus.APPROVED, retrievedEntityGraph.getDraftStatus());
	}

	@Test
	public void reapprovingEntityGraphDeletesRemovedAttributeGraphs() {

		EntityDefinition syncariEntity = entityProxyRepo
				.findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());

		AttributeDefinition attributeDefinition1 = attributeProxyRepo.findByEntityId(syncariEntity.getId()).get(0);
		AttributeDefinition attributeDefinition2 = attributeProxyRepo.findByEntityId(syncariEntity.getId()).get(1);
		var draftAttributeGraph1 =mappingGraphService.createDefaultAttributeGraph(attributeDefinition1.getId());
		var draftAttributeGraph2 =mappingGraphService.createDefaultAttributeGraph(attributeDefinition2.getId());
		assertEquals(DraftStatus.NEW, draftAttributeGraph1.getDraftStatus());
		assertEquals(DraftStatus.NEW, draftAttributeGraph2.getDraftStatus());

		MappingGraph approved = mappingGraphService.approveDraft(defaultEntityGraph);
		var retrievedEntityGraph = mappingGraphService.retrieveEntityGraph(syncariEntity.getId()).get();
		var retrievedAttributeGraph1 = mappingGraphService.retrieveAttributeGraph(attributeDefinition1.getId()).get();
		var retrievedAttributeGraph2 = mappingGraphService.retrieveAttributeGraph(attributeDefinition1.getId()).get();

		assertEquals(DraftStatus.APPROVED, retrievedEntityGraph.getDraftStatus());
		assertEquals(DraftStatus.APPROVED, retrievedAttributeGraph1.getDraftStatus());
		assertEquals(DraftStatus.APPROVED, retrievedAttributeGraph2.getDraftStatus());

		MappingGraph upserted = mappingGraphService.createDraftFor(approved);
		List<MappingGraph> attributeGraphs = mappingGraphService.retrieveDraftAttributeGraphs(upserted.getId());
		assertEquals(2, attributeGraphs.size());
		mappingGraphService.delete(attributeGraphs.get(0));
		attributeGraphs = mappingGraphService.retrieveDraftAttributeGraphs(upserted.getId());
		assertEquals(1, attributeGraphs.size());
		// reapprove graph
		MappingGraph approved2 = mappingGraphService.approveDraft(upserted);
		retrievedEntityGraph = mappingGraphService.retrieveEntityGraph(syncariEntity.getId()).get();
		var approvedAttributeGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(retrievedEntityGraph.getId());
		assertEquals(1, attributeGraphs.size());

		assertTrue(nodeRepo.findByGraphId(upserted.getId()).isEmpty());
		assertTrue(edgeRepo.findByGraphId(upserted.getId()).isEmpty());
		attributeGraphs.forEach(a -> {
			assertTrue(nodeRepo.findByGraphId(a.getId()).isEmpty());
			assertTrue(edgeRepo.findByGraphId(a.getId()).isEmpty());
		});

		upserted = mappingGraphService.createDraftFor(retrievedEntityGraph);
		approvedAttributeGraphs = mappingGraphService.retrieveDraftAttributeGraphs(upserted.getId());
		assertEquals(1, approvedAttributeGraphs.size());
	}

	@Test
	public void approvingEntityGraphWithNoAttributeGraphs() {

		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());

		try {
			mappingGraphService.approveDraft(defaultEntityGraph);
			fail();
		} catch (Exception e){
			assertEquals(String.format("Cannot publish pipeline for entity %s. There are no draft attributes pipeline in draft.", syncariEntity.getDisplayName()),
					e.getMessage());
		}
		// create attribute graph and approve
		var attribute = syncariEntity.getAttributes().get(0);
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(attribute.getId());
		var approved = mappingGraphService.approveDraft(defaultEntityGraph);

		var newEntityDraft = mappingGraphService.createDraftFor(approved);
		var newAttribDraft = mappingGraphService.retrieveDraftAttributeGraph(attribute.getId()).get();
		mappingGraphRepo.delete(newAttribDraft);
		// attribute draft is removed, approval should fail again
		try {
			mappingGraphService.approveDraft(newEntityDraft);
			fail();
		} catch (Exception e){
			assertEquals(String.format("Cannot publish pipeline for entity %s. There are no draft attributes pipeline in draft.", syncariEntity.getDisplayName()),
					e.getMessage());
		}
	}

	@Test
	public void retrieveGraphPopulatesEdgesAndNodes() {
		MappingGraph mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId("entityId"));

		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));

		var node1 = nodeRepo.save(new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(mappingGraph.getId()));

		var node2 = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(mappingGraph.getId()));

		var edge1 = edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.any())
				.setOutput(OutputPort.any()).setDestinationStage(node2).setSourceStage(node1));

		var retrieved = mappingGraphService.retrieve(mappingGraph.getId()).orElseThrow();

		assertEquals(List.of(edge1), retrieved.getEdges());
		assertEquals(List.of(node1, node2), retrieved.getNodes());
	}

	@Test
	public void upsertGraph() {
		EntityDefinition syncariEntity = entityProxyRepo
				.findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
		MappingGraph mappingGraph = new MappingGraph().setName("Account Map").setScope(Scope.ENTITY)
				.setTargetId(syncariEntity.getId());
		mappingGraph.setId(ObjectId.get().toHexString());
		var mappingNode = new MappingNode().setName(syncariEntity.getDisplayName()).setApiName(syncariEntity.getDisplayName())
		        .setScope(Scope.ENTITY)
				.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(syncariEntity));
		mappingNode.setId(ObjectId.get().toHexString());
		mappingGraph.setNodes(List.of(mappingNode));

		var saved = mappingGraphService.upsertGraph(mappingGraph, Optional.empty(), Optional.empty(),
				MappingNodeType.CORE_ENTITY, null);
		var retrieved = mappingGraphService.retrieve(saved.getId()).orElseThrow();
		retrieved.setName("New Name!");
		var resaved = mappingGraphService.upsertGraph(retrieved, Optional.of(saved), Optional.empty(),
				MappingNodeType.CORE_ENTITY, null);
		retrieved = mappingGraphService.retrieve(saved.getId()).orElseThrow();
		assertEquals("New Name!", retrieved.getName());
	}

	@Test
	public void updateStreamState() {
		var originalResyncService = mappingGraphService.resyncService;
		try {
			EntityDefinition syncariEntity = entityProxyRepo
					.findByConnectorId(connectorService.getSyncariConnector().getId()).get(0);

			EntityDefinition sfdcAccount = new EntityDefinition().setApiName("account").setConnectorId("connector1");
			sfdcAccount.setId(ObjectId.get().toHexString());

			EntityDefinition hubspotCompany = new EntityDefinition().setApiName("company").setConnectorId("connector2");
			hubspotCompany.setId(ObjectId.get().toHexString());

			MappingGraph mappingGraph = new MappingGraph().setName("Account Map").setScope(Scope.ENTITY)
					.setTargetId(syncariEntity.getId());
			mappingGraph.setId(ObjectId.get().toHexString());
			var mappingNode = new MappingNode().setName(syncariEntity.getDisplayName()).setApiName(syncariEntity.getDisplayName())
					.setScope(Scope.ENTITY)
					.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(syncariEntity));
			mappingNode.setId(ObjectId.get().toHexString());


			var source1 = new MappingNode().setName("sfdc account").setApiName("account")
					.setScope(Scope.ENTITY)
					.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount));
			var source2 = new MappingNode().setName("hubspot company").setApiName("company")
					.setScope(Scope.ENTITY)
					.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(hubspotCompany));
			mappingGraph.setNodes(List.of(mappingNode, source1, source2));
			Edge edge1 = new Edge().setSourceStage(source1).setDestinationStage(mappingNode).setInput(InputPort.any()).setOutput(OutputPort.any());
			Edge edge2 = new Edge().setSourceStage(source2).setDestinationStage(mappingNode).setInput(InputPort.any()).setOutput(OutputPort.any());
			mappingGraph.setEdges(List.of(edge1, edge2));
			List<SyncDetail> upstreamWatermarks = watermarkService.getUpstreamWatermarks(syncariEntity.getApiName(), List.of(sfdcAccount.getId(), hubspotCompany.getId()));
			assertTrue(upstreamWatermarks.isEmpty());
			long now = System.currentTimeMillis();
			Optional<SyncStream> streamState = streamRepo.findByGraphId(mappingGraph.getId());
			assertTrue(streamState.isEmpty());

			// Case 1: Incremental Sync without existing watermark - expected: new watermarks should be created for both source entities
			mappingGraphService.updateStreamState(mappingGraph, Optional.of(syncariEntity), false);
			streamState = streamRepo.findByGraphId(mappingGraph.getId());
			assertFalse(streamState.isEmpty());
			assertEquals(SyncStream.Status.READY, streamState.get().getStatus());

			upstreamWatermarks = watermarkService.getUpstreamWatermarks(syncariEntity.getApiName(), List.of(sfdcAccount.getId(), hubspotCompany.getId()));

			assertEquals(2, upstreamWatermarks.size());
			assertTrue(upstreamWatermarks.get(0).getWatermark().getStart() >= now);
			assertTrue(upstreamWatermarks.get(0).getWatermark().getEnd() >= now);
			assertFalse(upstreamWatermarks.get(0).getWatermark().isInitial());
			assertTrue(upstreamWatermarks.get(1).getWatermark().getStart() >= now);
			assertTrue(upstreamWatermarks.get(1).getWatermark().getEnd() >= now);
			assertFalse(upstreamWatermarks.get(1).getWatermark().isInitial());

			// Case 2: Incremental Sync with existing watermark - expected: no change to existing watermarks
			streamState.get().setStatus(SyncStream.Status.RUNNING);
			streamRepo.save(streamState.get());

			mappingGraphService.updateStreamState(mappingGraph, Optional.of(syncariEntity), false);
			var newWatermarks = watermarkService.getUpstreamWatermarks(syncariEntity.getApiName(), List.of(sfdcAccount.getId(), hubspotCompany.getId()));
			//no state change on stream
			streamState = streamRepo.findByGraphId(mappingGraph.getId());
			assertFalse(streamState.isEmpty());
			assertEquals(SyncStream.Status.RUNNING, streamState.get().getStatus());
			assertEquals(2, upstreamWatermarks.size());
			//No changes on second update, because watermarks exist now
			assertEquals(upstreamWatermarks.get(0).getWatermark().getStart(), newWatermarks.get(0).getWatermark().getStart());
			assertEquals(upstreamWatermarks.get(0).getWatermark().getEnd(), newWatermarks.get(0).getWatermark().getEnd());
			assertEquals(upstreamWatermarks.get(0).getWatermark().isInitial(), newWatermarks.get(0).getWatermark().isInitial());

			assertEquals(upstreamWatermarks.get(1).getWatermark().getStart(), newWatermarks.get(1).getWatermark().getStart());
			assertEquals(upstreamWatermarks.get(1).getWatermark().getEnd(), newWatermarks.get(1).getWatermark().getEnd());
			assertEquals(upstreamWatermarks.get(1).getWatermark().isInitial(), newWatermarks.get(1).getWatermark().isInitial());


			streamState.get().setStatus(SyncStream.Status.STOPPED);
			streamRepo.save(streamState.get());

			mappingGraphService.updateStreamState(mappingGraph, Optional.of(syncariEntity), false);
			streamState = streamRepo.findByGraphId(mappingGraph.getId());
			assertFalse(streamState.isEmpty());
			assertEquals(SyncStream.Status.STOPPED, streamState.get().getStatus());

			// Case 3: Incremental sync new source added and published - expected watermark created for new source and no change to existing watermark
			syncDetailRepo.delete(newWatermarks.get(1)); // delete hubspot watermark
			upstreamWatermarks = watermarkService.getUpstreamWatermarks(syncariEntity.getApiName(), List.of(sfdcAccount.getId(), hubspotCompany.getId()));
			assertEquals(1, upstreamWatermarks.size());
			mappingGraphService.updateStreamState(mappingGraph, Optional.of(syncariEntity), false);
			newWatermarks = watermarkService.getUpstreamWatermarks(syncariEntity.getApiName(), List.of(sfdcAccount.getId(), hubspotCompany.getId()));
			assertEquals(2, newWatermarks.size());

		}finally {
			mappingGraphService.setResyncService(originalResyncService);
		}

	}

	@Test
	public void updateStreamStateWithHistoricSync() {

		var original=mappingGraphService.resyncService;
    	try {
			EntityDefinition syncariEntity = entityProxyRepo
					.findByConnectorId(connectorService.getSyncariConnector().getId()).get(0);

			EntityDefinition sfdcAccount = new EntityDefinition().setApiName("account").setConnectorId("connector1");
			sfdcAccount.setId(ObjectId.get().toHexString());

			EntityDefinition hubspotCompany = new EntityDefinition().setApiName("company").setConnectorId("connector2");
			hubspotCompany.setId(ObjectId.get().toHexString());

			MappingGraph mappingGraph = new MappingGraph().setName("Account Map").setScope(Scope.ENTITY)
					.setTargetId(syncariEntity.getId());
			mappingGraph.setId(ObjectId.get().toHexString());
			var mappingNode = new MappingNode().setName(syncariEntity.getDisplayName()).setApiName(syncariEntity.getDisplayName())
					.setScope(Scope.ENTITY)
					.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(syncariEntity));
			mappingNode.setId(ObjectId.get().toHexString());


			var source1 = new MappingNode().setName("sfdc account").setApiName("account")
					.setScope(Scope.ENTITY)
					.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount));
			var source2 = new MappingNode().setName("hubspot company").setApiName("company")
					.setScope(Scope.ENTITY)
					.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(hubspotCompany));
			mappingGraph.setNodes(List.of(mappingNode, source1, source2));
			Edge edge1 = new Edge().setSourceStage(source1).setDestinationStage(mappingNode).setInput(InputPort.any()).setOutput(OutputPort.any());
			Edge edge2 = new Edge().setSourceStage(source2).setDestinationStage(mappingNode).setInput(InputPort.any()).setOutput(OutputPort.any());
			mappingGraph.setEdges(List.of(edge1, edge2));
			List<SyncDetail> upstreamWatermarks = watermarkService.getUpstreamWatermarks(syncariEntity.getApiName(), List.of(sfdcAccount.getId(), hubspotCompany.getId()));
			assertTrue(upstreamWatermarks.isEmpty());
			Optional<SyncStream> streamState = streamRepo.findByGraphId(mappingGraph.getId());
			assertTrue(streamState.isEmpty());

			// Case 1: Test to check resync is issued in INITIALSYNC mode if new pipeline is published and no watermarks exists
			ResyncService mockResyncService = mock(ResyncService.class);
			ResyncDetail resync = new ResyncDetail().setSyncariEntityId("syncariId").setSyncariEntityName("syncariEntityName")
				.setStartTime(Instant.EPOCH).setEndTime(Instant.now()).setStatus(ResyncStatus.NEW).setMode(ResyncDetail.Mode.INITIALSYNC);
			resync.setId("123");
			when(mockResyncService.createResyncRequest(eq(syncariEntity.getId()), eq(List.of(sfdcAccount.getId(), hubspotCompany.getId())), eq(Instant.EPOCH), any(), eq(true))).thenReturn(resync);
			mappingGraphService.setResyncService(mockResyncService);
			mappingGraphService.updateStreamState(mappingGraph, Optional.of(syncariEntity), true);
			verify(mockResyncService).createResyncRequest(eq(syncariEntity.getId()), eq(List.of(sfdcAccount.getId(), hubspotCompany.getId())), eq(Instant.EPOCH), any(), eq(true));
			streamState = streamRepo.findByGraphId(mappingGraph.getId());

			// Case 2: create watermarks and then updateStream to have resync issued on the stream
			mockResyncService = mock(ResyncService.class);
			resync = new ResyncDetail().setSyncariEntityId("syncariId").setSyncariEntityName("syncariEntityName")
				.setStartTime(Instant.EPOCH).setEndTime(Instant.now()).setStatus(ResyncStatus.NEW).setMode(ResyncDetail.Mode.RESYNC);
			resync.setId("123");
			when(mockResyncService.createResyncRequest(eq(syncariEntity.getId()), eq(List.of(sfdcAccount.getId(), hubspotCompany.getId())), eq(Instant.EPOCH), any(), eq(false))).thenReturn(resync);
			mappingGraphService.setResyncService(mockResyncService);
			Watermark w = new Watermark().setStart(Instant.EPOCH.toEpochMilli()).setEnd(Instant.now().toEpochMilli()).setInitial(false);
			syncDetailRepo.save(new SyncDetail(sfdcAccount.getId(), syncariEntity.getApiName(), w));
			syncDetailRepo.save(new SyncDetail(hubspotCompany.getId(), syncariEntity.getApiName(), w));
			mappingGraphService.updateStreamState(mappingGraph, Optional.of(syncariEntity), true);
			verify(mockResyncService).createResyncRequest(eq(syncariEntity.getId()), eq(List.of(sfdcAccount.getId(), hubspotCompany.getId())), eq(Instant.EPOCH), any(), eq(false));

			// Case 3: New Resync not created with there is an existing in-progress resync
			mockResyncService = mock(ResyncService.class);
			resync = new ResyncDetail().setSyncariEntityId(syncariEntity.getId()).setSyncariEntityName(syncariEntity.getApiName())
				.setStartTime(Instant.EPOCH).setEndTime(Instant.now()).setStatus(ResyncStatus.NEW).setMode(ResyncDetail.Mode.RESYNC);
			resync.setId("123");
			when(mockResyncService.updateResyncSources(any(), any())).thenReturn(resync);
			when(mockResyncService.findInProgressResyncBySyncariEntityId(syncariEntity.getId())).thenReturn(Optional.of(resync));
			when(mockResyncService.createResyncRequest(eq(syncariEntity.getId()), eq(List.of(sfdcAccount.getId(), hubspotCompany.getId())), eq(Instant.EPOCH), any(), eq(true))).thenReturn(resync);
			mappingGraphService.setResyncService(mockResyncService);
			mappingGraphService.updateStreamState(mappingGraph, Optional.of(syncariEntity), true);
			verify(mockResyncService).findInProgressResyncBySyncariEntityId(eq(syncariEntity.getId()));
			verify(mockResyncService, never()).createResyncRequest(eq(syncariEntity.getId()), eq(List.of(sfdcAccount.getId(), hubspotCompany.getId())), eq(Instant.EPOCH), any(), eq(false));
			verify(mockResyncService).updateResyncSources(any(), any());
		}finally {
			mappingGraphService.setResyncService(original);;
		}
	}

	@Test
	public void upsertEntityGraphRemovesDanglingAttributes() {
		var syncariConnector =connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();

		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		MappingGraph attributeGraph = mappingGraphService.retrieveDraftAttributeGraphs(accountGraph.getId()).get(0);

		assertEquals(1, attributeGraph.getSources().count());
		assertEquals(1, attributeGraph.getSinks().count());
		assertEquals(2, attributeGraph.getEdges().size());
		accountGraph.removeSource(sfdcAccount.getId());
		MappingGraph newGraph = mappingGraphService.upsertEntityGraph(accountGraph);
		MappingGraph attrGraphWithoutSource = mappingGraphService.retrieveDraftAttributeGraphs(accountGraph.getId()).get(0);
		assertEquals(0, attrGraphWithoutSource.getSources().count());
		assertEquals(1, attrGraphWithoutSource.getSinks().count());
		assertEquals(1, attrGraphWithoutSource.getEdges().size());
		newGraph.removeSink(sfdcAccount.getId());
		mappingGraphService.upsertEntityGraph(newGraph);
		List<MappingGraph> attrGraphWithoutSink = mappingGraphService.retrieveDraftAttributeGraphs(accountGraph.getId());
		assertEquals(0, attrGraphWithoutSink.size());
	}

	@Test
	public void getConnectedSourcesAndSinks() {
		var syncariConnector =connectorService.getSyncariConnector();
		//needed to activate & create mappings
		createConnector();
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();

		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		MappingNode danglingSource = new MappingNode().setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(new EntityDefinition()));
		danglingSource.setId(ObjectId.get().toHexString());
		MappingNode danglingSink = new MappingNode().setConfiguration(new EntitySinkNodeConfig().setEntityDefinition(new EntityDefinition()));
		danglingSink.setId(ObjectId.get().toHexString());
		accountGraph.addNode(danglingSource);
		accountGraph.addNode(danglingSink);
		assertEquals(2, accountGraph.getSources().count());
		assertEquals(1, accountGraph.getConnectedSources().count());
		assertEquals(2, accountGraph.getSinks().count());
		assertEquals(1, accountGraph.getConnectedSources().count());

	}

//	@Test
	public void initializeEntityGraph() {
		long nodesBefore = mappingGraphRepo.count();
		long edgesBefore = edgeRepo.count();

		EntityDefinition syncariEntity = entityProxyRepo
				.findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
		syncariEntity.getAttributes().addAll(attributeProxyRepo.findByEntityId(syncariEntity.getId()));
		mappingGraphService.initializeEntityGraph(syncariEntity, syncariEntity);

		long nodesAfter = mappingGraphRepo.count();
		assertTrue(nodesAfter > nodesBefore);
		long edgesAfter = edgeRepo.count();
		assertTrue(edgesAfter > edgesBefore);
	}

	@Test
	public void testInitializeEntityGraphForFieldNamesStartingOrEndingWith_() {
		var syncariConnector = connectorService.getSyncariConnector();
		var testConnector = getTestConnector();

		EntityDefinition synapseEntity = schemaService.getEntity(testConnector.getId(), "contact");
		var synapseField = SchemaHelper.createAttribute("_synapseField_", StringType.VALUE, synapseEntity.getId());
		synapseEntity.addField(synapseField);

		entityProxyRepo.save(synapseEntity);
		attributeProxyRepo.saveAll(List.of(synapseField));

		EntityDefinition syncariEntity = SchemaHelper.createEntityDef("coreContact", "contact", syncariConnector);
		var syncariField = SchemaHelper.createAttribute("synapseField", StringType.VALUE, syncariEntity.getId());
		syncariEntity.addField(syncariField);

		entityProxyRepo.save(syncariEntity);
		attributeProxyRepo.saveAll(List.of(syncariField));

		var mappingGraph = mappingGraphService.initializeEntityGraph(syncariEntity, synapseEntity);

		assertTrue(mappingGraph.isPresent());
		Map<String, EntityDefinition> entityMap = mappingGraphService.getConnectedSourceEntityMap(mappingGraph.get());
		assertEquals(1, entityMap.size());
		for (Map.Entry<String, EntityDefinition> entry : entityMap.entrySet()) {
			assertEquals(10, entry.getValue().getAttributes().size());
			assertEquals("_synapseField_", entry.getValue().getAttribute(synapseField.getId()).getApiName());
		}
		assertTrue(schemaService.getEntity(syncariEntity.getId()).getField("synapseField").isPresent());
	}

	@Test
	public void portEqualityExcludesNumConnections() {
		assertTrue(OutputPort.any().equals(OutputPort.many()));
		assertTrue(OutputPort.of(StringType.VALUE).equals(new OutputPort(StringType.VALUE, 200)));
		assertFalse(OutputPort.of(new DatetimeType()).equals(OutputPort.of(StringType.VALUE)));
		assertTrue(InputPort.any().equals(InputPort.many()));
		assertTrue(InputPort.of(StringType.VALUE).equals(new InputPort(StringType.VALUE, 200)));
		assertFalse(InputPort.of(new DatetimeType()).equals(InputPort.of(StringType.VALUE)));
	}

	@Test
	public void findEntityGraphsThatHaveGivenNodeAsSourceOrSink(){
		var syncariConnector =connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();


		assertTrue(mappingGraphService.findEntityGraphsWithSourceOrSink(coreAccount.getId(),sfdcAccount.getConnectorId()).isEmpty());

		var sfdcAccountNode = nodeRepo.save(new MappingNode()
				.setApiName(sfdcAccount.getApiName())
				.setName(sfdcAccount.getApiName()).setScope(Scope.ENTITY)
				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(accountGraph.getId()));


		var coreAccountNode = accountGraph.getCoreNode();
		var sfdcToCoreEdge = edgeRepo.save(
				new Edge().setGraphId(accountGraph.getId()).setInput(coreAccountNode.getConfiguration().getInputPorts().get(0))
						.setOutput(coreAccountNode.getConfiguration().getOutputPorts().get(0)).setDestinationStage(sfdcAccountNode)
						.setSourceStage(sfdcAccountNode));
		List<MappingGraph> graphsWithSourceOrSink = mappingGraphService.findEntityGraphsWithSourceOrSink(coreAccount.getId(), sfdcAccount.getId());
		assertEquals(1,graphsWithSourceOrSink.size());
		assertEquals(DraftStatus.NEW, graphsWithSourceOrSink.get(0).getDraftStatus());
		graphsWithSourceOrSink = mappingGraphService.findEntityGraphsWithSourceOrSink(sfdcAccount.getId());
		assertEquals(1,graphsWithSourceOrSink.size());
		assertEquals(DraftStatus.NEW, graphsWithSourceOrSink.get(0).getDraftStatus());

		mappingGraphService.approveDraft(accountGraph);
		graphsWithSourceOrSink = mappingGraphService.findEntityGraphsWithSourceOrSink(coreAccount.getId(), sfdcAccount.getId());
		assertEquals(1,graphsWithSourceOrSink.size());
		assertEquals(DraftStatus.APPROVED, graphsWithSourceOrSink.get(0).getDraftStatus());
		graphsWithSourceOrSink = mappingGraphService.findEntityGraphsWithSourceOrSink(sfdcAccount.getId());
		assertEquals(1,graphsWithSourceOrSink.size());
		assertEquals(DraftStatus.APPROVED, graphsWithSourceOrSink.get(0).getDraftStatus());

		mappingGraphService.createDraftFor(accountGraph);
		graphsWithSourceOrSink = mappingGraphService.findEntityGraphsWithSourceOrSink(coreAccount.getId(), sfdcAccount.getId());
		assertEquals(2,graphsWithSourceOrSink.size());
		graphsWithSourceOrSink = mappingGraphService.findEntityGraphsWithSourceOrSink(sfdcAccount.getId());
		assertEquals(2,graphsWithSourceOrSink.size());
	}

	@Test
	public void findAttributeGraphsThatHaveGivenNodeAsSourceOrSink(){
		var syncariConnector =connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(coreAccount.getId());
		var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
		List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());
		var sfdcNameAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
//		MappingGraph accountGraph = mappingGraphService.createDefaultEntityGraph(coreAccount.getId());
//		assertTrue(mappingGraphService.findEntityGraphsWithSourceOrSink(coreAccount.getId(),sfdcAccount.getConnectorId()).isEmpty());
//		var sfdcAccountNode = nodeRepo.save(new MappingNode().setName(sfdcAccount.getApiName()).setScope(Scope.ENTITY)
//				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount))
//				.setMappingGraphId(accountGraph.getId()));
//
//
//		var coreAccountNode = accountGraph.getCoreNode();
//		var sfdcToCoreEdge = edgeRepo.save(
//				new Edge().setGraphId(accountGraph.getId()).setInput(coreAccountNode.getConfiguration().getInputPorts().get(0))
//						.setOutput(coreAccountNode.getConfiguration().getOutputPorts().get(0)).setDestinationStage(sfdcAccountNode)
//						.setSourceStage(sfdcAccountNode));
//
		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		MappingGraph nameGraph = mappingGraphService.retrieveAttributeGraph(coreNameAttrib.getId()).get();

		var sfdcAccountNameNode = nodeRepo.save(new MappingNode()
				.setApiName(sfdcNameAttrib.getApiName())
				.setName(sfdcNameAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcNameAttrib))
				.setMappingGraphId(nameGraph.getId()));


		var coreNameNode = nameGraph.getCoreNode();
		var sfdcToCoreNameEdge = edgeRepo.save(
				new Edge().setGraphId(nameGraph.getId()).setInput(coreNameNode.getConfiguration().getInputPorts().get(0))
						.setOutput(coreNameNode.getConfiguration().getOutputPorts().get(0))
						.setDestinationStage(sfdcAccountNameNode).setSourceStage(sfdcAccountNameNode));

		List<MappingGraph> graphsWithSourceOrSink = mappingGraphService.findAttributeGraphsWithSourceOrSink(coreNameAttrib.getId(), sfdcNameAttrib.getId());
		assertEquals(1,graphsWithSourceOrSink.size());
		graphsWithSourceOrSink = mappingGraphService.findAttributeGraphsWithSourceOrSink(sfdcNameAttrib.getId());
		assertEquals(1,graphsWithSourceOrSink.size());

		mappingGraphService.approveDraft(accountGraph);
		graphsWithSourceOrSink = mappingGraphService.findAttributeGraphsWithSourceOrSink(coreNameAttrib.getId(), sfdcNameAttrib.getId());
		assertEquals(1,graphsWithSourceOrSink.size());
		assertEquals(DraftStatus.APPROVED, graphsWithSourceOrSink.get(0).getDraftStatus());
		graphsWithSourceOrSink = mappingGraphService.findAttributeGraphsWithSourceOrSink(sfdcNameAttrib.getId());
		assertEquals(1,graphsWithSourceOrSink.size());
		assertEquals(DraftStatus.APPROVED, graphsWithSourceOrSink.get(0).getDraftStatus());

		mappingGraphService.createDraftFor(nameGraph);
		graphsWithSourceOrSink = mappingGraphService.findAttributeGraphsWithSourceOrSink(coreNameAttrib.getId(), sfdcNameAttrib.getId());
		assertEquals(2,graphsWithSourceOrSink.size());
		graphsWithSourceOrSink = mappingGraphService.findAttributeGraphsWithSourceOrSink(sfdcNameAttrib.getId());
		assertEquals(2,graphsWithSourceOrSink.size());
	}

	@Test
	public void validateActivateMapping() {
		var syncariConnector = connectorService.getSyncariConnector();
		var sfdcConnector = createSFDCConnector();
		schemaService.activateMapping(sfdcConnector);
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		EntityDefinition coreDocument= entityProxyRepo.findEntityByConnectorIdAndApiName(syncariConnector.getId(),"document").get();
		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		assertNotNull(accountGraph);
		Optional<MappingGraph> documentGraph = mappingGraphService.retrieveEntityGraph(coreDocument.getId());
		assertFalse(documentGraph.isPresent());
		Map<String, Set<String>> mappedEntities =  mappingGraphService.getMappedEntities(sfdcConnector.getId());
		assertNotNull(mappedEntities);
		assertEquals(1, mappedEntities.size());
	}

	@Test
	public void getMappedEntities_DanglingNodeRefsSkipped() {
		var syncariConnector = connectorService.getSyncariConnector();
		var sfdcConnector = createSFDCConnector();
		schemaService.activateMapping(sfdcConnector);
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		assertNotNull(accountGraph);
		Map<String, Set<String>> mappedEntities =  mappingGraphService.getMappedEntities(sfdcConnector.getId());
		assertNotNull(mappedEntities);
		assertEquals(1, mappedEntities.size());

		// delete EP mappingGraph and keep nodes and layouts as is
		mappingGraphRepo.delete(accountGraph);
		mappedEntities =  mappingGraphService.getMappedEntities(sfdcConnector.getId());
		assertTrue(mappedEntities.isEmpty());
	}
	
   @Test
    public void validateSinkAttrCanBeUsedOnlyOnce(){
       var syncariConnector =connectorService.getSyncariConnector();
       var sfdcConnector = createConnector();
       schemaService.activateMapping(sfdcConnector);
       EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
       EntityDefinition sfdcAccount= schemaService.getEntity(sfdcConnector.getId(),"Account");

       MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
       MappingGraph attributeGraph = mappingGraphService.retrieveDraftAttributeGraphs(accountGraph.getId()).get(0);
       AttributeSinkNodeConfig sink = attributeGraph.getSinks().findAny().get().getTypedConfiguration();
       AttributeDefinition definition = sfdcAccount.getActiveAttributes().stream().filter(a -> a.getId().equals(sink.getAttributeDefinition().getId())).findFirst().get();
       definition.setWatermarkField(true);
       definition.setIdField(true);
       attributeProxyRepo.save(definition);
       var existingDestNode = attributeGraph.getSinks().findFirst().get();
       MappingNode dest = new MappingNode().setApiName(definition.getApiName()).setScope(Scope.ATTRIBUTE).setConfiguration(
               new AttributeSinkNodeConfig().setAttributeDefinition(definition)).setName(existingDestNode.getName());
       dest.setId(ObjectId.get().toHexString());
       attributeGraph.addNode(dest);
       
        Edge edge = new Edge().setDestinationStage(dest)
                .setSourceStage(attributeGraph.getCoreNode())
                .setInput(InputPort.any())
                .setOutput(OutputPort.any());
       edge.setId(ObjectId.get().toHexString());
       
       attributeGraph.addEdge(edge);
       Map<String, EntityDefinition> sourceEntitiesMap = mappingGraphService.getConnectedSourceEntityMap(accountGraph);
       try {
           mappingGraphService.validateGraph(attributeGraph, coreAccount, sourceEntitiesMap);
           fail();
        } catch (Exception e) {
       	   assertEquals(String.format("Duplicate Destination node '%s' in pipeline %s", existingDestNode.getName(), attributeGraph.getName()), e.getMessage());
        }
       
       // Removing duplicated node validates successfully
       attributeGraph = mappingGraphService.retrieveDraftAttributeGraphs(accountGraph.getId()).get(0);
       mappingGraphService.validateGraph(attributeGraph, coreAccount, sourceEntitiesMap);
       
       // test duplicates across graph
       attributeGraph.addNode(dest);
       attributeGraph.addEdge(edge);
       mappingGraphService.upsertAttributeGraph(attributeGraph);
       try {
           mappingGraphService.validateGraph(accountGraph, coreAccount, sourceEntitiesMap);
           fail();
        } catch (Exception e) {
       		System.out.println(e.getMessage());
       		assertTrue(e.getMessage().contains("Duplicate Destination node"));
        }
    }
	   
	@Test
	public void testOneConnectedGraph() {

		var syncariConnector =connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		schemaService.activateMapping(sfdcConnector);
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		coreAccount = schemaService.getEntity(coreAccount.getId());
		//coreAccount
		//coreAccount.getWatermarkField()
		coreAccount.getField("LastModifiedDate").get().setWatermarkField(true);
		EntityDefinition sfdcAccount= schemaService.getEntity(sfdcConnector.getId(),"Account");

		MappingGraph mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId("entityId"));

		// two simple graphs
		var srcNode1 = nodeRepo.save(new MappingNode().setName("src1").setApiName("src1").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));

		var srcNode2 = nodeRepo.save(new MappingNode().setName("src2").setApiName("src2").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));

		var coreNode = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreAccount))
				.setMappingGraphId(mappingGraph.getId()));

		var destNode = nodeRepo.save(new MappingNode().setName("dest").setApiName("dest").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySinkNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(coreNode)
				.setSourceStage(srcNode1));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(destNode)
				.setSourceStage(coreNode));

		assertValidation(mappingGraph, "Source src2 cannot be dangling in Account Map pipeline");

		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));
		sfdc.getConfig().put("maskCharacter", "*");

		var functionNode = nodeRepo.save(new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(mappingGraph.getId()));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(functionNode)
				.setSourceStage(srcNode2));

		assertValidation(mappingGraph, "Source src2 not connected to core node in Account Map pipeline");

		mappingGraphRepo.delete(mappingGraph);

		// cleanup before the graph is created.
		mappingGraphRepo.findEntityGraph(coreAccount.getId(), DraftStatus.NEW).ifPresent(g -> mappingGraphRepo.delete(g));

		mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(coreAccount.getId()));

		srcNode1 = nodeRepo.save(new MappingNode().setName("src1").setApiName("src1").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));


		coreNode = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreAccount))
				.setMappingGraphId(mappingGraph.getId()));

		destNode = nodeRepo.save(new MappingNode().setName("dest").setApiName("dest").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySinkNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(coreNode)
				.setSourceStage(srcNode1));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(destNode)
				.setSourceStage(coreNode));

		MappingGraph attribGraph = mappingGraphService.createDefaultAttributeGraph(coreAccount.getAttributes().get(0));

		var srcAttrib1 = nodeRepo.save(new MappingNode().setName("srcAttrib1").setApiName("srcAttrib1").setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcAccount.getAttributes().get(0)))
				.setMappingGraphId(attribGraph.getId()));

		var srcAttrib2 = nodeRepo.save(new MappingNode().setName("srcAttrib2").setApiName("srcAttrib2").setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcAccount.getAttributes().get(0)))
				.setMappingGraphId(attribGraph.getId()));

		var coreAttribNode = nodeRepo.save(new MappingNode().setName("coreAttrib").setApiName("coreAttrib").setScope(Scope.ATTRIBUTE)
				.setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(coreAccount.getAttributes().get(0)))
				.setMappingGraphId(attribGraph.getId()));

		var destAttribNode = nodeRepo.save(new MappingNode().setName("destAttrib").setApiName("destAttrib").setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSinkNodeConfig().setAttributeDefinition(sfdcAccount.getAttributes().get(0)))
				.setMappingGraphId(attribGraph.getId()));

		functionNode = nodeRepo.save(new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(attribGraph.getId()));

		edgeRepo.save(new Edge().setGraphId(attribGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(coreAttribNode)
				.setSourceStage(srcAttrib1));

		edgeRepo.save(new Edge().setGraphId(attribGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(destAttribNode)
				.setSourceStage(coreAttribNode));

		edgeRepo.save(new Edge().setGraphId(attribGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(functionNode)
				.setSourceStage(srcAttrib2));


		assertValidation(mappingGraph, String.format("Source srcAttrib1 not connected to core node in About Us pipeline", coreAccount.getAttributes().get(0).getDisplayName()));

		EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1",
				GraphHelper.createConnector("sourceConnector1", "sourceConnectorId1", "sourceConnectorMeta1"));

		EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2",
				GraphHelper.createConnector("sourceConnector2", "sourceConnectorId2", "sourceConnectorMeta2"));

		MappingGraph entityGraph = newGraph(coreAccount)
				.src(srcEntity2, "Source Account2")
				.src(srcEntity1, "Source Account1")
				.function("advancedAttachRecord", "Attach Record", Map.of("attachPredicate", Map.of()))
				.function("filter", "Filter All", Map.of())
				.dest(srcEntity1, "Dest Account")
				.connect("Source Account1", "account")
				.connect("account", "Filter All")
				.connect("Filter All", "Dest Account")
				.connect("Source Account2", "Attach Record")
				.connect("Attach Record", "account").getGraph();

		entityGraph.validate(); // this should work
		
	}

	@Test
	public void graphValidationMandatoryField(){
		var syncariConnector = connectorService.getSyncariConnector();

		EntitySchema entitySchema = new EntitySchema("Account", "Account");
		var nameAttrib = new AttributeSchema("Name","string").setDisplayName("Account Name");
		nameAttrib.setNillable(false);
		entitySchema.addField(nameAttrib);

		var idAttrib = new AttributeSchema("Id","id").setDisplayName("Id");
		idAttrib.setNillable(true);
		idAttrib.setIdField(true);
		entitySchema.addField(idAttrib);

		var descAttrib = new AttributeSchema("Description","string").setDisplayName("Description");
		descAttrib.setNillable(true);
		entitySchema.addField(descAttrib);

		var sfdcConnector = createConnectorWithSchema(List.of(entitySchema));
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		coreAccount = schemaService.getEntity(coreAccount.getId());

		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		//assertValidation(accountGraph, "Set a default value for mandatory destination field sfdc1 Account Name in the Account Name field pipeline");

		// delete account name attribute graph
		var accNameAttrib = coreAccount.getAttributes().stream().filter(a-> a.getApiName().equalsIgnoreCase("Name")).findFirst().get();
		mappingGraphService.discardDraftFieldGraph(accNameAttrib.getId());
		EntityDefinition sfdcAccount = schemaService.getEntity(sfdcConnector.getId(), "Account");
		AttributeDefinition sfdcAccountName = sfdcAccount.getFieldByName("Name");
		sfdcAccountName.setWatermarkField(true);
        attributeProxyRepo.save(sfdcAccountName);
		assertValidation(accountGraph, "No mapping for mandatory field Account Name in Entity Account (sfdc1)");
		//Adding a default value makes the graph valid
		sfdcAccountName.setDefaultValue("Default Account Name");
		attributeProxyRepo.save(sfdcAccountName);
		mappingGraphService.validateGraph(accountGraph.getId());
		//Removinng a default value makes the graph invalid
		sfdcAccountName.setDefaultValue(null);
		attributeProxyRepo.save(sfdcAccountName);
		assertValidation(accountGraph, "No mapping for mandatory field Account Name in Entity Account (sfdc1)");

		//Making the field readonly makes the pipeline valid
		sfdcAccountName.setUpdatable(false);
		attributeProxyRepo.save(sfdcAccountName);
		mappingGraphService.validateGraph(accountGraph.getId());

		//Making the field updatable makes the pipeline invalid
		sfdcAccountName.setUpdatable(true);
		attributeProxyRepo.save(sfdcAccountName);
		assertValidation(accountGraph, "No mapping for mandatory field Account Name in Entity Account (sfdc1)");
	}

	@Test
	public void validateReferenceField(){
	    var syncariConnector = connectorService.getSyncariConnector();
	    
	    EntitySchema entitySchema = new EntitySchema("Account", "Account");
	    var nameAttrib = new AttributeSchema("Name","string").setDisplayName("Account Name");
	    nameAttrib.setNillable(false);
	    entitySchema.addField(nameAttrib);
	    
	    var idAttrib = new AttributeSchema("Id","id").setDisplayName("Id");
	    idAttrib.setNillable(true);
	    idAttrib.setIdField(true);
	    entitySchema.addField(idAttrib);
	    
	    var descAttrib = new AttributeSchema("MasterRecordId","string").setDisplayName("MasterRecordId").setDataType("reference");
	    descAttrib.setNillable(true);
	    entitySchema.addField(descAttrib);
	    
	    var sfdcConnector = createConnectorWithSchema(List.of(entitySchema));
	    EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
	    List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());
	    var sfdcRefAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
	    
	    
	    EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
	    coreAccount = schemaService.getEntity(coreAccount.getId());
	    List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(coreAccount.getId());
	    var coreRefAttr = coreAccountAttribs.stream().filter(a->a.getApiName().equals("MasterRecordId")).findFirst().get();
	    
        MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
        MappingGraph refGraph = mappingGraphService.retrieveAttributeGraph(coreRefAttr.getId()).get();

        // Save sfdc ref node
        var sfdcRefNode = nodeRepo.save(new MappingNode()
                .setApiName(sfdcRefAttrib.getApiName())
                .setName(sfdcRefAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
                .setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcRefAttrib))
                .setMappingGraphId(refGraph.getId()));

        // Save sfdc ref node edge
        var coreRefNode = refGraph.getCoreNode();
        var edge = edgeRepo.save(
                new Edge().setGraphId(refGraph.getId()).setInput(coreRefNode.getConfiguration().getInputPorts().get(0))
                        .setOutput(coreRefNode.getConfiguration().getOutputPorts().get(0))
                        .setDestinationStage(coreRefNode).setSourceStage(sfdcRefNode));

		/*var edge1 = edgeRepo.save(
				new Edge().setGraphId(refGraph.getId()).setInput(coreRefNode.getConfiguration().getInputPorts().get(0))
						.setOutput(coreRefNode.getConfiguration().getOutputPorts().get(0))
						.setDestinationStage(coreRefNode).setSourceStage(sfdcRefNode));*/

        AttributeDefinition wm = sfdcAccountAttribs.get(0).setWatermarkField(true);
        attributeProxyRepo.save(wm);
	    assertValidation(accountGraph, "A non reference field 'Account Name' cannot be mapped to reference field 'Master Record ID'");
	    edgeRepo.delete(edge);
	    nodeRepo.delete(sfdcRefNode);
		//mappingGraphService.retrieveAttributeGraph(coreRefAttr.getId()).get()
	}
	
	@Test
	public void validateReferenceField2(){
	    var syncariConnector = connectorService.getSyncariConnector();
	    
	    EntitySchema entitySchema = new EntitySchema("Account", "Account");
	    var nameAttrib = new AttributeSchema("Name","string").setDisplayName("Account Name");
	    nameAttrib.setNillable(false);
	    entitySchema.addField(nameAttrib);
	    
	    var idAttrib = new AttributeSchema("Id","id").setDisplayName("Id");
	    idAttrib.setNillable(true);
	    idAttrib.setIdField(true);
	    entitySchema.addField(idAttrib);
	    
	    var descAttrib = new AttributeSchema("MasterRecordId","string").setDisplayName("MasterRecordId").setDataType("reference");
	    descAttrib.setNillable(true);
	    entitySchema.addField(descAttrib);
	    
	    var sfdcConnector = createConnectorWithSchema(List.of(entitySchema));
	    EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
	    List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());
	    var sfdcRefAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
	    
	    
	    EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
	    coreAccount = schemaService.getEntity(coreAccount.getId());
	    List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(coreAccount.getId());
	    var coreRefAttr = coreAccountAttribs.stream().filter(a->a.getApiName().equals("MasterRecordId")).findFirst().get();
	    
        MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
        MappingGraph refGraph = mappingGraphService.retrieveAttributeGraph(coreRefAttr.getId()).get();

        // Save sfdc ref node
        var sfdcRefNode = nodeRepo.save(new MappingNode()
                .setApiName(sfdcRefAttrib.getApiName())
                .setName(sfdcRefAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
                .setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcRefAttrib))
                .setMappingGraphId(refGraph.getId()));

        // Save sfdc ref node edge
        var coreRefNode = refGraph.getCoreNode();
        var edge = edgeRepo.save(
                new Edge().setGraphId(refGraph.getId()).setInput(coreRefNode.getConfiguration().getInputPorts().get(0))
                        .setOutput(coreRefNode.getConfiguration().getOutputPorts().get(0))
                        .setDestinationStage(coreRefNode).setSourceStage(sfdcRefNode));

        AttributeDefinition wm = sfdcAccountAttribs.get(0).setWatermarkField(true);
        attributeProxyRepo.save(wm);
        var validationErrors = mappingGraphService.validateGraphWithoutException(accountGraph, false, mappingGraphService.getCoreEntity(accountGraph), mappingGraphService.getConnectedSourceEntityMap(accountGraph), new HashMap<String, Object>());
        assertEquals(1, validationErrors.size());
        assertEquals("A non reference field 'Account Name' cannot be mapped to reference field 'Master Record ID'", validationErrors.get(0).getMessage());
        assertEquals(sfdcRefNode.getId(), validationErrors.get(0).getNodeId());
	    edgeRepo.delete(edge);
	    nodeRepo.delete(sfdcRefNode);
	}

	@Test
	public void validateReferenceField_InvalidEntityReference(){
		var syncariConnector = connectorService.getSyncariConnector();
		var testConnector = getTestConnector();

		EntityDefinition synapseEntity = schemaService.getEntity(testConnector.getId(), "contact");

		EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreContact", "contact", syncariConnector);
		var coreField1 = SchemaHelper.createAttribute("coreField1", StringType.VALUE, coreEntity.getId());
		var coreRefField = SchemaHelper.createAttribute("coreRefField", ReferenceType.VALUE, coreEntity.getId());
		coreRefField.setReferenceTo("Account");// Set incorrect ref entity name
		coreEntity.addField(coreField1);
		coreEntity.addField(coreRefField);

		entityProxyRepo.save(coreEntity);
		attributeProxyRepo.saveAll(List.of(coreField1, coreRefField));

		// create entity graph subsequent field graphs
		MappingGraph entityGraph = newGraph(coreEntity, functionService)
				.src(synapseEntity, "srcEntity")
				.connect("srcEntity", "coreContact").getGraph();
		MappingGraph field1Graph = newGraph(coreField1, functionService)
				.src(synapseEntity.getFieldByName("firstName"), "srcField1")
				.connect("srcField1", "coreField1").getGraph();
		MappingGraph field2Graph = newGraph(coreRefField, functionService)
				.src(synapseEntity.getFieldByName("accountId"), "srcField2")
				.connect("srcField2", "coreRefField").getGraph();

		mappingGraphService.upsertEntityGraph(entityGraph);
		mappingGraphService.upsertAttributeGraph(field1Graph);
		mappingGraphService.upsertAttributeGraph(field2Graph);

		assertValidation(entityGraph, "Invalid referenced entity 'Account' for reference field 'coreRefField'. Please update the referenced entity for this field in schema studio.");

	}

	@Test
	public void validateWatermarkField(){
	    String syncariConnId = connectorService.findSyncariConnector().getId();
        EntityDefinition syncariEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnId,"account").get();
        List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(syncariEntity.getId());
        var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
        var coreDescriptionAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
        MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		mappingGraphService.createDefaultAttributeGraph(coreNameAttrib.getId());
		mappingGraphService.createDefaultAttributeGraph(coreDescriptionAttrib.getId());

		var sfdcConnector = createConnectorForReadyOnly();
		EntityDefinition sfdcAccount = schemaService.getEntity(sfdcConnector.getId(), "Account");
		sfdcAccount.addField(getId(sfdcAccount));
		List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());

		List<MappingGraph> attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		MappingGraph attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
		attrGraph.setReady(true);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		// Add Account description pipeline
		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreDescriptionAttrib.getId())).findFirst().get();
		attrGraph.setReady(false);

		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);
		assertValidation(defaultEntityGraph, "Watermark field not defined for Account. <a href=\"https://support.syncari.com/hc/en-us/articles/360056583272-Configure-the-Id-and-Watermark-fields-for-a-Synapse-Entity\" target=\"_blank\" rel=\"noopener noreferrer\">Support Article</a>.");

		AttributeDefinition wm = sfdcAccountAttribs.get(0).setWatermarkField(true);
		attributeProxyRepo.save(wm);
		mappingGraphService.validateGraph(defaultEntityGraph.getId(), true);
	}

    @Test
	public void validateIdField(){
	    String syncariConnId = connectorService.findSyncariConnector().getId();
        EntityDefinition syncariEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnId,"account").get();
        List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(syncariEntity.getId());
        var coreIdAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Id")).findFirst().get();
        var coreLastModifiedDateAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("LastModifiedDate")).findFirst().get();
        var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
        var coreDescriptionAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
        MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
        mappingGraphService.createDefaultAttributeGraph(coreIdAttrib.getId());
		mappingGraphService.createDefaultAttributeGraph(coreLastModifiedDateAttrib.getId());
		mappingGraphService.createDefaultAttributeGraph(coreNameAttrib.getId());
		mappingGraphService.createDefaultAttributeGraph(coreDescriptionAttrib.getId());

		var mysqlConnector = createMySQLConnectorForTest();
		EntityDefinition mysqlAccount = schemaService.getEntity(mysqlConnector.getId(), "Account");
		List<AttributeDefinition> mysqlAccountAttribs = attributeProxyRepo.findByEntityId(mysqlAccount.getId());

		List<MappingGraph> attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		MappingGraph attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreIdAttrib.getId())).findFirst().get();
		attrGraph.setReady(true);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreLastModifiedDateAttrib.getId())).findFirst().get();
		attrGraph.setReady(true);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
		attrGraph.setReady(true);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		// Add Account description pipeline
		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreDescriptionAttrib.getId())).findFirst().get();
		attrGraph.setReady(false);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		assertValidation(defaultEntityGraph, "Watermark field not defined for Account. <a href=\"https://support.syncari.com/hc/en-us/articles/360056583272-Configure-the-Id-and-Watermark-fields-for-a-Synapse-Entity\" target=\"_blank\" rel=\"noopener noreferrer\">Support Article</a>.");

		AttributeDefinition wm = mysqlAccountAttribs.get(1).setWatermarkField(true);
		attributeProxyRepo.save(wm);

		assertValidation(defaultEntityGraph, "Id field not defined for Account. <a href=\"https://support.syncari.com/hc/en-us/articles/360056583272-Configure-the-Id-and-Watermark-fields-for-a-Synapse-Entity\" target=\"_blank\" rel=\"noopener noreferrer\">Support Article</a>.");

		AttributeDefinition id = mysqlAccountAttribs.get(0).setIdField(true);
		attributeProxyRepo.save(id);
        mappingGraphService.validateGraph(defaultEntityGraph.getId(), true);
	}

	@Test
	public void validateAttributeGraph_ExternalIdField(){
		EntityDefinition source = SchemaHelper.createEntityDefinition("srcContact")
				.string("srcId").id().getEntityDefinition();
		EntityDefinition coreEntity = SchemaHelper.createEntityDefinition("contact").id().getEntityDefinition();
		AttributeDefinition coreField = SchemaHelper.createAttribute("coreId", ExternalIdType.VALUE, coreEntity.getId());
		coreField.setUpdatable(false);
		coreEntity.addField(coreField);
		EntityDefinition dest = SchemaHelper.createEntityDefinition("destContact")
				.string("destId").id().getEntityDefinition();

		var entities = entityProxyRepo.saveAll(List.of(source, coreEntity, dest));
		var fields = attributeProxyRepo.saveAll(
				Stream.of(source.getAttributes(), coreEntity.getAttributes(), dest.getAttributes())
						.flatMap(Collection::stream)
						.collect(Collectors.toList())
		);

		final MappingGraph externalIdMappingGraph = newGraph(coreEntity.getFieldByName("coreId"))
				.src(source.getFieldByName("srcId"))
				.dest(dest.getFieldByName("destId"))
				.connect("srcId", "coreId")
				.connect("coreId", "destId")
				.getGraph();

		String expectedMessage = "The External Id Syncari field coreId is read-only and cannot have source side mapping";
		var errors = mappingGraphService.validateAttributeGraphsWithoutException(externalIdMappingGraph, false, Map.of());
		assertFalse(errors.isEmpty());
		assertEquals(expectedMessage, errors.get(0).getMessage());

		final MappingGraph externalIdMappingGraph_DestOnly = newGraph(coreEntity.getFieldByName("coreId"))
				.dest(dest.getFieldByName("destId"))
				.connect("coreId", "destId")
				.getGraph();

		errors = mappingGraphService.validateAttributeGraphsWithoutException(externalIdMappingGraph_DestOnly, false, Map.of());
		assertTrue(errors.isEmpty());

		// cleanup
		entityProxyRepo.deleteAll(entities);
		attributeProxyRepo.deleteAll(fields);
	}

	private Connector createConnector() {
		EntitySchema entitySchema = new EntitySchema("Account", "Account");
		entitySchema.addField(new AttributeSchema("Name","string").setDisplayName("Name"));
		entitySchema.addField(new AttributeSchema("Id","id").setDisplayName("Id").setIdField(true));
		entitySchema.addField(new AttributeSchema("LastModifiedDate","datetime").setDisplayName("Last Modified Date").setWatermarkField(true));
		when(salesforceService.describeAll(any())).thenReturn(List.of(entitySchema));
		when(salesforceService.getEntityMappings()).thenReturn(new SalesforceService().getEntityMappings());
		when(salesforceService.getName()).thenReturn("salesforce");
		when(salesforceService.isSource()).thenReturn(true);
		when(salesforceService.isSink()).thenReturn(true);
		var connector = new Connector("sfdc1", connectorService.describe("salesforce"),
				config.getSalesforceUrl(), config.getUser(), config.getPassword());
		connector.getAuthConfig().setToken(config.getToken());
		connector = connectorService.save(connector);
		connectorService.authenticated(connector.getId());
		connectorService.activate(connector.getId());
		verify(salesforceService).describeAll(any());
		return connector;
	}

	private Connector createSFDCConnector() {
		EntitySchema entitySchema = new EntitySchema("Account", "Account");
		entitySchema.addField(new AttributeSchema("Name","string").setDisplayName("Name"));
		entitySchema.addField(new AttributeSchema("Id","id").setDisplayName("Id").setIdField(true));
		entitySchema.addField(new AttributeSchema("LastModifiedDate","datetime").setDisplayName("Last Modified Date").setWatermarkField(true));

		EntitySchema docSchema = new EntitySchema("Document", "Document");
		docSchema.addField(new AttributeSchema("Name","string").setDisplayName("Name"));
		docSchema.addField(new AttributeSchema("Id","id").setDisplayName("Id").setIdField(true).setStatus(com.syncari.connector.Status.INACTIVE));
		docSchema.addField(new AttributeSchema("LastModifiedDate","datetime").setDisplayName("Last Modified Date").setWatermarkField(true));

		when(salesforceService.describeAll(any())).thenReturn(List.of(entitySchema, docSchema));
		when(salesforceService.getEntityMappings()).thenReturn(new SalesforceService().getEntityMappings());
		when(salesforceService.getName()).thenReturn("salesforce");
		when(salesforceService.isSource()).thenReturn(true);
		when(salesforceService.isSink()).thenReturn(true);
		var connector = new Connector("sfdc1", connectorService.describe("salesforce"),
				config.getSalesforceUrl(), config.getUser(), config.getPassword());
		connector.getAuthConfig().setToken(config.getToken());
		connector = connectorService.save(connector);
		connectorService.authenticated(connector.getId());
		connectorService.activate(connector.getId());
		verify(salesforceService).describeAll(any());
		return connector;
	}

	private Connector getTestConnector() {
		if(testConnector == null) {
			ConnectorMetadata metadata = connectorService.describe(Constants.TEST_SYNAPSE);
			testConnector = new Connector("testSynapse1", metadata.getId(), "http://someurl");
			testConnector.setMetadata(metadata);
			Connector saved = connectorService.save(testConnector);
			connectorService.authenticated(saved.getId());
			connectorService.activate(saved.getId());
		}
		return testConnector;
	}

	private Connector createConnectorWithSchema(List<EntitySchema> entitySchema) {
		DataServiceFactory mockDataServiceFactory = mock(DataServiceFactory.class);
		when(mockDataServiceFactory.getSchemaService(any())).thenReturn(salesforceService);
		when(mockDataServiceFactory.getDataService(any())).thenReturn(salesforceService);
		when(mockDataServiceFactory.getSynapseService(any())).thenReturn(salesforceService);
		when(salesforceService.describeAll(any())).thenReturn(entitySchema);
		when(salesforceService.getEntityMappings()).thenReturn(new SalesforceService().getEntityMappings());
		when(salesforceService.getName()).thenReturn("salesforce");
		when(salesforceService.isSource()).thenReturn(true);
		when(salesforceService.isSink()).thenReturn(true);

		var tempDataServiceFactory = schemaService.factory;
		schemaService.factory = mockDataServiceFactory;
		var connector = new Connector("sfdc1", connectorService.describe("salesforce"),
				config.getSalesforceUrl(), config.getUser(), config.getPassword());
		connector.getAuthConfig().setToken(config.getToken());
		connector = connectorService.save(connector);
		connectorService.authenticated(connector.getId());
		connectorService.activate(connector.getId());

		schemaService.factory = tempDataServiceFactory;
		return connector;
	}

    private Connector createMysqlConnectorWithSchema(List<EntitySchema> entitySchema) {
		DataServiceFactory mockDataServiceFactory = mock(DataServiceFactory.class);
		when(mockDataServiceFactory.getSchemaService(any())).thenReturn(mysqlService);
		when(mockDataServiceFactory.getDataService(any())).thenReturn(mysqlService);
		when(mockDataServiceFactory.getSynapseService(any())).thenReturn(mysqlService);
		when(mysqlService.describeAll(any())).thenReturn(entitySchema);
		when(mysqlService.getEntityMappings()).thenReturn(new SalesforceService().getEntityMappings());
		when(mysqlService.getName()).thenReturn(Constants.MYSQL);
		when(mysqlService.isSource()).thenReturn(true);
		when(mysqlService.isSink()).thenReturn(true);

		var tempDataServiceFactory = schemaService.factory;
		schemaService.factory = mockDataServiceFactory;
        // TODO Properly set mysql creds here.
		var connector = new Connector("mysql1", connectorService.describe(Constants.MYSQL),
				config.getSalesforceUrl(), config.getUser(), config.getPassword());
		connector.getAuthConfig().setToken(config.getToken());
		connector = connectorService.save(connector);
		connectorService.authenticated(connector.getId());
		connectorService.activate(connector.getId());

		schemaService.factory = tempDataServiceFactory;
		return connector;
	}

	private void assertValidation(MappingGraph mappingGraph, String expectedMessage) {
		try {
			mappingGraphService.validateGraph(mappingGraph.getId());
			fail("Validation expected to fail with message: " + expectedMessage);
		} catch (SyncariValidationException e) {
			assertEquals(expectedMessage, e.getMessage());
		}
	}

	@Test
	public void retrieveEntityGraph(){

		// No graph exists for the entity
		assertEquals(0, mappingGraphService.retrieveEntityGraphs().size());

		// get syncari entity
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());

		// create graph for entity
		/*MappingGraph graph1 = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));*/
		assertEquals(1, mappingGraphService.retrieveEntityGraphs().size());
		assertEquals(0, mappingGraphService.retrieveActiveEntityGraphs().size());

		var approved = mappingGraphService.approveDraft(defaultEntityGraph);
		assertEquals(1, mappingGraphService.retrieveEntityGraphs().size());
		assertEquals(1, mappingGraphService.retrieveActiveEntityGraphs().size());

		MappingGraph graph2 = mappingGraphService.createDraftFor(approved); // create new graph2

		assertEquals(2, mappingGraphService.retrieveEntityGraphs().size());
		assertEquals(1, mappingGraphService.retrieveActiveEntityGraphs().size());

		graph2 = mappingGraphService.approveDraft(graph2); // the existing graph1 will be archived
		assertEquals(1, mappingGraphService.retrieveEntityGraphs().size());
		assertEquals(1, mappingGraphService.retrieveActiveEntityGraphs().size());

		MappingGraph graph3 = mappingGraphService.createDraftFor(graph2); // new graph3 in draft status

		assertEquals(2, mappingGraphService.retrieveEntityGraphs().size());
		assertEquals(1, mappingGraphService.retrieveActiveEntityGraphs().size());

	}

	@Test
	public void deleteEntityGraph(){

		// get syncari entity
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));

		var node1 = nodeRepo.save(new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId()));

		var node2 = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId()));

		var edge1 = edgeRepo.save(new Edge().setGraphId(defaultEntityGraph.getId()).setInput(InputPort.any())
				.setOutput(OutputPort.any()).setDestinationStage(node2).setSourceStage(node1))
				.setGraphId(defaultEntityGraph.getId());

		var retrieved = mappingGraphService.retrieve(defaultEntityGraph.getId()).orElseThrow();

		//var graph = mappingGraphService.createDraftFor(retrieved);
		var graph = mappingGraphService.approveDraft(defaultEntityGraph);
		graph = mappingGraphService.retrieve(graph.getId()).orElseThrow();

		// add a dependency
		dependencyService.addDependency(graph.getId(), ComponentType.pipeline, "referenceId", ComponentType.referencedata);

		assertEquals(3, graph.getNodes().size());
		assertEquals(1, graph.getEdges().size());

		assertEquals(1, dependencyService.findDependenciesBy(graph.getId(), ComponentType.pipeline).size());

		mappingGraphService.deleteApprovedEntityGraph(syncariEntity.getId());

		assertFalse(mappingGraphService.retrieve(graph.getId()).isPresent());
		assertTrue(nodeRepo.findByGraphId(graph.getId()).isEmpty());
		assertTrue(edgeRepo.findByGraphId(graph.getId()).isEmpty());
		assertEquals(0, layoutService.findNodeLayouts(graph.getNodes().stream().map(MappingNode::getId).collect(Collectors.toList())).size());
		assertEquals(0, layoutService.findEdgeLayouts(graph.getEdges().stream().map(Edge::getId).collect(Collectors.toList())).size());
		assertEquals(0, dependencyService.findDependenciesBy(graph.getId(), ComponentType.pipeline).size());

	}
	
	@Test
	public void search(){
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));

		var node1 = nodeRepo.save(new MappingNode().setName("Save").setApiName("mask").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId()));

		var node2 = nodeRepo.save(new MappingNode().setName("line with space").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId()));

		var edge1 = edgeRepo.save(new Edge().setGraphId(defaultEntityGraph.getId()).setInput(InputPort.any())
				.setOutput(OutputPort.any()).setDestinationStage(node2).setSourceStage(node1))
				.setGraphId(defaultEntityGraph.getId());
		
		var node3 = nodeRepo.save(new MappingNode().setName("Attach").setApiName("attach").setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(syncariEntity.getAttributes().get(0)))
				.setMappingGraphId(defaultAttributeGraph.getId()));

		List<MappingGraph> list = mappingGraphService.search("Save");
		assertEquals(1, list.size());
		assertEquals(defaultEntityGraph.getId(), list.get(0).getId());
//		assertEquals("Save", list.get(0).getFunctions().findFirst().get().getName());
//		assertEquals("mask", list.get(0).getFunctions().findFirst().get().getApiName());
		list = mappingGraphService.search("save");
		assertEquals(1, list.size());
		assertEquals(defaultEntityGraph.getId(), list.get(0).getId());
		//partial search
		list = mappingGraphService.search("sa");
		assertEquals(1, list.size());
		assertEquals(defaultEntityGraph.getId(), list.get(0).getId());
		list = mappingGraphService.search("ith");
		assertEquals(1, list.size());
		assertEquals(defaultEntityGraph.getId(), list.get(0).getId());
		list = mappingGraphService.search("with");
		assertEquals(1, list.size());
		assertEquals(defaultEntityGraph.getId(), list.get(0).getId());
		list = mappingGraphService.search("mask");
		assertEquals(1, list.size());
		assertEquals(defaultEntityGraph.getId(), list.get(0).getId());
		list = mappingGraphService.search("Mask");
		assertEquals(1, list.size());
		assertEquals(defaultEntityGraph.getId(), list.get(0).getId());
		// test parentId set
		list = mappingGraphService.search("Attach");
		assertEquals(1, list.size());
		assertEquals(defaultAttributeGraph.getId(), list.get(0).getId());
		assertEquals(syncariEntity.getId(), list.get(0).getParentId());
		
		var graph = mappingGraphService.approveDraft(defaultEntityGraph);
		assertFalse(defaultEntityGraph.getId() == graph.getId());
		list = mappingGraphService.search("Save");
		assertEquals(1, list.size());
		assertEquals(graph.getId(), list.get(0).getId());
		list = mappingGraphService.search("mask");
		assertEquals(1, list.size());
		assertEquals(graph.getId(), list.get(0).getId());

		MappingGraph entityDraft = mappingGraphService.createDraftFor(graph);
		list = mappingGraphService.search("Save");
		assertEquals(2, list.size());
		list = mappingGraphService.search("mask");
		assertEquals(2, list.size());
	}

	@Test
	public void deleteApprovedEntityGraph_deletesWatermark(){
		var syncariConnector =connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();

		accountGraph = mappingGraphService.approveDraft(accountGraph);

		assertFalse(watermarkService.getUpstreamWatermarks(coreAccount.getApiName(), List.of(sfdcAccount.getId())).isEmpty());
		var syncStream = streamService.findStream(accountGraph.getId());
		assertTrue(syncStream.isPresent());
		assertEquals(SyncStream.Status.READY, syncStream.get().getStatus());

		mappingGraphService.deleteApprovedEntityGraph(coreAccount.getId());

		assertTrue(watermarkService.getUpstreamWatermarks(coreAccount.getApiName(), List.of(sfdcAccount.getId())).isEmpty());
		syncStream = streamService.findStream(accountGraph.getId());
		assertTrue(syncStream.isPresent());
		assertEquals(SyncStream.Status.INACTIVE, syncStream.get().getStatus());

	}

	@Test
	public void deleteApprovedEntityGraph_CancelsInProgressResyncAndOtherRemainsUnchanged(){
		var syncariConnector =connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		accountGraph = mappingGraphService.approveDraft(accountGraph);

		ResyncDetail resync1 = resyncService.createResyncRequest(coreAccount.getId(), List.of(sfdcAccount.getId()), Instant.EPOCH, Instant.now());
		resync1.setStatus(ResyncStatus.SUCCESS);
		resyncRepo.save(resync1);
		ResyncDetail resync2 = resyncService.createResyncRequest(coreAccount.getId(), List.of(sfdcAccount.getId()), Instant.EPOCH, Instant.now());
		resync2.setStatus(ResyncStatus.ERROR);
		resyncRepo.save(resync2);
		ResyncDetail resync3 = resyncService.createResyncRequest(coreAccount.getId(), List.of(sfdcAccount.getId()), Instant.EPOCH, Instant.now());
		ResyncDetail retrieved1 = resyncRepo.findById(resync1.getId()).get();
		assertEquals(ResyncStatus.SUCCESS, retrieved1.getStatus());
		ResyncDetail retrieved2 = resyncRepo.findById(resync2.getId()).get();
		assertEquals(ResyncStatus.ERROR, retrieved2.getStatus());
		ResyncDetail retrieved3 = resyncRepo.findById(resync3.getId()).get();
		assertEquals(ResyncStatus.NEW, retrieved3.getStatus());

		mappingGraphService.deleteApprovedEntityGraph(coreAccount.getId());

		retrieved1 = resyncRepo.findById(resync1.getId()).get();
		assertEquals(ResyncStatus.SUCCESS, retrieved1.getStatus());
		retrieved2 = resyncRepo.findById(resync2.getId()).get();
		assertEquals(ResyncStatus.ERROR, retrieved2.getStatus());
		retrieved3 = resyncRepo.findById(resync3.getId()).get();
		assertEquals(ResyncStatus.CANCELLED, retrieved3.getStatus());

	}

	@Test
	public void testDeleteDependency(){

		// get syncari entity
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		MappingGraph defaultAttributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());
		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));

		var node1 = nodeRepo.save(new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId()));

		var node2 = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(defaultEntityGraph.getId()));

		var edge1 = edgeRepo.save(new Edge().setGraphId(defaultEntityGraph.getId()).setInput(InputPort.any())
				.setOutput(OutputPort.any()).setDestinationStage(node2).setSourceStage(node1))
				.setGraphId(defaultEntityGraph.getId());

		var retrieved = mappingGraphService.retrieve(defaultEntityGraph.getId()).orElseThrow();

		var graph = mappingGraphService.approveDraft(defaultEntityGraph);
		graph = mappingGraphService.retrieve(graph.getId()).orElseThrow();

		// add a dependency
		dependencyService.addDependency(graph.getId(), ComponentType.pipeline, "referenceId", ComponentType.referencedata);
		assertEquals(1, dependencyService.findDependencies("referenceId", ComponentType.referencedata, ComponentType.pipeline).size());

		dependencyService.addDependency(graph.getId(), ComponentType.pipeline, "referenceId1", ComponentType.referencedata);
		dependencyService.deleteDependency(graph.getId(), ComponentType.pipeline, "referenceId1", ComponentType.referencedata);
		assertEquals(0, dependencyService.findDependencies("referenceId1", ComponentType.referencedata, ComponentType.pipeline).size());

		assertEquals(3, graph.getNodes().size());
		assertEquals(1, graph.getEdges().size());

		assertEquals(1, dependencyService.findDependenciesBy(graph.getId(), ComponentType.pipeline).size());

		mappingGraphService.deleteApprovedEntityGraph(syncariEntity.getId());

		assertFalse(mappingGraphService.retrieve(graph.getId()).isPresent());
		assertTrue(nodeRepo.findByGraphId(graph.getId()).isEmpty());
		assertTrue(edgeRepo.findByGraphId(graph.getId()).isEmpty());
		assertEquals(0, layoutService.findNodeLayouts(graph.getNodes().stream().map(MappingNode::getId).collect(Collectors.toList())).size());
		assertEquals(0, layoutService.findEdgeLayouts(graph.getEdges().stream().map(Edge::getId).collect(Collectors.toList())).size());
		assertEquals(0, dependencyService.findDependenciesBy(graph.getId(), ComponentType.pipeline).size());
		assertEquals(0, dependencyService.findDependencies("referenceId", ComponentType.referencedata, ComponentType.pipeline).size());

	}

	@Test
	public void restart_testRunning(){
		EntityDefinition syncariEntity = entityProxyRepo
				.findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
		MappingGraph mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
		mappingGraph.setDraftStatus(DraftStatus.APPROVED);
		mappingGraphRepo.save(mappingGraph);

		PipelineTestRepo mockPipelineTestRepo = mock(PipelineTestRepo.class);
		PipelineTest mockTest = new PipelineTest().setStatus(Status.PROCESSING);
		assertTrue(mockTest.isRunningTest());
		when(mockPipelineTestRepo.findByGraphIdAndStatusIn(any(), any())).thenReturn(List.of(mockTest));
		PipelineTestRepo originalRepo = pipelineTestService.pipelineTestRepo;
		pipelineTestService.pipelineTestRepo = mockPipelineTestRepo;
		assertTrue(pipelineTestService.hasTestInProgress(mappingGraph));
		try{
			mappingGraphService.restart(syncariEntity.getId());
			fail();
		} catch (Exception e){
			assertEquals("A test is being run on this graph currently. Cannot resume sync, try again later.", e.getMessage());
		}

		mockTest = new PipelineTest().setStatus(Status.NEW);
		when(mockPipelineTestRepo.findByGraphIdAndStatusIn(any(), any())).thenReturn(List.of(mockTest));
		try{
			mappingGraphService.restart(syncariEntity.getId());
			fail();
		} catch (Exception e){
			assertEquals("A test is being run on this graph currently. Cannot resume sync, try again later.", e.getMessage());
		}

		pipelineTestService.pipelineTestRepo = originalRepo;
	}

	@Test
	public void restart_success(){
		EntityDefinition syncariEntity = entityProxyRepo
				.findByConnectorId(connectorService.findSyncariConnector().getId()).get(0);
		MappingGraph mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(syncariEntity.getId()));
		mappingGraph.setDraftStatus(DraftStatus.APPROVED);
		mappingGraphRepo.save(mappingGraph);

		StreamService mockStreamService = mock(StreamService.class);
		when(mockStreamService.restart(any(), anyBoolean())).thenReturn(true);
		mappingGraphService.setStreamService(mockStreamService);
		assertTrue(mappingGraphService.restart(syncariEntity.getId()));
	}
	
   @Test
    public void hasChanges(){
       String syncariConnId = connectorService.findSyncariConnector().getId();
       EntityDefinition syncariEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnId,"account").get();
       List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(syncariEntity.getId());
       var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
       
       MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
       assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());

       var draftAttributeGraph =mappingGraphService.createDefaultAttributeGraph(coreNameAttrib.getId());
       assertEquals(DraftStatus.NEW, draftAttributeGraph.getDraftStatus());
       
       List<MappingGraph> attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
       MappingGraph attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
       // First draft creation without any changes are not detected as changes
       assertFalse(attrGraph.isChanged());
       
       var sfdcConnector = createConnector();
       EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
       List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());
       var sfdcNameAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
       MappingGraph nameGraph = mappingGraphService.retrieveAttributeGraph(coreNameAttrib.getId()).get();

       var sfdcAccountNameNode = nodeRepo.save(new MappingNode()
               .setApiName(sfdcNameAttrib.getApiName())
               .setName(sfdcNameAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
               .setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcNameAttrib))
               .setMappingGraphId(nameGraph.getId()));


       var coreNameNode = nameGraph.getCoreNode();
        edgeRepo.save(new Edge().setGraphId(nameGraph.getId())
				.setInput(coreNameNode.getConfiguration().getInputPorts().get(0))
				.setOutput(coreNameNode.getConfiguration().getOutputPorts().get(0))
				.setDestinationStage(sfdcAccountNameNode).setSourceStage(sfdcAccountNameNode));

	   attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
	   attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
	   // Further changes are detected as changes
	   assertTrue(attrGraph.isChanged());

	   // Editing the changed graph still shows as changed
	   attrGraph.setChanged(false);
	   mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);
	   attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
	   assertTrue(attrGraph.isChanged());

	   mappingGraphService.approveDraft(defaultEntityGraph);
	   var retrievedEntityGraph = mappingGraphService.retrieveEntityGraph(syncariEntity.getId()).get();
	   var retrievedAttributeGraph = mappingGraphService.retrieveAttributeGraph(coreNameAttrib.getId()).get();

	   assertEquals(DraftStatus.APPROVED, retrievedEntityGraph.getDraftStatus());
	   assertEquals(DraftStatus.APPROVED, retrievedAttributeGraph.getDraftStatus());

	   attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(retrievedEntityGraph.getId());
       attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
       // Approved graphs should not have any changes
       assertFalse(attrGraph.isChanged());
    }

	@Test
	public void fieldPipelineMarkedReadyAndToggle(){
		String syncariConnId = connectorService.findSyncariConnector().getId();
		EntityDefinition syncariEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnId,"account").get();
		List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(syncariEntity.getId());
		var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();

		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		assertEquals(DraftStatus.NEW, defaultEntityGraph.getDraftStatus());

		var draftAttributeGraph = mappingGraphService.createDefaultAttributeGraph(coreNameAttrib.getId());
		assertEquals(DraftStatus.NEW, draftAttributeGraph.getDraftStatus());

		List<MappingGraph> attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		MappingGraph attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();

		attrGraph.setReady(true);
		MappingGraph g = mappingGraphService.upsertAttributeGraph(attrGraph);
		assertTrue(g.isReady());
		g.setReady(false);
		MappingGraph g1 = mappingGraphService.upsertAttributeGraph(attrGraph);
		assertFalse(g1.isReady());
	}

	@Test
	public void approveReadyOnly() {
		String syncariConnId = connectorService.findSyncariConnector().getId();
		EntityDefinition syncariEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnId,"account").get();
		List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(syncariEntity.getId());
		var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		var coreDescriptionAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		mappingGraphService.createDefaultAttributeGraph(coreNameAttrib.getId());
		mappingGraphService.createDefaultAttributeGraph(coreDescriptionAttrib.getId());

		List<MappingGraph> attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());

		var sfdcConnector = createConnectorForReadyOnly();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
		List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());

		// Add name pipeline
		var sfdcNameAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		MappingGraph nameGraph = mappingGraphService.retrieveAttributeGraph(coreNameAttrib.getId()).get();

		var sfdcAccountNameNode = nodeRepo.save(new MappingNode()
				.setApiName(sfdcNameAttrib.getApiName())
				.setName(sfdcNameAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcNameAttrib))
				.setMappingGraphId(nameGraph.getId()));

		var coreNameNode = nameGraph.getCoreNode();
		edgeRepo.save(new Edge().setGraphId(nameGraph.getId())
				.setInput(coreNameNode.getConfiguration().getInputPorts().get(0))
				.setOutput(coreNameNode.getConfiguration().getOutputPorts().get(0))
				.setDestinationStage(sfdcAccountNameNode).setSourceStage(sfdcAccountNameNode));

		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		MappingGraph attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
		attrGraph.setReady(true);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		// Add Account description pipeline
		var sfdcDescriptionAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
		MappingGraph descriptionGraph = mappingGraphService.retrieveAttributeGraph(coreDescriptionAttrib.getId()).get();
		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());

		var sfdcDescriptionNode = nodeRepo.save(new MappingNode()
				.setApiName(sfdcDescriptionAttrib.getApiName())
				.setName(sfdcDescriptionAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcDescriptionAttrib))
				.setMappingGraphId(descriptionGraph.getId()));

		var coreDescriptionNode = descriptionGraph.getCoreNode();
		edgeRepo.save(new Edge().setGraphId(descriptionGraph.getId())
				.setInput(coreDescriptionNode.getConfiguration().getInputPorts().get(0))
				.setOutput(coreDescriptionNode.getConfiguration().getOutputPorts().get(0))
				.setDestinationStage(coreDescriptionNode).setSourceStage(sfdcDescriptionNode));

		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreDescriptionAttrib.getId())).findFirst().get();
		attrGraph.setReady(false);

		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		mappingGraphService.approveDraft(defaultEntityGraph, false, true);
		var retrievedEntityGraph = mappingGraphService.retrieveEntityGraph(syncariEntity.getId()).get();
		var retrievedAttributeGraph = mappingGraphService.retrieveAttributeGraph(coreNameAttrib.getId()).get();
		var retrievedDescriptionAttributeGraph = mappingGraphService.retrieveAttributeGraph(coreDescriptionAttrib.getId()).get();

		assertEquals(DraftStatus.APPROVED, retrievedEntityGraph.getDraftStatus());
		assertEquals(DraftStatus.APPROVED, retrievedAttributeGraph.getDraftStatus());
		assertEquals(DraftStatus.NEW, retrievedDescriptionAttributeGraph.getDraftStatus());
	}
	
	@Test
	public void approveReadyOnlyDeleted() {
		String syncariConnId = connectorService.findSyncariConnector().getId();
		EntityDefinition syncariEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnId,"account").get();
		List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(syncariEntity.getId());
		var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		var coreDescriptionAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		mappingGraphService.createDefaultAttributeGraph(coreNameAttrib.getId());
		mappingGraphService.createDefaultAttributeGraph(coreDescriptionAttrib.getId());

		List<MappingGraph> attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());

		var sfdcConnector = createConnectorForReadyOnly();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
		List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());

		// Add name pipeline
		var sfdcNameAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		MappingGraph nameGraph = mappingGraphService.retrieveAttributeGraph(coreNameAttrib.getId()).get();

		var sfdcAccountNameNode = nodeRepo.save(new MappingNode()
				.setApiName(sfdcNameAttrib.getApiName())
				.setName(sfdcNameAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcNameAttrib))
				.setMappingGraphId(nameGraph.getId()));

		var coreNameNode = nameGraph.getCoreNode();
		edgeRepo.save(new Edge().setGraphId(nameGraph.getId())
				.setInput(coreNameNode.getConfiguration().getInputPorts().get(0))
				.setOutput(coreNameNode.getConfiguration().getOutputPorts().get(0))
				.setDestinationStage(sfdcAccountNameNode).setSourceStage(sfdcAccountNameNode));

		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		MappingGraph attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
		attrGraph.setReady(true);
		attrGraph.setDeleted(true);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		// Add Account description pipeline
		var sfdcDescriptionAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
		MappingGraph descriptionGraph = mappingGraphService.retrieveAttributeGraph(coreDescriptionAttrib.getId()).get();
		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());

		var sfdcDescriptionNode = nodeRepo.save(new MappingNode()
				.setApiName(sfdcDescriptionAttrib.getApiName())
				.setName(sfdcDescriptionAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcDescriptionAttrib))
				.setMappingGraphId(descriptionGraph.getId()));

		var coreDescriptionNode = descriptionGraph.getCoreNode();
		edgeRepo.save(new Edge().setGraphId(descriptionGraph.getId())
				.setInput(coreDescriptionNode.getConfiguration().getInputPorts().get(0))
				.setOutput(coreDescriptionNode.getConfiguration().getOutputPorts().get(0))
				.setDestinationStage(coreDescriptionNode).setSourceStage(sfdcDescriptionNode));

		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreDescriptionAttrib.getId())).findFirst().get();
		attrGraph.setReady(false);

		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		mappingGraphService.approveDraft(defaultEntityGraph, false, true);
		var retrievedEntityGraph = mappingGraphService.retrieveEntityGraph(syncariEntity.getId()).get();
		var retrievedAttributeGraph = mappingGraphService.retrieveAttributeGraph(coreNameAttrib.getId());
		var retrievedDescriptionAttributeGraph = mappingGraphService.retrieveAttributeGraph(coreDescriptionAttrib.getId()).get();

		assertEquals(DraftStatus.APPROVED, retrievedEntityGraph.getDraftStatus());
		assertTrue(retrievedAttributeGraph.isEmpty());
		assertEquals(DraftStatus.NEW, retrievedDescriptionAttributeGraph.getDraftStatus());
	}

	@Test
	public void approveAllWithReadyOnlyFieldPipelines(){
		String syncariConnId = connectorService.findSyncariConnector().getId();
		EntityDefinition syncariEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnId,"account").get();
		List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(syncariEntity.getId());
		var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		var coreDescriptionAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		mappingGraphService.createDefaultAttributeGraph(coreNameAttrib.getId());
		mappingGraphService.createDefaultAttributeGraph(coreDescriptionAttrib.getId());

		var sfdcConnector = createConnectorForReadyOnly();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
		List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());

		// Add name pipeline
		var sfdcNameAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		MappingGraph nameGraph = mappingGraphService.retrieveAttributeGraph(coreNameAttrib.getId()).get();

		var sfdcAccountNameNode = nodeRepo.save(new MappingNode()
				.setApiName(sfdcNameAttrib.getApiName())
				.setName(sfdcNameAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcNameAttrib))
				.setMappingGraphId(nameGraph.getId()));

		var coreNameNode = nameGraph.getCoreNode();
		edgeRepo.save(new Edge().setGraphId(nameGraph.getId())
				.setInput(coreNameNode.getConfiguration().getInputPorts().get(0))
				.setOutput(coreNameNode.getConfiguration().getOutputPorts().get(0))
				.setDestinationStage(coreNameNode).setSourceStage(sfdcAccountNameNode));

		List<MappingGraph>  attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		MappingGraph attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
		attrGraph.setReady(true);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		// Add Account description pipeline
		var sfdcDescriptionAttrib = sfdcAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
		MappingGraph descriptionGraph = mappingGraphService.retrieveAttributeGraph(coreDescriptionAttrib.getId()).get();

		var sfdcDescriptionNode = nodeRepo.save(new MappingNode()
				.setApiName(sfdcDescriptionAttrib.getApiName())
				.setName(sfdcDescriptionAttrib.getApiName()).setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcDescriptionAttrib))
				.setMappingGraphId(descriptionGraph.getId()));

		var coreDescriptionNode = descriptionGraph.getCoreNode();
		edgeRepo.save(new Edge().setGraphId(descriptionGraph.getId())
				.setInput(coreDescriptionNode.getConfiguration().getInputPorts().get(0))
				.setOutput(coreDescriptionNode.getConfiguration().getOutputPorts().get(0))
				.setDestinationStage(coreDescriptionNode).setSourceStage(sfdcDescriptionNode));

		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreDescriptionAttrib.getId())).findFirst().get();
		attrGraph.setReady(false);

		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		mappingGraphService.approveDraft(defaultEntityGraph, false, false);
		var retrievedEntityGraph = mappingGraphService.retrieveEntityGraph(syncariEntity.getId()).get();
		var retrievedAttributeGraph = mappingGraphService.retrieveAttributeGraph(coreNameAttrib.getId()).get();
		var retrievedDescriptionAttributeGraph = mappingGraphService.retrieveAttributeGraph(coreDescriptionAttrib.getId()).get();

		assertEquals(DraftStatus.APPROVED, retrievedEntityGraph.getDraftStatus());
		assertEquals(DraftStatus.APPROVED, retrievedAttributeGraph.getDraftStatus());
		assertEquals(DraftStatus.APPROVED, retrievedDescriptionAttributeGraph.getDraftStatus());
	}

	@Test
	public void validateReadyOnlyGraphs(){
		String syncariConnId = connectorService.findSyncariConnector().getId();
		EntityDefinition syncariEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnId,"account").get();
		List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(syncariEntity.getId());
		var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		var coreDescriptionAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		mappingGraphService.createDefaultAttributeGraph(coreNameAttrib.getId());
		mappingGraphService.createDefaultAttributeGraph(coreDescriptionAttrib.getId());

		var sfdcConnector = createConnectorForReadyOnly();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
		sfdcAccount.addField(getId(sfdcAccount));
		List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());

		List<MappingGraph>  attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		MappingGraph attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
		attrGraph.setReady(true);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		// Add Account description pipeline
		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreDescriptionAttrib.getId())).findFirst().get();
		attrGraph.setReady(false);

		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);
        AttributeDefinition wm = sfdcAccountAttribs.get(0).setWatermarkField(true);
        attributeProxyRepo.save(wm);

		mappingGraphService.validateGraph(defaultEntityGraph.getId(), true);
		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreDescriptionAttrib.getId())).findFirst().get();
		assertEquals(attrGraph.isReady(), false);
	}

	@Test
	public void validateAllWithReadyOnlyFieldPipelines(){
		String syncariConnId = connectorService.findSyncariConnector().getId();
		EntityDefinition syncariEntity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnId,"account").get();
		List<AttributeDefinition> coreAccountAttribs = attributeProxyRepo.findByEntityId(syncariEntity.getId());
		var coreNameAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		var coreDescriptionAttrib = coreAccountAttribs.stream().filter(a->a.getApiName().equals("Description")).findFirst().get();
		MappingGraph defaultEntityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		mappingGraphService.createDefaultAttributeGraph(coreNameAttrib.getId());
		mappingGraphService.createDefaultAttributeGraph(coreDescriptionAttrib.getId());

		var sfdcConnector = createConnectorForReadyOnly();
		EntityDefinition sfdcAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(sfdcConnector.getId(),"Account").get();
		sfdcAccount.addField(getId(sfdcAccount));
		List<AttributeDefinition> sfdcAccountAttribs = attributeProxyRepo.findByEntityId(sfdcAccount.getId());

		List<MappingGraph>  attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		MappingGraph attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
		attrGraph.setReady(true);
		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);

		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreDescriptionAttrib.getId())).findFirst().get();
		attrGraph.setReady(false);

		mappingGraphService.upsertGraph(attrGraph, Optional.empty(), Optional.empty(), MappingNodeType.CORE_ATTRIBUTE, null);
        AttributeDefinition wm = sfdcAccountAttribs.get(0).setWatermarkField(true);
        attributeProxyRepo.save(wm);
		mappingGraphService.validateGraph(defaultEntityGraph.getId());
		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreNameAttrib.getId())).findFirst().get();
		assertEquals(attrGraph.isReady(), true);
		attrGraphs = mappingGraphService.retrieveAttributeGraphsForEntityGraph(defaultEntityGraph.getId());
		attrGraph = attrGraphs.stream().filter(a -> a.getTargetId().equalsIgnoreCase(coreDescriptionAttrib.getId())).findFirst().get();
		assertEquals(attrGraph.isReady(), false);
	}

	@Test
	public void getMappedAttributesForSource(){
		Connector syncariConnector = connectorService.getSyncariConnector();
		EntityDefinition coreEntity1 = SchemaHelper.createEntityDef("coreEntity1", "coreEntity1", syncariConnector);
		var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity1.getId());
		coreEntity1.addField(coreField1);

		EntityDefinition coreEntity2 = SchemaHelper.createEntityDef("coreEntity2", "coreEntity2", syncariConnector);
		var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity2.getId());
		coreEntity2.addField(coreField2);

		EntityDefinition coreEntity3 = SchemaHelper.createEntityDef("coreEntity3", "coreEntity3", syncariConnector);
		var coreField3 = SchemaHelper.createAttribute("corefield3", StringType.VALUE, coreEntity3.getId());
		coreEntity3.addField(coreField3);

		EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcEntity", "srcEntity",
				GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
		var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
		var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
		var srcField3 = SchemaHelper.createAttribute("srcfield3", StringType.VALUE, srcEntity.getId());
		var srcField4 = SchemaHelper.createAttribute("srcfield4", StringType.VALUE, srcEntity.getId());
		srcEntity.addField(srcField1);
		srcEntity.addField(srcField2);
		srcEntity.addField(srcField3);
		srcEntity.addField(srcField4);

		entityProxyRepo.saveAll(List.of(coreEntity1, coreEntity2, coreEntity3, srcEntity));
		attributeProxyRepo.saveAll(List.of(coreField1, coreField2, coreField3, srcField1, srcField2, srcField3, srcField4));

		MappingGraph entityGraph1 = newGraph(coreEntity1, functionService)
				.src(srcEntity).connect("coreEntity1", "srcEntity").getGraph();
		MappingGraph field1Graph = newGraph(coreEntity1.getFieldByName("corefield1"), functionService)
				.src(srcField1).connect("srcfield1", "corefield1").getGraph();

		Map<String, Object> predicateMap = new HashMap<>();
		var preidcates = List.of(Map.of(
				"left", Map.of("type", "variable", "value", srcField3.getId()),
				"operator", "eq",
				"right", Map.of("type", "literal", "value", "{{sourceConnector.srcEntity.srcfield4}}")
		));
		predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));

		MappingGraph entityGraph2 = newGraph(coreEntity2, functionService)
				.src(srcEntity).connect("coreEntity2", "srcEntity").getGraph();

		MappingGraph field2Graph = newGraph(coreEntity2.getFieldByName("corefield2"), functionService)
				.src(srcField2).function("filter", "filter", predicateMap).connect("srcfield2", "filter").connect("filter", "corefield2").getGraph();

		MappingGraph entityGraph3 = newGraph(coreEntity3, functionService)
				.src(srcEntity).connect("coreEntity3", "srcEntity").getGraph();

		MappingGraph field3Graph = newGraph(coreEntity3.getFieldByName("corefield3"), functionService)
				.src(srcField1).function("filter", "filter", predicateMap).connect("srcfield1", "filter").connect("filter", "corefield3").getGraph();


		mappingGraphService.upsertEntityGraph(entityGraph1);
		mappingGraphService.upsertEntityGraph(entityGraph2);
		mappingGraphService.upsertEntityGraph(entityGraph3);
		mappingGraphService.upsertAttributeGraph(field1Graph);
		mappingGraphService.upsertAttributeGraph(field2Graph);
		mappingGraphService.upsertAttributeGraph(field3Graph);

		List<AttributeDefinition> mappedAttrib1 = mappingGraphService.getMappedAndFilterAttributes(srcEntity, coreEntity1, entityGraph1);
		assertEquals(1, mappedAttrib1.size());
		assertEquals("srcfield1", mappedAttrib1.get(0).getApiName());
		List<AttributeDefinition> mappedAttrib2 = mappingGraphService.getMappedAndFilterAttributes(srcEntity, coreEntity2, entityGraph2);
		assertEquals(3, mappedAttrib2.size());
		Set<String> apiNames = mappedAttrib2.stream().map(attributeDefinition -> attributeDefinition.getApiName()).collect(Collectors.toSet());
		assertTrue(apiNames.contains("srcfield3"));
		assertTrue(apiNames.contains("srcfield4"));

		List<AttributeDefinition> mappedAttrib3 = mappingGraphService.getMappedAndFilterAttributes(srcEntity, coreEntity3, entityGraph3);
		assertEquals(3, mappedAttrib3.size());
		apiNames = mappedAttrib3.stream().map(attributeDefinition -> attributeDefinition.getApiName()).collect(Collectors.toSet());
		assertTrue(apiNames.contains("srcfield3"));
		assertTrue(apiNames.contains("srcfield4"));
		assertTrue(apiNames.contains("srcfield1"));
	}

	@Test
	public void trueFalseGraphs() {
		Connector syncariConnector = connectorService.getSyncariConnector();
		EntityDefinition coreEntity1 = SchemaHelper.createEntityDef("coreEntity1", "coreEntity1", syncariConnector);
		var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity1.getId());
		coreEntity1.addField(coreField1);

		EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcEntity", "srcEntity",
				GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
		var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
		var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
		var srcField3 = SchemaHelper.createAttribute("srcfield3", StringType.VALUE, srcEntity.getId());
		var srcField4 = SchemaHelper.createAttribute("srcfield4", StringType.VALUE, srcEntity.getId());
		srcEntity.addField(srcField1);
		srcEntity.addField(srcField2);
		srcEntity.addField(srcField3);
		srcEntity.addField(srcField4);

		entityProxyRepo.saveAll(List.of(coreEntity1, srcEntity));
		attributeProxyRepo.saveAll(List.of(coreField1, srcField1, srcField2, srcField3, srcField4));

		Map<String, Object> predicateMap = new HashMap<>();
		var preidcates = List.of(Map.of(
				"left", Map.of("type", "variable", "value", srcField3.getId()),
				"operator", "eq",
				"right", Map.of("type", "literal", "value", "{{sourceConnector.srcEntity.srcfield4}}")
		));

		predicateMap.put("predicate", Map.of("predicates", preidcates, "operator", "AND"));
		MappingGraph entityGraph1 = newGraph(coreEntity1, functionService)
				.src(srcEntity).function("filter", "filter", predicateMap).function("isTrue", "isTrue", Map.of())
				.connect("srcEntity", "filter").connect("filter", "isTrue").connect("isTrue", "coreEntity1")
				.getGraph();
		MappingGraph field1Graph = newGraph(coreEntity1.getFieldByName("corefield1"), functionService)
				.src(srcField1).connect("srcfield1", "corefield1").getGraph();

		FeatureService featureService = mock(FeatureService.class);
		//when(featureService.isEnabled(Features.PredicateNode)).thenReturn(true);
		mappingGraphService.featureService = featureService;

		mappingGraphService.upsertEntityGraph(entityGraph1);
		mappingGraphService.upsertAttributeGraph(field1Graph);
		mappingGraphService.approveDraft(entityGraph1, DraftStatus.APPROVED);

		var result = mappingGraphService.createDraftFor(entityGraph1);
		List<MappingNode> predicateNodeList = result.getNodes().stream().filter(node -> node.getApiName().equalsIgnoreCase(FunctionConstants.PREDICATE)).collect(Collectors.toList());
		assertTrue(!predicateNodeList.isEmpty());
		assertTrue(predicateNodeList.get(0).getName().equalsIgnoreCase("isTrue"));
	}

	@Test
	public void testGraph(){
		var syncariConnector = connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		schemaService.activateMapping(sfdcConnector);
		EntityDefinition coreAccount = entityProxyRepo
				.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(), "account").get();
		EntityDefinition sfdcAccount = schemaService.getEntity(sfdcConnector.getId(), "Account");

		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		MappingGraph attributeGraph = mappingGraphService.retrieveDraftAttributeGraphs(accountGraph.getId()).get(0);
		AttributeSinkNodeConfig sink = attributeGraph.getSinks().findAny().get().getTypedConfiguration();
		AttributeDefinition definition = sfdcAccount.getActiveAttributes().stream()
				.filter(a -> a.getId().equals(sink.getAttributeDefinition().getId())).findFirst().get();
		definition.setWatermarkField(true);
		definition.setIdField(true);
		attributeProxyRepo.save(definition);
		var existingDestNode = attributeGraph.getSinks().findFirst().get();
		MappingNode dest = new MappingNode().setApiName(definition.getApiName()).setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSinkNodeConfig().setAttributeDefinition(definition))
				.setName(existingDestNode.getName());
		dest.setId(ObjectId.get().toHexString());
		attributeGraph.addNode(dest);

		Edge edge = new Edge().setDestinationStage(dest).setSourceStage(attributeGraph.getCoreNode())
				.setInput(InputPort.any()).setOutput(OutputPort.any());
		edge.setId(ObjectId.get().toHexString());
		attributeGraph.addEdge(edge);
		attributeGraph = mappingGraphService.retrieveDraftAttributeGraphs(accountGraph.getId()).get(0);
		Map<String, EntityDefinition> sourceEntitiesMap = mappingGraphService.getConnectedSourceEntityMap(accountGraph);
		mappingGraphService.validateGraph(attributeGraph, coreAccount, sourceEntitiesMap);
		sfdcAccount.setStatus(Status.INACTIVE);
		entityProxyRepo.save(sfdcAccount);

		try {
			mappingGraphService.testEntityGraph(coreAccount.getId(), Instant.now(), Instant.now(), 0,
					Map.of(sfdcAccount.getId(), List.of()), Map.of());
		} catch (Exception e) {
			assertEquals("The source entity Account is deactivated, please activate it before running test",
					e.getMessage());
		}

		sfdcAccount.setStatus(Status.ACTIVE);
		entityProxyRepo.save(sfdcAccount);
		Map<String, List<String>> map = new HashMap<>();
		map.put(sfdcAccount.getId(), new ArrayList<>());
		mappingGraphService.testEntityGraph(coreAccount.getId(), Instant.now(), Instant.now(), 0,
				map, Map.of());
	}

	@Test
	public void updateGraphOnSyncariAttributeChange(){
		var syncariConnector = connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		schemaService.activateMapping(sfdcConnector);
		EntityDefinition coreAccount = entityProxyRepo
				.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(), "account").get();
		EntityDefinition sfdcAccount = schemaService.getEntity(sfdcConnector.getId(), "Account");

		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		MappingGraph attributeGraph = mappingGraphService.retrieveDraftAttributeGraphs(accountGraph.getId()).get(0);
		MappingNode coreNode = attributeGraph.getCoreNode();
		CoreAttributeNodeConfig coreNodeConfig = coreNode.getTypedConfiguration();
		AttributeDefinition attr = coreNodeConfig.getAttributeDefinition();
		attr.setDataType(new StringType());
		attr.setLength(1028);


		mappingGraphService.updateSyncariAttributeChangeForGivenGraph(mappingGraphService.retrieveDraftAttributeGraph(attr.getId()),attr);
		MappingGraph changedGraph = mappingGraphService.retrieveDraftAttributeGraph(attr.getId()).get();
		MappingNode changedCoreNode = changedGraph.getCoreNode();
		CoreAttributeNodeConfig changedCoreNodeConfig = changedCoreNode.getTypedConfiguration();
		AttributeDefinition changedAttr = changedCoreNodeConfig.getAttributeDefinition();
		assertEquals(new StringType(), changedAttr.getDataType());
		assertEquals(1028, changedAttr.getLength());

		changedGraph.getInboundEdges(changedCoreNode).forEach(e -> {
			assertEquals(e.getInput().getDatatype(), new StringType());
		});

		changedGraph.getOutboundEdges(changedCoreNode).forEach(e -> {
			assertEquals(e.getOutput().getDatatype(), new StringType());
		});

	}

	@Test
	public void deleteAttributeNodesFromApprovedGraph(){
		Connector syncariConnector = connectorService.getSyncariConnector();
		EntityDefinition coreEntity1 = SchemaHelper.createEntityDef("coreEntity1", "coreEntity1", syncariConnector);
		var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity1.getId());
		coreEntity1.addField(coreField1);
		var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity1.getId());
		coreEntity1.addField(coreField2);

		Connector c = GraphHelper.createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta");
		EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcEntity", "srcEntity", c);
		var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
		var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
		srcEntity.addField(srcField1);
		srcEntity.addField(srcField2);

		entityProxyRepo.saveAll(List.of(coreEntity1, srcEntity));
		attributeProxyRepo.saveAll(List.of(coreField1, coreField2, srcField1, srcField2));

		MappingGraph entityGraph1 = newGraph(coreEntity1, functionService)
				.src(srcEntity).connect("coreEntity1", "srcEntity").getGraph();
		MappingGraph field1Graph = newGraph(coreEntity1.getFieldByName("corefield1"), functionService)
				.src(srcField1).connect("srcfield1", "corefield1").getGraph();
		MappingGraph field2Graph = newGraph(coreEntity1.getFieldByName("corefield2"), functionService)
				.src(srcField2).connect("srcfield2", "corefield2").getGraph();

		mappingGraphService.upsertEntityGraph(entityGraph1);
		mappingGraphService.upsertAttributeGraph(field1Graph);
		mappingGraphService.upsertAttributeGraph(field2Graph);

		MappingGraph draftGraph = mappingGraphService.retrieveDraftEntityGraph(coreEntity1.getId()).get();
		MappingGraph approved = mappingGraphService.approveDraft(draftGraph);
		SyncStream syncStream = streamService.getOrCreateReadyStream(approved.getId());

		int oldNotifCount = notificationRepo.findAll().size();
		mappingGraphService.notifyAttributeDeletion(srcEntity, srcField2, c);

		var newNotifs = notificationRepo.findAll();
		assertTrue(newNotifs.size() > oldNotifCount);
		Notification latestNotif = newNotifs.get(newNotifs.size() - 1);
		assertEquals(String.format("Mapped Attribute srcfield2(srcfield2) in Entity srcEntity and Synapse sourceConnector was deleted in instance %s(%s) of Subscription %s",
				SyncariContext.getInstance().getDisplayName(), SyncariContext.getInstance().getSyncariId(), SyncariContext.getOrganziation().getName()),
				latestNotif.getSubject());
		assertEquals("A mapped attribute srcfield2(srcfield2) in Entity srcEntity and Synapse sourceConnector was deleted. This attribute was used in Field pipeline corefield2 of Entity coreEntity1. Source node for this Field pipeline would be deactivated.",
				latestNotif.getBody());
    }

	@Test
	public void graphValidations() {
		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));
		sfdc.getConfig().put("maskCharacter", "*");
		MappingGraph mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId("entityId"));
		// Empty graph
		assertValidation(mappingGraph, "Did not find a core node in Account Map pipeline ");

		var node1 = nodeRepo.save(new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(mappingGraph.getId()));
		EntityDefinition coreAccount= entityProxyRepo.findByConnectorIdAndApiName(connectorService.findSyncariConnector().getId(),"account").get();
		var coreNode = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreAccount))
				.setMappingGraphId(mappingGraph.getId()));
		// Graph has nodes, but not connected
		mappingGraph = mappingGraphService.retrieve(mappingGraph.getId()).orElseThrow();
		assertValidation(mappingGraph, "No edges in Account Map pipeline");
		var edge1 = edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				// .setDestinationStage(node2)
				.setSourceStage(node1));
		// Graph has a node and a dangling edge
		mappingGraph = mappingGraphService.retrieve(mappingGraph.getId()).orElseThrow();
		assertValidation(mappingGraph, "Edge not connected to Destination node in Account Map pipeline");
		edgeRepo.delete(edge1);
		Connector connector = createConnector();

		EntityDefinition sfdcAccount = entityProxyRepo.findByConnectorIdAndApiName(connector.getId(),"Account").get();

		mappingGraph = mappingGraphService.retrieve(mappingGraph.getId()).orElseThrow();
		assertValidation(mappingGraph, "No edges in Account Map pipeline");

		var destNode = nodeRepo.save(new MappingNode().setName("dest").setApiName("dest").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySinkNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));
		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.any())
				.setOutput(OutputPort.any())
				.setDestinationStage(destNode)
				.setSourceStage(coreNode));
		mappingGraph = mappingGraphService.retrieve(mappingGraph.getId()).orElseThrow();
		//A graph with just core & dest is valid
		mappingGraph.validate();

		MappingGraph accountGraph = mappingGraphService.retrieveDraftEntityGraph(coreAccount.getId()).get();
		assertEquals(1,accountGraph.getSources().count());
		assertEquals(1,accountGraph.getSinks().count());
		assertEquals(2,accountGraph.getEdges().size());
		var sinkToCore =new Edge().setSourceStage(accountGraph.getSinks().findFirst().get())
				.setDestinationStage(accountGraph.getCoreNode())
				.setInput(InputPort.any())
				.setOutput(OutputPort.any())
				.setGraphId(accountGraph.getId());
		sinkToCore.setId(ObjectId.get().toHexString());
		accountGraph.getEdges().add(sinkToCore);
		MappingGraph savedGraph = mappingGraphService.upsertEntityGraph(accountGraph);
		assertValidation(savedGraph, "There is an invalid infinite loop in pipeline 'Account' caused by an edge from node 'Account'");

		// multiple core nodes
		var coreNode2 = nodeRepo.save(new MappingNode().setName("Zzzz2").setApiName("Zzzz2").setScope(Scope.ENTITY)
				.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreAccount))
				.setMappingGraphId(mappingGraph.getId()));
		// Graph has nodes, but not connected
		mappingGraph = mappingGraphService.retrieve(mappingGraph.getId()).orElseThrow();
		assertValidation(mappingGraph, "Pipeline Account Map has multiple core nodes");
	}
	
	@Test
	public void graphValidationsCycle() {
		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));
		sfdc.getConfig().put("maskCharacter", "*");
		MappingGraph mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map2").setScope(Scope.ENTITY).setTargetId("entityId"));

		var functionNode = nodeRepo.save(new MappingNode().setName("Save1").setApiName("Save1").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(mappingGraph.getId()));
		Connector connector = createConnector();

		EntityDefinition sfdcAccount = entityProxyRepo.findByConnectorIdAndApiName(connector.getId(),"Account").get();
		EntityDefinition coreAccount= entityProxyRepo.findByConnectorIdAndApiName(connectorService.findSyncariConnector().getId(),"account").get();

		var coreNode = nodeRepo.save(new MappingNode().setName("Zzzz1").setApiName("Zzzz1").setScope(Scope.ENTITY)
				.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreAccount))
				.setMappingGraphId(mappingGraph.getId()));

		var sourceNode = nodeRepo.save(new MappingNode().setName("dest1").setApiName("dest1").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));
		//Source to Function
		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(functionNode)
				.setSourceStage(sourceNode));
		//Function to Source
		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(sourceNode)
				.setSourceStage(functionNode));
		//Function to Core
		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(coreNode)
				.setSourceStage(functionNode));
		
		mappingGraph = mappingGraphService.retrieve(mappingGraph.getId()).orElseThrow();
		try {
			mappingGraph.validate();
			fail();
		}catch (SyncariValidationException e) {
			assertEquals("There is an invalid infinite loop in pipeline 'Account Map2' caused by an edge from node 'Save1'", e.getMessage());
		}
	}
	
	//@Test
	public void version() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph entityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity);
		MappingGraph versionGraph = mappingGraphService.createVersion(entityGraph, Version.builder()
				.actionType(ActionType.Manual)
				.id(new ObjectId().toHexString())
    			.name("V1")
    			.summary("V1 Summary")
    			.numberOfChanges(0)
				.build());
		assertNotNull(versionGraph);
		assertNotNull(versionGraph.getVersionInfo());
		assertEquals(ActionType.Manual, versionGraph.getVersionInfo().getActionType());
		assertEquals(1, versionGraph.getVersionInfo().getVersionNumber().intValue());
		assertEquals("V1", versionGraph.getVersionInfo().getName());
		
		var versions = mappingGraphService.getVersions(syncariEntity.getId());
		assertNotNull(versions);
		assertEquals(1, versions.size());
		assertEquals("V1", versions.get(0).getVersionInfo().getName());
		
		var res = mappingGraphService.restoreEntityDraft(syncariEntity.getId(), versionGraph.getVersionInfo().getId());
		assertNotNull(res);
		assertEquals("1", res.get("version"));
		assertEquals("V1", res.get("name"));
		
	}

	@Test
	public void populateNodesTest() {
		var syncariConnector =connectorService.getSyncariConnector();
		var sfdcConnector = createConnector();
		schemaService.activateMapping(sfdcConnector);
		EntityDefinition coreAccount= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(syncariConnector.getId(),"account").get();
		coreAccount = schemaService.getEntity(coreAccount.getId());
		//coreAccount
		//coreAccount.getWatermarkField()
		coreAccount.getField("LastModifiedDate").get().setWatermarkField(true);
		EntityDefinition sfdcAccount= schemaService.getEntity(sfdcConnector.getId(),"Account");
		EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2",sfdcConnector);
		schemaService.save(srcEntity2);

		MappingGraph mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId("entityId"));

		// two simple graphs
		var srcNode1 = nodeRepo.save(new MappingNode().setName("src1").setApiName("src1").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));

		var srcNode2 = nodeRepo.save(new MappingNode().setName("src2").setApiName("src2").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(srcEntity2))
				.setMappingGraphId(mappingGraph.getId()));

		var coreNode = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreAccount))
				.setMappingGraphId(mappingGraph.getId()));

		var destNode = nodeRepo.save(new MappingNode().setName("dest").setApiName("dest").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySinkNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(coreNode)
				.setSourceStage(srcNode1));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(coreNode)
				.setSourceStage(srcNode2));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(destNode)
				.setSourceStage(coreNode));

		List<MappingNode> nodes = mappingGraphService.findNodesByGraphId(mappingGraph.getId());
		assertEquals(4, nodes.size());
		EntitySourceNodeConfig config = nodes.get(0).getTypedConfiguration();
		assertEquals(sfdcAccount.getApiName(), ((EntitySourceNodeConfig)nodes.get(0).getConfiguration()).getEntityDefinition().getApiName());
		assertEquals(srcEntity2.getApiName(), ((EntitySourceNodeConfig)nodes.get(1).getConfiguration()).getEntityDefinition().getApiName());

		List<Edge> edges = mappingGraphService.findEdgesForGraphId(mappingGraph.getId(), nodes);
		assertEquals(3, edges.size());
		assertEquals(srcNode1.getApiName(), edges.get(0).getSourceStage().getApiName());

		//assertEquals("src1", nodes.get(0).getTypedConfiguration());

/*		FunctionDefinition mask = functionDefinitionRepo.findByNameAndScope("mask", Scope.ATTRIBUTE).get();
		FunctionCall sfdc = mask.withParams(ParameterValue.string("a.b", "sfdc"));
		sfdc.getConfig().put("maskCharacter", "*");

		var functionNode = nodeRepo.save(new MappingNode().setName("Save").setApiName("Save").setScope(Scope.ENTITY)
				.setConfiguration(new SimpleFunctionNodeConfig().setFunctionCall(sfdc))
				.setMappingGraphId(mappingGraph.getId()));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(functionNode)
				.setSourceStage(srcNode2));

		assertValidation(mappingGraph, "Source src2 not connected to core node in Account Map pipeline");

		mappingGraphRepo.delete(mappingGraph);

		mappingGraph = mappingGraphRepo
				.save(new MappingGraph().setName("Account Map").setScope(Scope.ENTITY).setTargetId(coreAccount.getId()));

		srcNode1 = nodeRepo.save(new MappingNode().setName("src1").setApiName("src1").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));


		coreNode = nodeRepo.save(new MappingNode().setName("Zzzz").setApiName("Zzzz").setScope(Scope.ENTITY)
				.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(coreAccount))
				.setMappingGraphId(mappingGraph.getId()));

		destNode = nodeRepo.save(new MappingNode().setName("dest").setApiName("dest").setScope(Scope.ENTITY)
				.setConfiguration(new EntitySinkNodeConfig().setEntityDefinition(sfdcAccount))
				.setMappingGraphId(mappingGraph.getId()));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(coreNode)
				.setSourceStage(srcNode1));

		edgeRepo.save(new Edge().setGraphId(mappingGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(destNode)
				.setSourceStage(coreNode));

		MappingGraph attribGraph = mappingGraphService.createDefaultAttributeGraph(coreAccount.getAttributes().get(0));

		var srcAttrib1 = nodeRepo.save(new MappingNode().setName("srcAttrib1").setApiName("srcAttrib1").setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcAccount.getAttributes().get(0)))
				.setMappingGraphId(attribGraph.getId()));

		var srcAttrib2 = nodeRepo.save(new MappingNode().setName("srcAttrib2").setApiName("srcAttrib2").setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSourceNodeConfig().setAttributeDefinition(sfdcAccount.getAttributes().get(0)))
				.setMappingGraphId(attribGraph.getId()));

		var coreAttribNode = nodeRepo.save(new MappingNode().setName("coreAttrib").setApiName("coreAttrib").setScope(Scope.ATTRIBUTE)
				.setConfiguration(new CoreAttributeNodeConfig().setAttributeDefinition(coreAccount.getAttributes().get(0)))
				.setMappingGraphId(attribGraph.getId()));

		var destAttribNode = nodeRepo.save(new MappingNode().setName("destAttrib").setApiName("destAttrib").setScope(Scope.ATTRIBUTE)
				.setConfiguration(new AttributeSinkNodeConfig().setAttributeDefinition(sfdcAccount.getAttributes().get(0)))
				.setMappingGraphId(attribGraph.getId()));

		edgeRepo.save(new Edge().setGraphId(attribGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(coreAttribNode)
				.setSourceStage(srcAttrib1));

		edgeRepo.save(new Edge().setGraphId(attribGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(destAttribNode)
				.setSourceStage(coreAttribNode));

		edgeRepo.save(new Edge().setGraphId(attribGraph.getId()).setInput(InputPort.of(new StringType()))
				.setOutput(OutputPort.any())
				.setDestinationStage(functionNode)
				.setSourceStage(srcAttrib2));

		assertValidation(mappingGraph, String.format("Source srcAttrib1 not connected to core node in About Us pipeline", coreAccount.getAttributes().get(0).getDisplayName()));

		EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1",
				GraphHelper.createConnector("sourceConnector1", "sourceConnectorId1", "sourceConnectorMeta1"));

		EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2",
				GraphHelper.createConnector("sourceConnector2", "sourceConnectorId2", "sourceConnectorMeta2"));

		MappingGraph entityGraph = newGraph(coreAccount)
				.src(srcEntity2, "Source Account2")
				.src(srcEntity1, "Source Account1")
				.function("advancedAttachRecord", "Attach Record", Map.of("attachPredicate", Map.of()))
				.function("filter", "Filter All", Map.of())
				.dest(srcEntity1, "Dest Account")
				.connect("Source Account1", "account")
				.connect("account", "Filter All")
				.connect("Filter All", "Dest Account")
				.connect("Source Account2", "Attach Record")
				.connect("Attach Record", "account").getGraph();

		entityGraph.validate(); // this should work*/
	}
	
	@Test
	public void updateScheduledSources() {
		var originalResyncService = mappingGraphService.resyncService;
		try {
			EntityDefinition syncariEntity = entityProxyRepo
					.findByConnectorId(connectorService.getSyncariConnector().getId()).get(0);

			EntityDefinition sfdcAccount = new EntityDefinition().setApiName("account").setConnectorId("connector1");
			sfdcAccount.setId(ObjectId.get().toHexString());

			MappingGraph mappingGraph = new MappingGraph().setName("Account Map").setScope(Scope.ENTITY)
					.setTargetId(syncariEntity.getId());
			mappingGraph.setId(ObjectId.get().toHexString());
			var mappingNode = new MappingNode().setName(syncariEntity.getDisplayName()).setApiName(syncariEntity.getDisplayName())
					.setScope(Scope.ENTITY)
					.setConfiguration(new CoreEntityNodeConfig().setEntityDefinition(syncariEntity));
			mappingNode.setId(ObjectId.get().toHexString());


			var source1 = new MappingNode().setName("sfdc account").setApiName("account")
					.setScope(Scope.ENTITY)
					.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(sfdcAccount).setSchedule("0 * * * *"));
			mappingGraph.setNodes(List.of(mappingNode, source1));
			Edge edge1 = new Edge().setSourceStage(source1).setDestinationStage(mappingNode).setInput(InputPort.any()).setOutput(OutputPort.any());
			mappingGraph.setEdges(List.of(edge1));
			List<SyncDetail> upstreamWatermarks = watermarkService.getUpstreamWatermarks(syncariEntity.getApiName(), List.of(sfdcAccount.getId()));
			assertTrue(upstreamWatermarks.isEmpty());
			long now = System.currentTimeMillis();
			Optional<SyncStream> streamState = streamRepo.findByGraphId(mappingGraph.getId());
			assertTrue(streamState.isEmpty());

			// Case 1: Incremental Sync without existing watermark - expected: new watermarks should be created for both source entities
			mappingGraphService.updateStreamState(mappingGraph, Optional.of(syncariEntity), false);
			streamState = streamRepo.findByGraphId(mappingGraph.getId());
			assertFalse(streamState.isEmpty());
			assertEquals(SyncStream.Status.READY, streamState.get().getStatus());
			mappingGraphService.updateScheduledSources(mappingGraph);

			upstreamWatermarks = watermarkService.getUpstreamWatermarks(syncariEntity.getApiName(), List.of(sfdcAccount.getId()));

			assertEquals(1, upstreamWatermarks.size());
			assertTrue(upstreamWatermarks.get(0).getNextSyncAt() > 0);

		}finally {
			mappingGraphService.resyncService = originalResyncService;
		}

	}

	@Test
	public void validateSimpleIndexLoop() {

		var orgFeatures = SyncariContext.getInstance().getFeatures();

		try {
			// setup schema and pipeline
			Connector connector = createConnector();
			EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
			EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
			AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
			AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
			MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
			attributeProxyRepo.save(srcNameAttr);
			attributeProxyRepo.save(coreNameAttr);

			// source -> loop -> foreach -> function -> endLoop
			//                -> after -> core

			MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
					.src(srcNameAttr)
					.function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
							"apiName", "output_list", "displayName", "output_list" , "multiValueField", true), "newValue", ""))
					.function("loop","loop",Map.of("option", "index", "startIndex", "1", "endIndex", "4", "loopStart", true))
					.function("forEach","foreach")
					.function("addToList","addToList", Map.of("dataType", "text", "value",
							"{{currentLoop.index}}", "inputList", "{{syncari.temp.output_list}}"))
					.function("endLoop", "endloop", Map.of("loopEnd", true))
					.function("after", "after")
					.function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.output_list}}"))
					.function("first", "first")
					.function("join", "join", Map.of("delimiter", ","))
					.connect(srcNameAttr.getApiName(),"setValue")
					.connect("setValue","loop")
					.connect("loop","foreach")
					.connect("loop","after")
					.connect("foreach","addToList")
					.connect("addToList", "endloop")
					.connect("endloop", "loop")
					.connect("after","findValue")
					.connect("findValue","first")
					.connect("first","join")
					.connect("join",coreNameAttr.getApiName()).getGraph();

			nameAttrGraph.setSettings(new PipelineSettings().setSimpleLoops(true));

			mappingGraphService.validateGraph(nameAttrGraph, coreEntityDef, Map.of(srcEntityDef.getApiName(), srcEntityDef));

		} finally {
			SyncariContext.getInstance().setFeatures(orgFeatures);
		}
	}

	@Test
	public void validateSimpleVariableLoops() {

		var orgFeatures = SyncariContext.getInstance().getFeatures();
		try {
			// setup schema and pipeline
			Connector connector = createConnector();
			EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
			EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
			AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
			AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
			MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
			attributeProxyRepo.save(srcNameAttr);
			attributeProxyRepo.save(coreNameAttr);

			// source -> loop -> foreach -> function -> endLoop
			//                -> after -> core

			MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
					.src(srcNameAttr)
					.function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
							"apiName", "output_list", "displayName", "output_list" , "multiValueField", true), "newValue", ""))
					.function("loop","loop",Map.of("option", "variable", "variable", "{{current_list}}", "loopStart", true))
					.function("forEach","foreach")
					.function("addToList","addToList", Map.of("dataType", "text", "value",
							"{{currentLoop.value}}", "inputList", "{{syncari.temp.output_list}}"))
					.function("endLoop", "endloop", Map.of("loopEnd", true))
					.function("after", "after")
					.function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.output_list}}"))
					.function("first", "first")
					.function("join", "join", Map.of("delimiter", ","))
					.connect(srcNameAttr.getApiName(),"setValue")
					.connect("setValue","loop")
					.connect("loop","foreach")
					.connect("loop","after")
					.connect("foreach","addToList")
					.connect("addToList", "endloop")
					.connect("endloop", "loop")
					.connect("after","findValue")
					.connect("findValue","first")
					.connect("first","join")
					.connect("join",coreNameAttr.getApiName()).getGraph();

			nameAttrGraph.setSettings(new PipelineSettings().setSimpleLoops(true));
			mappingGraphService.validateGraph(nameAttrGraph, coreEntityDef, Map.of(srcEntityDef.getApiName(), srcEntityDef));
		} finally {
			SyncariContext.getInstance().setFeatures(orgFeatures);
		}
	}

	@Test
	public void validateSimpleVariableMapLoops() {

		var orgFeatures = SyncariContext.getInstance().getFeatures();

		try {
			// setup schema and pipeline
			Connector connector = createConnector();
			EntityDefinition coreEntityDef = SchemaHelper.createEntityDef("account", "Account", null);
			EntityDefinition srcEntityDef = SchemaHelper.createEntityDef("Organization", "Organization", connector);
			AttributeDefinition coreNameAttr = SchemaHelper.createAttribute("Name", new StringType(), coreEntityDef.getId());
			AttributeDefinition srcNameAttr = SchemaHelper.createAttribute("AccountName", new StringType(), srcEntityDef.getId());
			MappingGraph entityGraph = GraphHelper.newGraph(coreEntityDef,functionService).src(srcEntityDef).connect("Organization","account").getGraph();
			attributeProxyRepo.save(srcNameAttr);
			attributeProxyRepo.save(coreNameAttr);

			// source -> loop -> foreach -> function -> endLoop
			//                -> after -> core

			LinkedHashMap<String, String> inputMap = new LinkedHashMap<>();
			inputMap.put("first_name", "john");
			inputMap.put("last_name", "doe");
			inputMap.put("email", "john.doe@syncari.com");
			MappingGraph nameAttrGraph = GraphHelper.newGraph(coreNameAttr,functionService)
					.src(srcNameAttr)
					.function("setValue", "setValue", Map.of("setValueField", Map.of("type", "temporary", "dataType", "text",
							"apiName", "output_list", "displayName", "output_list","multiValueField", true), "newValue", ""))
					.function("loop","loop",Map.of("option", "variable", "variable", "{{input_map}}", "loopStart", true))
					.function("forEach","foreach")
					.function("addToList","addToList", Map.of("dataType", "text", "value",
							"{{currentLoop.value}}", "inputList", "{{syncari.temp.output_list}}"))
					.function("endLoop", "endloop", Map.of("loopEnd", true))
					.function("after", "after")
					.function("findValue", "findValue", Map.of("fieldName", "{{syncari.temp.output_list}}"))
					.function("first", "first")
					.function("join", "join", Map.of("delimiter", ","))
					.connect(srcNameAttr.getApiName(),"setValue")
					.connect("setValue","loop")
					.connect("loop","foreach")
					.connect("loop","after")
					.connect("foreach","addToList")
					.connect("addToList", "endloop")
					.connect("endloop", "loop")
					.connect("after","findValue")
					.connect("findValue","first")
					.connect("first", "join")
					.connect("join",coreNameAttr.getApiName()).getGraph();

			nameAttrGraph.setSettings(new PipelineSettings().setSimpleLoops(true));
			mappingGraphService.validateGraph(nameAttrGraph, coreEntityDef, Map.of(srcEntityDef.getApiName(), srcEntityDef));
		} finally {
			SyncariContext.getInstance().setFeatures(orgFeatures);
		}
	}


	private Connector createConnectorForReadyOnly() {
		EntitySchema entitySchema = new EntitySchema("Account", "Account");
		var nameAttrib = new AttributeSchema("Name","string").setDisplayName("Account Name");
		nameAttrib.setNillable(false);
		entitySchema.addField(nameAttrib);
		var descAttrib = new AttributeSchema("Description","string").setDisplayName("Description");
		descAttrib.setNillable(true);
		entitySchema.addField(descAttrib);
		return createConnectorWithSchema(List.of(entitySchema));
	}

    private Connector createMySQLConnectorForTest() {
		EntitySchema entitySchema = new EntitySchema("Account", "Account");
        var idAttrib = new AttributeSchema("Id","string").setDisplayName("Account Id");
		idAttrib.setNillable(false);
		entitySchema.addField(idAttrib);
        var updatedAtAttrib = new AttributeSchema("LastModifiedDate","datetime").setDisplayName("Last Modified Date");
		updatedAtAttrib.setNillable(false);
		entitySchema.addField(updatedAtAttrib);
		var nameAttrib = new AttributeSchema("Name","string").setDisplayName("Account Name");
		nameAttrib.setNillable(false);
		entitySchema.addField(nameAttrib);
		var descAttrib = new AttributeSchema("Description","string").setDisplayName("Description");
		descAttrib.setNillable(true);
		entitySchema.addField(descAttrib);
		return createMysqlConnectorWithSchema(List.of(entitySchema));
	}
    
	private AttributeDefinition getId(EntityDefinition sfdcAccount) {
		AttributeDefinition id = new AttributeDefinition().setApiName("Id").setDataType(new StringType())
				.setDisplayName("Id").setStatus(Status.ACTIVE)
				.setIdField(true).setEntityId(sfdcAccount.getId());
		attributeProxyRepo.save(id);
		return id;
	}

	@Test
	public void testInitializeAttrGraphWithLock() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		Connector sfdcConnector = createConnector();
		EntityDefinition synapseEntity = schemaService.getEntity(sfdcConnector.getId(), "Account");
		AttributeDefinition synapseAttribute = synapseEntity.getAttributes().stream().filter(a -> a.getApiName().equals("Name")).findFirst().get();

		Map<String, AttributeDefinition> syncariNameToDef = new HashMap<>();
		syncariEntity.getAttributes().forEach(e -> syncariNameToDef.put(e.getApiName().toLowerCase(), e));
		Map<String, AttributeDefinition> synapseNameToDef = new HashMap<>();
		synapseEntity.getAttributes().forEach(e -> synapseNameToDef.put(e.getApiName().toLowerCase(), e));

		mappingGraphService.initializeAttrGraph(syncariEntity, synapseEntity, synapseAttribute, syncariNameToDef, synapseNameToDef, Optional.empty());

		AttributeDefinition syncariAttribute = syncariEntity.getAttributes().stream().filter(a -> a.getApiName().equals("Name")).findFirst().get();
		Optional<MappingGraph> attributeGraph = mappingGraphService.retrieveAttributeGraph(syncariAttribute.getId());
		assertTrue(attributeGraph.isPresent());
	}

	@Test
	public void isGraphLockedReturnsFalseForUnlockedGraph() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph entityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());

		assertFalse(mappingGraphService.isGraphLocked(entityGraph));
	}

	@Test
	public void isGraphLockedReturnsTrueWhenLocked() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph entityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());

		// Lock the graph using the same lock key pattern as MappingGraphService
		String lockId = "entity_" + entityGraph.getTargetId();
		String lockOwner = "test_isGraphLocked";
		lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));

		try {
			assertTrue(mappingGraphService.isGraphLocked(entityGraph));
		} finally {
			lockRepo.unlock(lockId, lockOwner);
		}
	}

	@Test
	public void isGraphLockedReturnsFalseAfterUnlock() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph entityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());

		String lockId = "entity_" + entityGraph.getTargetId();
		String lockOwner = "test_isGraphLocked";
		lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));

		assertTrue(mappingGraphService.isGraphLocked(entityGraph));

		lockRepo.unlock(lockId, lockOwner);

		assertFalse(mappingGraphService.isGraphLocked(entityGraph));
	}

	@Test
	public void isGraphLockedReturnsFalseForAttributeGraph() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph attributeGraph = mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());

		// Attribute graphs should never be considered locked (locking only applies to entity graphs)
		assertFalse(mappingGraphService.isGraphLocked(attributeGraph));

		// Even if we try to lock with the same pattern, attribute graphs should return false
		String lockId = "entity_" + attributeGraph.getTargetId();
		String lockOwner = "test_isGraphLocked";
		lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));

		try {
			// isGraphLocked should still return false because it's an attribute graph
			assertFalse(mappingGraphService.isGraphLocked(attributeGraph));
		} finally {
			lockRepo.unlock(lockId, lockOwner);
		}
	}

	@Test
	public void approveDraftFailsWhenGraphIsLocked() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph entityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());

		// Lock the graph
		String lockId = "entity_" + entityGraph.getTargetId();
		String lockOwner = "test_approveDraftFailsWhenLocked";
		lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));

		try {
			mappingGraphService.approveDraft(entityGraph);
			fail("Expected SyncariValidationException when approving a locked graph");
		} catch (Exception e) {
			assertEquals("This draft is currently being approved. Please wait for it to complete.", e.getMessage());
		} finally {
			lockRepo.unlock(lockId, lockOwner);
		}
	}

	@Test
	public void approveDraftSucceedsAfterUnlock() {
		EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
		MappingGraph entityGraph = mappingGraphService.createDefaultEntityGraph(syncariEntity.getId());
		mappingGraphService.createDefaultAttributeGraph(syncariEntity.getAttributes().get(0).getId());

		// Lock and then unlock the graph
		String lockId = "entity_" + entityGraph.getTargetId();
		String lockOwner = "test_approveDraftSucceedsAfterUnlock";
		lockRepo.lock(lockId, lockOwner, Duration.ofMinutes(3));
		lockRepo.unlock(lockId, lockOwner);

		// Should succeed now
		MappingGraph approved = mappingGraphService.approveDraft(entityGraph);
		assertEquals(DraftStatus.APPROVED, approved.getDraftStatus());
	}
}
