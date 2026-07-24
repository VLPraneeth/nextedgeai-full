package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.syncari.api.rest.controllers.data.ConnectorMetadataDTO;
import com.syncari.api.rest.controllers.data.ConnectoryEntityNodeDTO;
import com.syncari.core.model.Connector;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.restutils.data.PortDTO;
import com.syncari.restutils.data.PortType;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.junit.Assert.*;

@Slf4j
public class EntityNodeControllerTest extends AbstractSyncariTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	EntityNodeController controller;

	@Autowired
	MappingGraphService graphService;

	@Autowired
	EntityDefinitionRepo entityProxyRepo;

	@Autowired
	AttributeRepo attributeProxyRepo;

	@Autowired
	ConnectorRepo connectorRepo;

	@Autowired
    private MappingNodeRepo nodeRepo;
	
    @Autowired
    private EdgeRepo edgeRepo;

	@Autowired
	FunctionService functionService;

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
	private GraphTransformer transformer;

	@Override
	public void setUp() {

		super.setUp();

		resetRepos(connectorRepo, entityProxyRepo, mappingGraphRepo, nodeRepo, edgeRepo);

		connector = new Connector("sfdc1", connectorService.describe("salesforce").getId(), config.getSalesforceUrl(),
				config.getUser(), config.getPassword());
		connector.getAuthConfig().setToken(config.getToken());
		connector = connectorService.save(connector);
		connectorService.authenticated(connector.getId());
		connectorService.activate(connector.getId());
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
	}

    @Override
    public void tearDown() {
    }
    
	@Test
	@Ignore
	@WithMockUser(username = "admin", authorities = { WRITE_STUDIO, READ_STUDIO })
	public void listConnectorEntities() throws Exception {
		EntityDef entityDef = schemaService.getSyncariSchema().findEntityByName("user").get();
		List<ConnectoryEntityNodeDTO> connectorEntityNodes = controller.getConnectorEntityNodes(entityDef.getId());

		Schema connectorSchema = schemaService.getSchemaFor(connector.getId());

		log.info("Connectors after setup " +  connectorEntityNodes.stream().map(ConnectoryEntityNodeDTO::getName).collect(Collectors.joining(",")));
		assertEquals(4, connectorEntityNodes.size());
		ConnectoryEntityNodeDTO connectoryEntityNodeDTO = connectorEntityNodes.get(2);
		assertFalse(connectoryEntityNodeDTO.isCoreNode());
		assertEquals(connector.getName(), connectoryEntityNodeDTO.getName());
		assertEquals(ConnectorMetadataDTO.getIconURIForDTO(connector.getMetadata()), connectoryEntityNodeDTO.getIconPath());
		assertEquals(connector.getId(), connectoryEntityNodeDTO.getId());
		List<KeyValue> configuration = connectoryEntityNodeDTO.getConfiguration();
		assertEquals(12, configuration.size());
		var labelConfig = configuration.get(0);
		assertEquals("{direction} {entity}", labelConfig.get("value"));
		var subLabelConfig = configuration.get(1);
		assertEquals(connector.getName(), subLabelConfig.get("value"));
		var entityListConfig = configuration.get(2);
		var connetorIdConfig = configuration.get(3);
		var directionConfig = configuration.get(4);
		assertFalse((Boolean)entityListConfig.get("implicit"));
		assertFalse((Boolean)directionConfig.get("implicit"));
		assertTrue((Boolean)connetorIdConfig.get("implicit"));
		assertNotNull(configuration.stream().filter(conf -> conf.get("datatype") == "textarea").findAny().orElse(null));
		List<KeyValue> entityPicklist = (List<KeyValue>) entityListConfig.get("values");
		entityPicklist.forEach(entityConfig ->{
			assertEquals(List.of(new PortDTO().setPortType(PortType.OUTPUT).setDatatype("object").setMaxConnections(Integer.MAX_VALUE)),entityConfig.get("outputPorts"));
			assertEquals(List.of(new PortDTO().setPortType(PortType.INPUT).setDatatype("object").setMaxConnections(Integer.MAX_VALUE)),entityConfig.get("inputPorts"));
			assertTrue(entityConfig.containsKey("value"));
			assertTrue(entityConfig.containsKey("label"));
		});

		assertEquals(connectorSchema.getEntities().size(), entityPicklist.size());

//		assertEquals(List.of(new KeyValue().set("value", "ENTITY_SOURCE").set("label", "Sync From").set("icon","/icons/pull.png"),
//		        new KeyValue().set("value", "ENTITY_SINK").set("label", "Sync To").set("icon","/icons/push.png")), directionConfig.get("values"));
		assertEquals(connector.getId(), connetorIdConfig.get("value"));

		ConnectoryEntityNodeDTO coreEntityNode = connectorEntityNodes.get(2);
		assertTrue(coreEntityNode.isCoreNode());
		List<KeyValue> coreConfiguration = coreEntityNode.getConfiguration();
		assertEquals(19, coreConfiguration.size());

		System.out.println(mapper.writeValueAsString(connectorEntityNodes));
	}

}
