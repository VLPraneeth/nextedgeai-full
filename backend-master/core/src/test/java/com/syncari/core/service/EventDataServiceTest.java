package com.syncari.core.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.service.def.WebhookService;
import com.syncari.core.model.*;
import com.syncari.core.repositories.customer.*;
import org.apache.commons.codec.binary.Hex;
import org.bson.types.ObjectId;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;

import com.syncari.connector.Constants;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.EndSystemConfig;
import com.syncari.core.event.Message;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.syncari.GlobalConfigurationRepo;
import com.syncari.utils.TextUtil;

@DirtiesContext
public class EventDataServiceTest extends AbstractSyncariTest {

    @Autowired
    ConnectorService connService;
    @Autowired
    SchemaService schemaService;
	@Autowired
	EndSystemConfig config;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    MappingGraphRepo mappingGraphRepo;
    @Autowired
    EventDataService service;
    @Autowired
    GlobalConfigurationRepo repo;
	@Autowired
	private EdgeRepo edgeRepo;
	@Autowired
	private MappingNodeRepo nodeRepo;
	@Autowired
	EntityDefinitionRepo entityProxyRepo;
    @Autowired
    ConnectorRepo cRepo;
    @Autowired
    ApiErrorLogRepo apiRepo;
	@Autowired
	EventDataRepo eventDataRepo;

	String data = "[\n"
			+ "  {\n"
			+ "    \"eventId\": 2270875301,\n"
			+ "    \"subscriptionId\": 1238319,\n"
			+ "    \"portalId\": 6196729,\n"
			+ "    \"appId\": 204106,\n"
			+ "    \"occurredAt\": 1632175988258,\n"
			+ "    \"subscriptionType\": \"company.deletion\",\n"
			+ "    \"attemptNumber\": 0,\n"
			+ "    \"objectId\": 6261424409,\n"
			+ "    \"changeFlag\": \"DELETED\",\n"
			+ "    \"changeSource\": \"API\"\n"
			+ "  }\n"
			+ "]";
	
	@Override
	public void tearDown() {
		super.tearDown();
		repo.deleteAll();
		resetRepos(mappingGraphRepo);
	}

	@Test
	public void deletedOrgOrInstanceConsumedWithoutError(){
		final EventDataService eventDataService = new EventDataService();
		eventDataService.metaService = mock(ConnectorMetadataService.class);
		eventDataService.factory = mock(DataServiceFactory.class);
		eventDataService.subscriptionService = service.subscriptionService;
		eventDataService.globalRepo = mock(GlobalConfigurationRepo.class);
		when(eventDataService.metaService.findByName(anyString())).thenReturn(Optional.of(new ConnectorMetadata()));
		final WebhookService webhookService = mock(WebhookService.class);
		when(eventDataService.factory.getWebhookService(any(ConnectorMetadata.class))).thenReturn(webhookService);
		final GlobalConfiguration globalConfiguration = new GlobalConfiguration();
		globalConfiguration.setValue(List.of("invalid_value"));
		when(eventDataService.globalRepo.findByKey(anyString())).thenReturn(Optional.of(globalConfiguration));
		when(webhookService.extractIdentifier(any())).thenReturn("invalid").thenThrow(new RuntimeException("Fail"));

		try {
			eventDataService.handleEvent(new Message().setEvent(new Event().setDetails(
					Map.of("synapse", "hubspot","body","body")
			)));
		}catch(Exception e){
			fail("Not supposed to fail on bad org ids");
		}
		try {
			eventDataService.handleEvent(new Message().setEvent(new Event().setDetails(Map.of("synapse", "hubspot","body","body"))));
			fail();
		}catch(RuntimeException e){
			assertEquals("Fail",e.getMessage());
		}

	}
	@Ignore
    @Test
    public void processSavesData() throws IOException {
		Connector connector = getConnector();
	    
		Map<String, String> headers = Map.of("x-hubspot-signature", Hex.encodeHexString(TextUtil.getSha(config.getHubspotTestClientSecret().concat(data))));
		Message msg = new Message().setEvent(new Event().setDetails(Map.of("synapse", "hubspot", "body", data, "headers", headers)));

        
        EntityDefinition coreAccount = schemaService.getSyncariEntityByName("account").get();
		Optional<MappingGraph> optionalEntityMappingGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId());
		if (optionalEntityMappingGraph.isEmpty()){
			mappingGraphService.createDefaultEntityGraph(coreAccount);
		}

