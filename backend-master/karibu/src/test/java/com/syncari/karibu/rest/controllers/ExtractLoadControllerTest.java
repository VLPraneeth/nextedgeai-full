package com.syncari.karibu.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.Constants;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.karibu.rest.request.ExtractLoadRequest;
import com.syncari.karibu.rest.util.OauthUtil;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ExtractLoadControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OauthUtil oauthUtil;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private MappingGraphService mappingGraphService;

    @Autowired
    private ConnectorMetadataService metadataService;

    @Autowired
    private EntityDefinitionRepo entityDefinitionRepo;

    @Autowired
    private AttributeRepo attributeRepo;

    private static Connector sourceConnector;
    private static Connector destConnector;
    private static Connector syncariConnector;
    private static EntityDefinition sourceEntity;
    private static EntityDefinition destEntity;
    private static EntityDefinition syncariEntity;

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void test01_Setup() throws Exception {
        String testSynapseMetadataId = connectorService.describe(Constants.TEST_SYNAPSE).getId();

        // Create source connector
        sourceConnector = new Connector("Source Test Synapse", testSynapseMetadataId, "http://source-test");
        sourceConnector = connectorService.save(sourceConnector);
        assertNotNull("Source connector should be created", sourceConnector.getId());

        // Create destination connector
        destConnector = new Connector("Destination Test Synapse", testSynapseMetadataId, "http://dest-test");
        destConnector = connectorService.save(destConnector);
        assertNotNull("Destination connector should be created", destConnector.getId());

        // Get syncari connector
        syncariConnector = connectorService.getSyncariConnector();
        assertNotNull("Syncari connector should exist", syncariConnector);

        // Create source entity with fields
        sourceEntity = SchemaHelper.createEntityDefinition("source_test_entity", sourceConnector)
                .id()
                .string("field_1")
                .string("field_2")
                .watermark().getEntityDefinition();
        final List<AttributeDefinition> attributes = sourceEntity.getAttributes();
        // Save entity first to get ID
        sourceEntity = entityDefinitionRepo.save(sourceEntity);
        attributes.forEach(attribute ->
                attribute.setEntityId(sourceEntity.getId())
        );
        // Save attributes with entity ID using attributeRepo
        attributeRepo.saveAll(attributes);

        // Reload entity to verify attributes
        sourceEntity = schemaService.getEntity(sourceEntity.getId());
        assertNotNull("Source entity should be created", sourceEntity.getId());
        assertEquals("Source entity should have 4 fields", 4, sourceEntity.getActiveAttributes().size());
        // Note: destination entity will be created by extract-load API in test02
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void test02_ExtractLoad_CreatePipelineAndFields() throws Exception {
        // Arrange - omit destinationEntityId to trigger destination entity creation
        ExtractLoadRequest request = new ExtractLoadRequest();
        request.setSourceEntityId(sourceEntity.getId());
        request.setDestinationConnectorId(destConnector.getId());
        // destinationEntityId not set - will create new destination entity
        request.setDestinationEntityName("dest_test_entity");
        request.setSyncariEntityName("test_extract_load_entity");
        request.setPipelineName("Test Extract Load Pipeline");
        request.setPublish(false);
        request.setStartResync(false);

        String requestJson = objectMapper.writeValueAsString(request);

        // Save context before mockMvc call
        pushContext();

        // Act
        MvcResult result = mockMvc.perform(post("/api/v1/extract-load")
                        .header("Authorization", "Bearer " + oauthUtil.getTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.pipelineId").isNotEmpty())
                .andExpect(jsonPath("$.syncariEntityId").isNotEmpty())
                .andExpect(jsonPath("$.syncariEntityName").value("test_extract_load_entity"))
                .andExpect(jsonPath("$.destinationEntityId").isNotEmpty())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.resyncStarted").value(false))
                .andExpect(jsonPath("$.fieldsMapped").value(4))
                .andReturn();

        // Restore context after mockMvc call
        restoreContext();

        String response = result.getResponse().getContentAsString();
        String syncariEntityId = objectMapper.readTree(response).get("syncariEntityId").asText();
        String destEntityId = objectMapper.readTree(response).get("destinationEntityId").asText();

        // Update static destEntity for use in subsequent tests
        destEntity = schemaService.getEntity(destEntityId);

        // Assert: Verify syncari entity was created
        syncariEntity = schemaService.getEntity(syncariEntityId);
        assertNotNull("Syncari entity should exist", syncariEntity);
        assertEquals("Syncari entity API name should match", "test_extract_load_entity", syncariEntity.getApiName());
        assertEquals("Syncari entity should have fields", 4, syncariEntity.getActiveAttributes().size());

        // Assert: Verify entity pipeline was created
        Optional<MappingGraph> entityPipeline = mappingGraphService.retrieveDraftEntityGraph(syncariEntityId);
        assertTrue("Entity pipeline should exist", entityPipeline.isPresent());
        assertEquals("Entity pipeline should be DRAFT", DraftStatus.NEW, entityPipeline.get().getDraftStatus());
        assertEquals("Entity pipeline should have correct name", "Test Extract Load Pipeline", entityPipeline.get().getName());

        // Assert: Verify entity pipeline has source and destination entity nodes
        MappingGraph ep = entityPipeline.get();
        assertTrue("Entity pipeline should have source entity", ep.hasSource(sourceEntity.getId()));
        assertTrue("Entity pipeline should have destination entity", ep.hasSink(destEntity.getId()));

        // Assert: Verify field pipelines were created
        for (AttributeDefinition syncariField : syncariEntity.getActiveAttributes()) {
            Optional<MappingGraph> fieldPipeline = mappingGraphService.retrieveDraftAttributeGraph(syncariField.getId());
            assertTrue("Field pipeline should exist for " + syncariField.getDisplayName(), fieldPipeline.isPresent());
            assertEquals("Field pipeline should be DRAFT", DraftStatus.NEW, fieldPipeline.get().getDraftStatus());
            assertEquals("Field pipeline scope should be ATTRIBUTE", Scope.ATTRIBUTE, fieldPipeline.get().getScope());

            // Verify field pipeline has source field node
            long sourceCount = fieldPipeline.get().getSources().count();
            assertEquals("Field pipeline should have 1 source", 1, sourceCount);
        }

        // Assert: Verify destination fields were created (via connector, so use destEntity from response)
        assertNotNull("Destination entity should exist", destEntity);
        assertEquals("Destination entity should have 2 fields", 4, destEntity.getActiveAttributes().size());

        // Verify destination fields have correct names
        assertTrue("Destination should have field_1",
                destEntity.getActiveAttributes().stream().anyMatch(f -> "field_1".equals(f.getApiName())));
        assertTrue("Destination should have field_2",
                destEntity.getActiveAttributes().stream().anyMatch(f -> "field_2".equals(f.getApiName())));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void test03_ExtractLoad_UpdateExistingPipeline_AddsDestinationNodes() throws Exception {
        // This test verifies that calling extract-load again on the same pipeline
        // adds destination nodes to existing field pipelines

        // Arrange
        ExtractLoadRequest request = new ExtractLoadRequest();
        request.setSourceEntityId(sourceEntity.getId());
        request.setDestinationConnectorId(destConnector.getId());
        request.setDestinationEntityId(destEntity.getId());
        request.setSyncariEntityName("test_extract_load_entity"); // Same syncari entity
        request.setPipelineName("Test Extract Load Pipeline"); // Same pipeline name
        request.setPublish(false);

        String requestJson = objectMapper.writeValueAsString(request);

        // Save context before mockMvc call
        pushContext();

        // Act
        mockMvc.perform(post("/api/v1/extract-load")
                        .header("Authorization", "Bearer " + oauthUtil.getTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        // Restore context after mockMvc call
        restoreContext();

        // Assert: Verify field pipelines now have destination nodes
        for (AttributeDefinition syncariField : syncariEntity.getActiveAttributes()) {
            Optional<MappingGraph> fieldPipeline = mappingGraphService.retrieveDraftAttributeGraph(syncariField.getId());
            assertTrue("Field pipeline should still exist", fieldPipeline.isPresent());

            // Verify it has both source and destination
            long sourceCount = fieldPipeline.get().getSources().count();
            long sinkCount = fieldPipeline.get().getSinks().count();

            assertEquals("Field pipeline should have 1 source", 1, sourceCount);
            assertEquals("Field pipeline should have 1 destination", 1, sinkCount);
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void test04_ExtractLoad_PublishPipeline() throws Exception {
        // Arrange
        ExtractLoadRequest request = new ExtractLoadRequest();
        request.setSourceEntityId(sourceEntity.getId());
        request.setDestinationConnectorId(destConnector.getId());
        request.setDestinationEntityId(destEntity.getId());
        request.setSyncariEntityName("test_extract_load_entity");
        request.setPipelineName("Test Extract Load Pipeline");
        request.setPublish(true); // Publish this time
        request.setStartResync(false);

        String requestJson = objectMapper.writeValueAsString(request);

        // Save context before mockMvc call
        pushContext();

        // Act
        mockMvc.perform(post("/api/v1/extract-load")
                        .header("Authorization", "Bearer " + oauthUtil.getTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.published").value(true))
                .andReturn();

        // Restore context after mockMvc call
        restoreContext();

        // Assert: Verify pipeline is now published (APPROVED)
        Optional<MappingGraph> approvedPipeline = mappingGraphService.retrieveApprovedEntityGraph(syncariEntity.getId());
        assertTrue("Entity pipeline should be approved after publish request", approvedPipeline.isPresent());
        assertEquals("Entity pipeline should be APPROVED", DraftStatus.APPROVED, approvedPipeline.get().getDraftStatus());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void test05_ExtractLoad_DefaultsToSourceEntityName() throws Exception {
        // Arrange - omit syncariEntityName, destinationEntityName, and pipelineName to test defaults
        ExtractLoadRequest request = new ExtractLoadRequest();
        request.setSourceEntityId(sourceEntity.getId());
        request.setDestinationConnectorId(destConnector.getId());
        // destinationEntityId not set - will create new destination entity
        // syncariEntityName not set - should default to source entity name
        // destinationEntityName not set - should default to source entity name
        // pipelineName not set - should default to source entity name

        String requestJson = objectMapper.writeValueAsString(request);

        // Save context before mockMvc call
        pushContext();

        // Act & Assert - should succeed with defaults
        mockMvc.perform(post("/api/v1/extract-load")
                        .header("Authorization", "Bearer " + oauthUtil.getTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.syncariEntityName").value(sourceEntity.getApiName()))
                .andExpect(jsonPath("$.destinationEntityName").value(sourceEntity.getApiName()))
                .andExpect(jsonPath("$.pipelineName").value(sourceEntity.getApiName()));

        // Restore context after mockMvc call
        restoreContext();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void test06_ExtractLoad_CreateDestinationEntity() throws Exception {
        // Arrange - omit destinationEntityId to trigger destination entity creation
        ExtractLoadRequest request = new ExtractLoadRequest();
        request.setSourceEntityId(sourceEntity.getId());
        request.setDestinationConnectorId(destConnector.getId());
        // destinationEntityId not set - will create new destination entity
        request.setDestinationEntityName("New Destination Entity");
        request.setSyncariEntityName("test_new_dest_entity");
        request.setPipelineName("Test New Dest Pipeline");
        request.setPublish(false);

        String requestJson = objectMapper.writeValueAsString(request);

        // Save context before mockMvc call
        pushContext();

        // Act
        MvcResult result = mockMvc.perform(post("/api/v1/extract-load")
                        .header("Authorization", "Bearer " + oauthUtil.getTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(containsString("new destination entity")))
                .andExpect(jsonPath("$.destinationEntityId").isNotEmpty())
                .andReturn();

        // Restore context after mockMvc call
        restoreContext();

        String response = result.getResponse().getContentAsString();
        String newDestEntityId = objectMapper.readTree(response).get("destinationEntityId").asText();

        // Assert: Verify destination entity was created
        EntityDefinition newDestEntity = schemaService.getEntity(newDestEntityId);
        assertNotNull("New destination entity should exist", newDestEntity);
        assertEquals("Destination entity should be on destination connector", destConnector.getId(), newDestEntity.getConnectorId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void test07_ExtractLoad_ReuseExistingSyncariEntity() throws Exception {
        // Test that using an existing syncari entity name reuses the entity

        // First, verify the syncari entity from test02 exists
        Optional<EntityDefinition> existingSyncariEntity = schemaService.getSyncariEntityByName("test_extract_load_entity");
        assertTrue("Syncari entity from previous test should exist", existingSyncariEntity.isPresent());
        String existingId = existingSyncariEntity.get().getId();

        // Create new source entity
        EntityDefinition newSourceEntity = SchemaHelper.createEntityDefinition("another_source_entity", sourceConnector)
                .id()
                .watermark()
                .string("field_1")
                .getEntityDefinition();
        final List<AttributeDefinition> attributes = newSourceEntity.getAttributes();
        // Save entity first to get ID
        newSourceEntity = entityDefinitionRepo.save(newSourceEntity);
        // Save attributes with entity ID using attributeRepo
        attributeRepo.saveAll(attributes);

        // Reload entity to include attributes
        newSourceEntity = schemaService.getEntity(newSourceEntity.getId());

        // Create new destination entity
        EntityDefinition newDestEntity2 = new EntityDefinition();
        newDestEntity2.setApiName("another_dest_entity");
        newDestEntity2.setDisplayName("Another Dest Entity");
        newDestEntity2.setConnectorId(destConnector.getId());
        newDestEntity2.setStatus(Status.ACTIVE);
        newDestEntity2.setDraftStatus(DraftStatus.APPROVED);
        newDestEntity2 = entityDefinitionRepo.save(newDestEntity2);

        // Arrange
        ExtractLoadRequest request = new ExtractLoadRequest();
        request.setSourceEntityId(newSourceEntity.getId());
        request.setDestinationConnectorId(destConnector.getId());
        request.setDestinationEntityId(newDestEntity2.getId());
        request.setSyncariEntityName("test_extract_load_entity"); // Reuse existing syncari entity
        request.setPipelineName("Another Pipeline");
        request.setPublish(false);

        String requestJson = objectMapper.writeValueAsString(request);

        // Save context before mockMvc call
        pushContext();

        // Act
        MvcResult result = mockMvc.perform(post("/api/v1/extract-load")
                        .header("Authorization", "Bearer " + oauthUtil.getTestAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.syncariEntityId").value(existingId))
                .andReturn();

        // Restore context after mockMvc call
        restoreContext();

        // Assert: Verify the same syncari entity was reused
        String syncariEntityId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("syncariEntityId").asText();
        assertEquals("Should reuse existing syncari entity", existingId, syncariEntityId);
    }
}
