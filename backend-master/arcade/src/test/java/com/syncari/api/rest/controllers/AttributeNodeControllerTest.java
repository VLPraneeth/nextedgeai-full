package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import com.syncari.connector.config.AuthConfig;
import com.syncari.core.repositories.customer.*;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.syncari.core.model.Connector;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.KeyValue;

public class AttributeNodeControllerTest extends AbstractSyncariTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	AttributeNodeController controller;
	@Autowired
	PipelineController entityNodeController;

	@Autowired
	MappingGraphService graphService;

	@Autowired
	MappingGraphRepo graphRepo;

	@Autowired
	EntityDefinitionRepo entityProxyRepo;

	@Autowired
	AttributeRepo attributeProxyRepo;

	@Autowired
	ConnectorRepo connectorRepo;

	@Autowired
	SchemaService schemaService;

	@Autowired
	ObjectMapper mapper;
	private Connector connector;

	@Autowired
	private ConnectorService connectorService;
	@Autowired
	private EndSystemConfig config;

    @Autowired
    MappingGraphRepo mappingGraphRepo;

    @Autowired
    private MappingNodeRepo nodeRepo;
    @Autowired
    private EdgeRepo edgeRepo;

	@Override
	public void setUp() {
		super.setUp();
		resetRepos(connectorRepo, entityProxyRepo, mappingGraphRepo, nodeRepo, edgeRepo);

		if (connector == null ) {
			connector = new Connector("attributenode", connectorService.describe("zendesk").getId(), "https://d3v-syncari.zendesk.com");
			connector.setAuthConfig(getAuthCOnfig());
			connector = connectorService.save(connector);
			connectorService.authenticated(connector.getId());
			connectorService.activate(connector.getId());
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
		}
	}

    @Override
    public void tearDown() {
    }
    
	@Test
	@WithMockUser(username = "admin", authorities = { WRITE_STUDIO, READ_STUDIO })
	public void listAttributeNodes() throws Exception {

		EntityDef entityDef = schemaService.getSyncariSchema().findEntityByName("account").get();
		var sfdcAccount = schemaService.getEntity(connector.getId(), "organization");

		AttributeDef attributeDef = entityDef.getFields().stream().filter(a->a.getApiName().equals("Name")).findFirst().get();
		var graph0 = entityNodeController.getEntityPipeline(entityDef.getId());
		assertEquals(entityDef.getDisplayName(), graph0.getName());

		List<KeyValue> availableAttributeNodes = controller
				.getAvailableAttributeNodes(attributeDef.getId());
		var sources = availableAttributeNodes.stream().filter(k -> k.get("type").equals("source")).collect(Collectors.toList());
		var sinks = availableAttributeNodes.stream().filter(k -> k.get("type").equals("sink")).collect(Collectors.toList());
		assertEquals(1, sources.size());
		assertEquals(1, sinks.size());
		var config = (List<KeyValue>) sources.get(0).get("configuration");
		var configValyes = (List<KeyValue>) config.get(4).get("values");
		assertEquals(9, config.size());
		assertEquals(sfdcAccount.getAttributes().size(), configValyes.size());
		var core = availableAttributeNodes.stream().filter(k -> k.get("type").equals("core")).findFirst().get();
		assertEquals(core.get("isCoreNode"),true);
		List<KeyValue> coreConfig = core.get("configuration");
		assertEquals(8, coreConfig.size());

		System.out.println(mapper.writeValueAsString(availableAttributeNodes));

	}

	@Test
	@WithMockUser(username = "admin", authorities = { WRITE_STUDIO, READ_STUDIO })
	public void attributeLabelWithApiName() {
		EntityDef entityDef = schemaService.getSyncariSchema().findEntityByName("account").get();
		AttributeDef attributeDef = entityDef.getFields().stream().filter(a->a.getApiName().equals("Name")).findFirst().get();

		List<KeyValue> availableAttributeNodes = controller.getAvailableAttributeNodes(attributeDef.getId());
		var sources = availableAttributeNodes.stream().filter(k -> k.get("type").equals("source")).collect(Collectors.toList());
		var sinks = availableAttributeNodes.stream().filter(k -> k.get("type").equals("sink")).collect(Collectors.toList());

		var config = (List<KeyValue>) sources.get(0).get("configuration");
		var configValues = (List<KeyValue>) config.get(4).get("values");
		assertEquals(configValues.get(3).get("label"), "Domain Names (domain_names)");

		var sinkConfig = (List<KeyValue>) sinks.get(0).get("configuration");
		var sinkConfigValues = (List<KeyValue>) sinkConfig.get(4).get("values");
		assertEquals(sinkConfigValues.get(3).get("label"), "Domain Names (domain_names)");

	}

	private AuthConfig getAuthCOnfig() {
		AuthConfig authConfig = new AuthConfig();
		authConfig.setToken("dev@syncari.com/token");
		authConfig.setClientSecret(System.getenv().getOrDefault("TEST_CLIENT_SECRET", "REPLACE_ME"));
		return authConfig;
	}
}