		Optional<MappingGraph> optionalAttrMappingGraph  = mappingGraphService.retrieveAttributeGraph(coreAccount.getAttributes().get(0).getId());
		if (optionalAttrMappingGraph.isEmpty()){
			mappingGraphService.createDefaultAttributeGraph(coreAccount.getAttributes().get(0).getId());
		}

		EntityDefinition hubCompany= entityProxyRepo.findActiveEntityByConnectorIdAndApiName(connector.getId(),"company").get();
		MappingGraph accountGraph = mappingGraphService.retrieveEntityGraph(coreAccount.getId()).get();
		var sfdcAccountNode = nodeRepo.save(new MappingNode()
				.setApiName(hubCompany.getApiName())
				.setName(hubCompany.getApiName()).setScope(Scope.ENTITY)
				.setConfiguration(new EntitySourceNodeConfig().setEntityDefinition(hubCompany))
				.setMappingGraphId(accountGraph.getId()));
		var coreAccountNode = accountGraph.getCoreNode();
		var sfdcToCoreEdge = edgeRepo.save(
				new Edge().setGraphId(accountGraph.getId()).setInput(coreAccountNode.getConfiguration().getInputPorts().get(0))
						.setOutput(coreAccountNode.getConfiguration().getOutputPorts().get(0)).setDestinationStage(sfdcAccountNode)
						.setSourceStage(sfdcAccountNode));
		List<MappingGraph> graphsWithSourceOrSink = mappingGraphService.findEntityGraphsWithSourceOrSink(hubCompany.getId());
		assertTrue(!graphsWithSourceOrSink.isEmpty());
		mappingGraphService.approveDraft(accountGraph);
		
		service.handleEvent(msg);
		
		assertEquals(1,
				service.findAllByConnectorId(connector.getId(), PageRequest.of(0, 100))
						.getContent().size());
		assertEquals(0, apiRepo.count());

