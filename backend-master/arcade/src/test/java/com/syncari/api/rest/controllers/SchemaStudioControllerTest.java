package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.studio.SchemaResponse;
import com.syncari.core.Link;
import com.syncari.core.Route;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.util.Scope;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import org.mockito.ArgumentCaptor;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static com.syncari.core.utils.GraphHelper.createConnector;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SchemaStudioControllerTest extends AbstractSyncariTest {
	static { System.setProperty("os.arch", "i686_64"); }
	@Autowired
	SchemaStudioController controller;
	@Autowired
	ConnectorService connectService;
	@MockBean
	MappingGraphService mockGraphService;

	@Override
	public void setUp() {
		super.setUp();
	}

	@Override
	public void tearDown() {
	}

	@Test
	@WithMockUser(username = "admin", authorities = { READ_STUDIO })
	public void getSyncariSchemaPublishedEntities() {
		SchemaResponse schema = controller.getSchema(connectService.getSyncariConnector().getId());
		assertEquals(11, schema.getMeta().size());
		// TODO: we have an issue with test cleanup in DatastudioControllerTest.
		// Revisit.
		assertTrue(schema.getData().size() > 5);
		schema.getData().stream().forEach(d -> {
			assertNotNull(d.getPublished());
			assertNull(d.getDraft());
		});
	}

	@Test
	@WithMockUser(username = "admin", authorities = { READ_STUDIO })
	public void getSyncariSchemaPublishedAttributes() {
		SchemaResponse schema = controller.getSchema(connectService.getSyncariConnector().getId());
		schema.getData().stream().forEach(d -> {
			SchemaResponse attrSchema = controller.getSchemaForEntity(d.getPublished().getFields().get("id").toString());
			assertEquals(attrSchema.getMeta().size(), attrSchema.getData().get(0).getPublished().getFields().size());
			assertNotNull(d.getPublished());
			assertNull(d.getDraft());
		});
	}

	@Test
	@WithMockUser(username = "admin", authorities = { READ_STUDIO })
	public void getSynapseSchemaPublishedAttributes() {
		var orgSchemaService = controller.getSchemaService();
		var orgConnService = controller.getConnectorService();
		try {
			Connector syncariConnector = connectService.getSyncariConnector();
			SchemaService mockSchemaService = mock(SchemaService.class);
			ConnectorService mockConnService = mock(ConnectorService.class);
			Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");
			doReturn(Optional.of(connector)).when(mockConnService).find(connector.getId());
			doReturn(syncariConnector).when(mockConnService).getSyncariConnector();

			EntityDefinition synapseEntity = SchemaHelper.createEntityDef("synapseEntity", "synapseEntity", connector);
			var synapseField = SchemaHelper.createAttribute("synapsefield", StringType.VALUE, synapseEntity.getId());
			synapseEntity.addField(synapseField);
			doReturn(synapseEntity).when(mockSchemaService).getEntity(synapseEntity.getId());
			doReturn(List.of(synapseEntity)).when(mockSchemaService).getEntityVersionsByName(connector.getId(),
					synapseEntity.getApiName());

			controller.setConnectorService(mockConnService);
			controller.setSchemaService(mockSchemaService);

			SchemaResponse attrSchema = controller.getSchemaForEntity(synapseEntity.getId());
			assertEquals(attrSchema.getMeta().size(), attrSchema.getData().get(0).getPublished().getFields().size());
			// assert that synapse fields have readOnly flag set
			assertTrue(attrSchema.getMeta().containsKey("isReadonly"));
			assertTrue(attrSchema.getData().get(0).getPublished().getFields().containsKey("isReadonly"));
		} finally {
			controller.setSchemaService(orgSchemaService);
			controller.setConnectorService(orgConnService);
		}
	}

	@Test
	@WithMockUser(username = "admin", authorities = { WRITE_STUDIO, READ_STUDIO })
	public void addAttribToSchema() {
		var orgSchemaService = controller.getSchemaService();
		var orgConnService = controller.getConnectorService();
		try {
			Connector syncariConnector = connectService.getSyncariConnector();
			SchemaService mockSchemaService = mock(SchemaService.class);
			ConnectorService mockConnService = mock(ConnectorService.class);
			Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");
			doReturn(Optional.of(connector)).when(mockConnService).find(connector.getId());
			doReturn(syncariConnector).when(mockConnService).getSyncariConnector();

			EntityDefinition synapseEntity = SchemaHelper.createEntityDef("synapseEntity", "synapseEntity", connector);
			var synapseField = SchemaHelper.createAttribute("synapsefield", StringType.VALUE, synapseEntity.getId());
			synapseEntity.addField(synapseField);
			doReturn(synapseEntity).when(mockSchemaService).getEntity(synapseEntity.getId());
			doReturn(List.of(synapseEntity)).when(mockSchemaService).getEntityVersionsByName(connector.getId(),
					synapseEntity.getApiName());

			controller.setConnectorService(mockConnService);
			controller.setSchemaService(mockSchemaService);

			SchemaResponse attrSchema = controller.getSchemaForEntity(synapseEntity.getId());
			assertEquals(attrSchema.getMeta().size(), attrSchema.getData().get(0).getPublished().getFields().size());
			// assert that synapse fields have readOnly flag set
			assertTrue(attrSchema.getMeta().containsKey("isReadonly"));
			assertTrue(attrSchema.getData().get(0).getPublished().getFields().containsKey("isReadonly"));
			try {
				AttributeDef def = new AttributeDef().setApiName("testApiName").setDataType("string");
                controller.addField(synapseEntity.getId(), def);
                fail();
            } catch (SyncariValidationException exception) {
                assertTrue(exception.getMessage().contains("Display Name"));
            }
        } finally {
            controller.setSchemaService(orgSchemaService);
            controller.setConnectorService(orgConnService);
        }
    }

    @Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void updateFieldTest() {
        var orgSchemaService = controller.getSchemaService();
        var orgConnService = controller.getConnectorService();

        try {
            Connector syncariConnector = connectService.getSyncariConnector();
            SchemaService mockSchemaService = mock(SchemaService.class);
            ConnectorService mockConnService = mock(ConnectorService.class);
            Connector connector = createConnector("connector", "connectorId", "sourceConnectorMetaId");

            doReturn(Optional.of(connector)).when(mockConnService).find(connector.getId());
            doReturn(syncariConnector).when(mockConnService).getSyncariConnector();

            EntityDefinition ent = SchemaHelper.createEntityDef("oldEntity", "oldEntity", connector);
            AttributeDefinition attr = SchemaHelper.createAttribute("sample", StringType.VALUE, ent.getId());
            ent.setAttributes(List.of(attr));

            AttributeDef attr1 = new AttributeDef().setApiName("newTest").setDisplayName("newTest").setDataType("Reference").setReferenceTo("oldEntity").setReferenceTargetField("someRandomFieldName");
            EntityDefinition synapseEntity = SchemaHelper.createEntityDef("newEntity", "newEntity", connector);
            doReturn(synapseEntity).when(mockSchemaService).getEntity(synapseEntity.getId());
            doReturn(ent).when(mockSchemaService).getEntity(connector.getId(), ent.getApiName());
            controller.setConnectorService(mockConnService);
            controller.setSchemaService(mockSchemaService);
            try {
                controller.updateField(synapseEntity.getId(), ObjectId.get().toHexString(), attr1);
                fail();
            } catch (SyncariValidationException exception) {
                assertEquals(exception.getMessage(), "Invalid Reference Attribute for the selected Entity");
            }

            AttributeDef attr2 = new AttributeDef().setApiName(" ").setDisplayName("newTest").setDataType("string").setReferenceTo("oldEntity").setReferenceTargetField("someRandomFieldName");
            try {
                controller.updateField(synapseEntity.getId(), ObjectId.get().toHexString(), attr2);
                fail();
            } catch (SyncariValidationException exception) {
                assertEquals(exception.getMessage(), "Please provide valid Api Name");
            }

            AttributeDef attr3 = new AttributeDef().setApiName("testName").setDisplayName(" ").setDataType("string").setReferenceTo("oldEntity").setReferenceTargetField("someRandomFieldName");
            try {
                controller.updateField(synapseEntity.getId(), ObjectId.get().toHexString(), attr3);
                fail();
            } catch (SyncariValidationException exception) {
                assertEquals(exception.getMessage(), "Display Name cannot be empty");
            }

            AttributeDef attr4 = new AttributeDef().setApiName("testName").setDisplayName("testName").setReferenceTo("oldEntity").setReferenceTargetField("someRandomFieldName");
            try {
                controller.updateField(synapseEntity.getId(), ObjectId.get().toHexString(), attr4);
                fail();
            } catch (SyncariValidationException exception) {
                assertEquals(exception.getMessage(), "Please select valid datatype");
            }

        } finally {
            controller.setSchemaService(orgSchemaService);
            controller.setConnectorService(orgConnService);
        }
    }

    @Test
    public void getUsedInDependencyLinksForEntity() {
        controller.mappingGraphService = mockGraphService;
        MappingGraph entityGraph = new MappingGraph().setTargetId("syncariEntityId").setScope(Scope.ENTITY)
                .setName("Account");
        // Case 1: No Graphs
        when(mockGraphService.findEntityGraphsByConnectorEntityId(ArgumentMatchers.any())).thenReturn(Map.of());
        Map<String, List<Link>> linksByEntity = controller.getUsedInDependencyLinksForEntity(List.of("accountEntityId"));
        verify(mockGraphService).findEntityGraphsByConnectorEntityId(ArgumentMatchers.any());
		assertTrue(linksByEntity.isEmpty());

		// Case 2: Draft only Graph
		MappingGraph draft = entityGraph.makeCopy();
		draft.setDraftStatus(DraftStatus.NEW);
		when(mockGraphService.findEntityGraphsByConnectorEntityId(ArgumentMatchers.any()))
				.thenReturn(Map.of("accountEntityId", List.of(draft)));
		List<Link> links = controller.getUsedInDependencyLinksForEntity(List.of("accountEntityId")).get("accountEntityId");
		assertEquals(1, links.size());
		assertEquals(Route.RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION.name(), links.get(0).getRoute().get("route"));
		assertEquals("Account", links.get(0).getDisplayText());
		assertEquals("syncariEntityId", links.get(0).getRoute().get("entityId"));
		assertEquals(DraftStatus.NEW.name(), links.get(0).getRoute().get("graphVersion"));

		// Case 3: Published Graph
		MappingGraph approved = entityGraph.makeCopy();
		approved.setDraftStatus(DraftStatus.APPROVED);
		when(mockGraphService.findEntityGraphsByConnectorEntityId(ArgumentMatchers.any()))
				.thenReturn(Map.of("accountEntityId", List.of(approved)));
		links = controller.getUsedInDependencyLinksForEntity(List.of("accountEntityId")).get("accountEntityId");
		assertEquals(1, links.size());
		assertEquals(Route.RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION.name(), links.get(0).getRoute().get("route"));
		assertEquals("Account", links.get(0).getDisplayText());
		assertEquals("syncariEntityId", links.get(0).getRoute().get("entityId"));
		assertEquals(DraftStatus.APPROVED.name(), links.get(0).getRoute().get("graphVersion"));

		// Case 4: Published With Draft graph
		when(mockGraphService.findEntityGraphsByConnectorEntityId(ArgumentMatchers.any()))
				.thenReturn(Map.of("accountEntityId", List.of(approved, draft)));
		links = controller.getUsedInDependencyLinksForEntity(List.of("accountEntityId")).get("accountEntityId");
		assertEquals(1, links.size());
		assertEquals(Route.RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION.name(), links.get(0).getRoute().get("route"));
		assertEquals("Account", links.get(0).getDisplayText());
		assertEquals("syncariEntityId", links.get(0).getRoute().get("entityId"));
		assertEquals(DraftStatus.APPROVED.name(), links.get(0).getRoute().get("graphVersion"));

		// Case 5: One published and one draft graph
		MappingGraph entityGraph2 = new MappingGraph().setTargetId("syncariEntityId2").setScope(Scope.ENTITY)
				.setName("Contact");
		MappingGraph draft2 = entityGraph2.makeCopy();
		draft2.setDraftStatus(DraftStatus.NEW);
		when(mockGraphService.findEntityGraphsByConnectorEntityId(ArgumentMatchers.any()))
				.thenReturn(Map.of("accountEntityId", List.of(approved, draft2)));
		links = controller.getUsedInDependencyLinksForEntity(List.of("accountEntityId")).get("accountEntityId");
		assertEquals(2, links.size());
		assertEquals(Route.RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION.name(), links.get(0).getRoute().get("route"));
		assertEquals(Route.RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION.name(), links.get(1).getRoute().get("route"));
		assertEquals("Account", links.get(0).getDisplayText());
		assertEquals("Contact", links.get(1).getDisplayText());
		assertEquals("syncariEntityId", links.get(0).getRoute().get("entityId"));
		assertEquals("syncariEntityId2", links.get(1).getRoute().get("entityId"));
		assertEquals(DraftStatus.APPROVED.name(), links.get(0).getRoute().get("graphVersion"));
		assertEquals(DraftStatus.NEW.name(), links.get(1).getRoute().get("graphVersion"));
	}

	@Test
	@WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
	public void createEntityDraftWithRunDFIAndRunMerge() {
		var orgSchemaService = controller.getSchemaService();
		var orgConnService = controller.getConnectorService();
		try {
			Connector syncariConnector = connectService.getSyncariConnector();
			SchemaService mockSchemaService = mock(SchemaService.class);
			ConnectorService mockConnService = mock(ConnectorService.class);

			doReturn(syncariConnector).when(mockConnService).getSyncariConnector();

			EntityDefinition savedEntity = SchemaHelper.createEntityDef("TestEntity", "TestEntity", syncariConnector);
			savedEntity.setRunDFI(true);
			savedEntity.setRunMerge(true);

			ArgumentCaptor<EntityDefinition> entityCaptor = ArgumentCaptor.forClass(EntityDefinition.class);
			doReturn(savedEntity).when(mockSchemaService).createDraftEntity(entityCaptor.capture(), eq(false));

			controller.setConnectorService(mockConnService);
			controller.setSchemaService(mockSchemaService);

			EntityDef entityDef = new EntityDef();
			entityDef.setApiName("TestEntity");
			entityDef.setDisplayName("Test Entity");
			entityDef.setRunDFI(true);
			entityDef.setRunMerge(true);

			EntityDef result = controller.createEntityDraft(entityDef);

			EntityDefinition capturedEntity = entityCaptor.getValue();
			assertTrue("runDFI should be true", capturedEntity.isRunDFI());
			assertTrue("runMerge should be true", capturedEntity.isRunMerge());

			assertTrue("Response runDFI should be true", result.isRunDFI());
			assertTrue("Response runMerge should be true", result.isRunMerge());
		} finally {
			controller.setSchemaService(orgSchemaService);
			controller.setConnectorService(orgConnService);
		}
	}

	@Test
	@WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
	public void createEntityDraftWithDefaultRunDFIAndRunMerge() {
		var orgSchemaService = controller.getSchemaService();
		var orgConnService = controller.getConnectorService();
		try {
			Connector syncariConnector = connectService.getSyncariConnector();
			SchemaService mockSchemaService = mock(SchemaService.class);
			ConnectorService mockConnService = mock(ConnectorService.class);

			doReturn(syncariConnector).when(mockConnService).getSyncariConnector();

			EntityDefinition savedEntity = SchemaHelper.createEntityDef("TestEntity", "TestEntity", syncariConnector);

			ArgumentCaptor<EntityDefinition> entityCaptor = ArgumentCaptor.forClass(EntityDefinition.class);
			doReturn(savedEntity).when(mockSchemaService).createDraftEntity(entityCaptor.capture(), eq(false));

			controller.setConnectorService(mockConnService);
			controller.setSchemaService(mockSchemaService);

			EntityDef entityDef = new EntityDef();
			entityDef.setApiName("TestEntity");
			entityDef.setDisplayName("Test Entity");

			EntityDef result = controller.createEntityDraft(entityDef);

			EntityDefinition capturedEntity = entityCaptor.getValue();
			assertFalse("runDFI should default to false", capturedEntity.isRunDFI());
			assertFalse("runMerge should default to false", capturedEntity.isRunMerge());

			assertFalse("Response runDFI should be false", result.isRunDFI());
			assertFalse("Response runMerge should be false", result.isRunMerge());
		} finally {
			controller.setSchemaService(orgSchemaService);
			controller.setConnectorService(orgConnService);
		}
	}

	@Test
	@WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
	public void updateEntityDraftWithRunDFIAndRunMerge() {
		var orgSchemaService = controller.getSchemaService();
		var orgConnService = controller.getConnectorService();
		try {
			Connector syncariConnector = connectService.getSyncariConnector();
			SchemaService mockSchemaService = mock(SchemaService.class);
			ConnectorService mockConnService = mock(ConnectorService.class);

			doReturn(syncariConnector).when(mockConnService).getSyncariConnector();

			String entityId = ObjectId.get().toHexString();
			EntityDefinition savedEntity = SchemaHelper.createEntityDef("TestEntity", "TestEntity", syncariConnector);
			savedEntity.setId(entityId);
			savedEntity.setRunDFI(true);
			savedEntity.setRunMerge(true);

			ArgumentCaptor<EntityDefinition> entityCaptor = ArgumentCaptor.forClass(EntityDefinition.class);
			doReturn(savedEntity).when(mockSchemaService).updateDraftEntity(entityCaptor.capture());

			controller.setConnectorService(mockConnService);
			controller.setSchemaService(mockSchemaService);

			EntityDef entityDef = new EntityDef();
			entityDef.setId(entityId);
			entityDef.setApiName("TestEntity");
			entityDef.setDisplayName("Test Entity");
			entityDef.setRunDFI(true);
			entityDef.setRunMerge(true);

			EntityDef result = controller.updateEntityDraft(entityId, entityDef);

			EntityDefinition capturedEntity = entityCaptor.getValue();
			assertTrue("runDFI should be true", capturedEntity.isRunDFI());
			assertTrue("runMerge should be true", capturedEntity.isRunMerge());

			assertTrue("Response runDFI should be true", result.isRunDFI());
			assertTrue("Response runMerge should be true", result.isRunMerge());
		} finally {
			controller.setSchemaService(orgSchemaService);
			controller.setConnectorService(orgConnService);
		}
	}
}