		// test with params
		Map<String, String> params = Map.of("entity", "account");
		msg = new Message().setEvent(new Event().setDetails(Map.of("synapse", "hubspot", "body", data, "headers", headers, "params", params)));
		service.handleEvent(msg);
		assertEquals(2,
				service.findAllByConnectorId(connector.getId(), PageRequest.of(0, 100))
						.getContent().size());
		assertEquals(0, apiRepo.count());
    }

    @Ignore
    @Test
    public void validations() throws IOException {
    	Connector connector = getConnector();

		Map<String, String> headers = Map.of("x-hubspot-signature", Hex.encodeHexString(TextUtil.getSha("invalid".concat(data))));
		Message msg = new Message().setEvent(new Event().setDetails(Map.of("synapse", "hubspot", "body", data, "headers", headers)));
        service.handleEvent(msg);
        assertEquals(1, apiRepo.count());

		// test with params but invalid header.
		Map<String, String> params = Map.of("entity", "account");
		msg = new Message().setEvent(new Event().setDetails(Map.of("synapse", "hubspot", "body", data, "headers", headers, "params", params)));
		service.handleEvent(msg);
		assertEquals(2, apiRepo.count());
    }

	@Test
	public void deleteTest() throws IOException {
		String connectorId = ObjectId.get().toHexString();
		String graphId = ObjectId.get().toHexString();

		String currentBatchId = UUID.randomUUID().toString();
		var eventData = IntStream.range(0, 50).mapToObj(i -> {
			var entityData = new EntityData().setId(ObjectId.get().toHexString()).setName("Name " + i);
			return new EventData().setGraphId(graphId).setConnectorId(connectorId).setBatchId(currentBatchId).setOperation(Operation.update).setData(entityData);
		}).collect(Collectors.toList());

		service.save(eventData);
		service.deleteByBatchId(currentBatchId);

		var eventPage = eventDataRepo.findAllByBatchId(currentBatchId, PageRequest.of(0, 100));
		assertTrue(!eventPage.hasContent());

		eventData = IntStream.range(0, 102).mapToObj(i -> {
			var entityData = new EntityData().setId(ObjectId.get().toHexString()).setName("Name " + i);
			return new EventData().setGraphId(graphId).setConnectorId(connectorId).setBatchId(currentBatchId).setOperation(Operation.update).setData(entityData);
		}).collect(Collectors.toList());

		service.save(eventData);
		service.deleteByBatchId(currentBatchId);

		eventPage = eventDataRepo.findAllByBatchId(currentBatchId, PageRequest.of(0, 100));
		assertTrue(!eventPage.hasContent());

		eventData = IntStream.range(0, 200).mapToObj(i -> {
			var entityData = new EntityData().setId(ObjectId.get().toHexString()).setName("Name " + i);
			return new EventData().setGraphId(graphId).setConnectorId(connectorId).setBatchId(currentBatchId).setOperation(Operation.update).setData(entityData);
		}).collect(Collectors.toList());

		service.save(eventData);
		service.deleteByBatchId(currentBatchId);

		eventPage = eventDataRepo.findAllByBatchId(currentBatchId, PageRequest.of(0, 100));
		assertTrue(!eventPage.hasContent());


		eventData = IntStream.range(0, 201).mapToObj(i -> {
			var entityData = new EntityData().setId(ObjectId.get().toHexString()).setName("Name " + i);
			return new EventData().setGraphId(graphId).setConnectorId(connectorId).setBatchId(currentBatchId).setOperation(Operation.update).setData(entityData);
		}).collect(Collectors.toList());

		service.save(eventData);
		service.deleteByBatchId(currentBatchId);

		eventPage = eventDataRepo.findAllByBatchId(currentBatchId, PageRequest.of(0, 100));
		assertTrue(!eventPage.hasContent());
	}
    
	@Test
	public void testProcessAssociationDeletionWebhooks_WithPlaceholderId_NoMatchingAssociations() {
		// Test that placeholder IDs are detected and processed (but no matches found in DB)
		Connector connector = getConnector();

		EntityData associationData = new EntityData("contact_association");
		associationData.setId("174442225803-39883298054-company-UNKNOWN-UNKNOWN");
		associationData.setConnectorId(connector.getId());
		associationData.setDeleted(true);
		associationData.addValue("fromObjectId", "174442225803");
		associationData.addValue("toObjectId", "39883298054");
		associationData.addValue("toObjectType", "company");
		associationData.setLastModified(System.currentTimeMillis());

		com.syncari.core.model.EventData eventData = new com.syncari.core.model.EventData();
		eventData.setData(associationData);
		eventData.setOperation(Operation.delete);

		List<com.syncari.core.model.EventData> input = List.of(eventData);
		List<com.syncari.core.model.EventData> result = service.processAssociationDeletionWebhooks(input, connector);

		// Since no matching associations exist in DB, result should be empty (placeholder filtered out)
		assertEquals(0, result.size());
	}

	@Test
	public void testProcessAssociationDeletionWebhooks_WithoutPlaceholderId_PassedThrough() {
		// Test that normal events (without placeholder ID) are passed through unchanged
		Connector connector = getConnector();

		EntityData normalData = new EntityData("contact");
		normalData.setId("12345");
		normalData.setConnectorId(connector.getId());
		normalData.setDeleted(false);

		com.syncari.core.model.EventData eventData = new com.syncari.core.model.EventData();
		eventData.setData(normalData);
		eventData.setOperation(Operation.update);

		List<com.syncari.core.model.EventData> input = List.of(eventData);
		List<com.syncari.core.model.EventData> result = service.processAssociationDeletionWebhooks(input, connector);

		// Normal events should pass through unchanged
		assertEquals(1, result.size());
		assertEquals("12345", result.get(0).getData().getId());
		assertEquals("contact", result.get(0).getData().getName());
	}

	@Test
	public void testProcessAssociationDeletionWebhooks_NonAssociationEntity_PassedThrough() {
		// Test that non-association entities with UNKNOWN pattern are passed through
		Connector connector = getConnector();

		EntityData normalData = new EntityData("contact");
		normalData.setId("123-456-UNKNOWN-UNKNOWN");  // Has UNKNOWN but not an association
		normalData.setConnectorId(connector.getId());
		normalData.setDeleted(false);

		com.syncari.core.model.EventData eventData = new com.syncari.core.model.EventData();
		eventData.setData(normalData);
		eventData.setOperation(Operation.create);

		List<com.syncari.core.model.EventData> input = List.of(eventData);
		List<com.syncari.core.model.EventData> result = service.processAssociationDeletionWebhooks(input, connector);

		// Non-association entities should pass through even with UNKNOWN pattern
		assertEquals(1, result.size());
		assertEquals("123-456-UNKNOWN-UNKNOWN", result.get(0).getData().getId());
	}

	@Test
	public void testProcessAssociationDeletionWebhooks_MissingFields_KeptInResult() {
		// Test that association webhooks with missing required fields are kept in result
		Connector connector = getConnector();

		EntityData incompleteData = new EntityData("contact_association");
		incompleteData.setId("111-222-company-UNKNOWN-UNKNOWN");
		incompleteData.setConnectorId(connector.getId());
		incompleteData.setDeleted(true);
		// Missing fromObjectId, toObjectId, toObjectType
		incompleteData.setLastModified(System.currentTimeMillis());

		com.syncari.core.model.EventData eventData = new com.syncari.core.model.EventData();
		eventData.setData(incompleteData);
		eventData.setOperation(Operation.delete);

		List<com.syncari.core.model.EventData> input = List.of(eventData);
		List<com.syncari.core.model.EventData> result = service.processAssociationDeletionWebhooks(input, connector);

		// Incomplete data should be kept (logged as warning but not dropped)
		assertEquals(1, result.size());
		assertEquals("111-222-company-UNKNOWN-UNKNOWN", result.get(0).getData().getId());
	}

	@Test
	public void testProcessAssociationDeletionWebhooks_MixedEvents() {
		// Test processing a mix of association and non-association events
		Connector connector = getConnector();

		// Association event with placeholder
		EntityData associationData = new EntityData("deal_association");
		associationData.setId("999-888-contact-UNKNOWN-UNKNOWN");
		associationData.setConnectorId(connector.getId());
		associationData.setDeleted(true);
		associationData.addValue("fromObjectId", "999");
		associationData.addValue("toObjectId", "888");
		associationData.addValue("toObjectType", "contact");

		// Normal contact event
		EntityData contactData = new EntityData("contact");
		contactData.setId("12345");
		contactData.setConnectorId(connector.getId());

		// Normal company event
		EntityData companyData = new EntityData("company");
		companyData.setId("67890");
		companyData.setConnectorId(connector.getId());

		com.syncari.core.model.EventData event1 = new com.syncari.core.model.EventData();
		event1.setData(associationData);
		event1.setOperation(Operation.delete);

		com.syncari.core.model.EventData event2 = new com.syncari.core.model.EventData();
		event2.setData(contactData);
		event2.setOperation(Operation.update);

		com.syncari.core.model.EventData event3 = new com.syncari.core.model.EventData();
		event3.setData(companyData);
		event3.setOperation(Operation.create);

		List<com.syncari.core.model.EventData> input = List.of(event1, event2, event3);
		List<com.syncari.core.model.EventData> result = service.processAssociationDeletionWebhooks(input, connector);

		// Association with no match should be filtered out, others pass through
		assertEquals(2, result.size());
		assertEquals("12345", result.get(0).getData().getId());
		assertEquals("67890", result.get(1).getData().getId());
	}

	@Test
	public void testProcessAssociationDeletionWebhooks_MultipleMatchingAssociations() {
		// Test the scenario where one deletion webhook matches multiple associations
		// with different typeIds (e.g., different types of relationships between the same objects)
		Connector connector = getConnector();

		// Simulating: Contact 100 -> Company 200 with multiple relationship types
		String fromId = "100";
		String toId = "200";
		String toType = "company";

		// Mock data: Multiple association records with same from/to but different typeIds
		// Association 1: Primary Contact relationship (typeId: 1)
		Map<String, Object> association1 = new HashMap<>();
		association1.put("contact_association_id", fromId + "-" + toId + "-" + toType + "-HUBSPOT_DEFINED-1");
		association1.put("fromObjectId", fromId);
		association1.put("toObjectId", toId);
		association1.put("fromObjectType", "contact");
		association1.put("toObjectType", toType);
		association1.put("category", "HUBSPOT_DEFINED");
		association1.put("typeId", "1");
		association1.put("label", "Primary");
		association1.put("isDeleted", false);

		// Association 2: Decision Maker relationship (typeId: 2)
		Map<String, Object> association2 = new HashMap<>();
		association2.put("contact_association_id", fromId + "-" + toId + "-" + toType + "-HUBSPOT_DEFINED-2");
		association2.put("fromObjectId", fromId);
		association2.put("toObjectId", toId);
		association2.put("fromObjectType", "contact");
		association2.put("toObjectType", toType);
		association2.put("category", "HUBSPOT_DEFINED");
		association2.put("typeId", "279");
		association2.put("label", "Decision Maker");
		association2.put("isDeleted", false);

		// Association 3: Billing Contact relationship (typeId: 3)
		Map<String, Object> association3 = new HashMap<>();
		association3.put("contact_association_id", fromId + "-" + toId + "-" + toType + "-HUBSPOT_DEFINED-3");
		association3.put("fromObjectId", fromId);
		association3.put("toObjectId", toId);
		association3.put("fromObjectType", "contact");
		association3.put("toObjectType", toType);
		association3.put("category", "HUBSPOT_DEFINED");
		association3.put("typeId", "3");
		association3.put("label", "Billing Contact");
		association3.put("isDeleted", false);

		List<Map<String, Object>> mockAssociations = List.of(association1, association2, association3);

		// Mock the repository to return these associations
		EntityRepo mockEntityRepo = mock(EntityRepo.class);
		when(mockEntityRepo.findAssociationsByFields("contact_association", fromId, toId, toType))
				.thenReturn(mockAssociations);

		// Create a test instance with mocked EntityRepo
		EventDataService testService = new EventDataService();
		testService.entityRepo = mockEntityRepo;

		// Create webhook event with placeholder ID (HubSpot doesn't provide specific typeId in webhook)
		EntityData webhookData = new EntityData("contact_association");
		webhookData.setId(fromId + "-" + toId + "-" + toType + "-UNKNOWN-UNKNOWN");
		webhookData.setConnectorId(connector.getId());
		webhookData.setDeleted(true);
		webhookData.setLastModified(System.currentTimeMillis());
		webhookData.addValue("fromObjectId", fromId);
		webhookData.addValue("toObjectId", toId);
		webhookData.addValue("toObjectType", toType);
		webhookData.addValue("fromObjectType", "contact");

		com.syncari.core.model.EventData event = new com.syncari.core.model.EventData();
		event.setData(webhookData);
		event.setOperation(Operation.delete);

		List<com.syncari.core.model.EventData> input = List.of(event);

		// Process the webhook
		List<com.syncari.core.model.EventData> result = testService.processAssociationDeletionWebhooks(input, connector);

		// Verify: Should create 3 deletion events (one for each matching association)
		assertEquals("Should create deletion events for all 3 matching associations", 3, result.size());

		// Verify all events are deletions
		for (com.syncari.core.model.EventData resultEvent : result) {
			assertEquals(Operation.delete, resultEvent.getOperation());
			assertTrue(resultEvent.getData().isDeleted());
			assertEquals("contact_association", resultEvent.getData().getName());
			assertEquals(fromId, resultEvent.getData().getValue("fromObjectId"));
			assertEquals(toId, resultEvent.getData().getValue("toObjectId"));
			assertEquals(toType, resultEvent.getData().getValue("toObjectType"));
		}

		// Verify each has a unique ID corresponding to the actual association IDs
		Set<String> resultIds = result.stream()
				.map(e -> e.getData().getId())
				.collect(Collectors.toSet());

		assertEquals("All result IDs should be unique", 3, resultIds.size());
		assertTrue(resultIds.contains(fromId + "-" + toId + "-" + toType + "-HUBSPOT_DEFINED-1"));
		assertTrue(resultIds.contains(fromId + "-" + toId + "-" + toType + "-HUBSPOT_DEFINED-2"));
		assertTrue(resultIds.contains(fromId + "-" + toId + "-" + toType + "-HUBSPOT_DEFINED-3"));
	}

	private Connector getConnector() {
		Connector connector = new Connector("hubspot", connService.describe(Constants.HUBSPOT).getId(),
				"https://api.hubapi.com");
		connector.setMetaConfig(Map.of("portalId", "6196729"));
		connector.getAuthConfig().setClientId(config.getHubspotTestClientId()).setClientSecret(config.getHubspotTestClientSecret()).setRefreshToken(config.getHubspotTestClientRefreshToken()).setExpiresIn("0");
		connector = connService.save(connector);
		connector = connService.refreshAuthentication(connector);
		connector = connService.save(connector);
		connService.authenticated(connector.getId());
		connService.activate(connector.getId());
		connService.createWebhookConfig(connector);
		return connector;
	}

}
