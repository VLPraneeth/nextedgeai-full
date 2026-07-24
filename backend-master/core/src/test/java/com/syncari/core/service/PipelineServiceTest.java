package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.connector.service.TestSynapseService;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.TextareaType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.utils.SchemaHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

@DirtiesContext
public class PipelineServiceTest extends AbstractSyncariTest {

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private MappingGraphService mappingGraphService;

    @Autowired
    private EntityDefinitionRepo entityDefinitionRepo;

    @Autowired
    private AttributeRepo attributeRepo;

    private Connector sourceConnector;
    private Connector destConnector;
    private EntityDefinition sourceEntity;

    @Before
    public void setUp() {
        super.setUp();
        // Reset dynamic entities in TestSynapseService
        TestSynapseService.resetDynamicEntities();

        // Create source connector using TestSynapse
        sourceConnector = new Connector("Source Test Synapse", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://source.test");
        sourceConnector = connectorService.save(sourceConnector);
        sourceConnector = connectorService.find(sourceConnector.getId()).get();

        // Create destination connector using TestSynapse
        destConnector = new Connector("Dest Test Synapse", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://dest.test");
        destConnector = connectorService.save(destConnector);
        destConnector = connectorService.find(destConnector.getId()).get();

        // Create source entity with fields (simulating account entity)
        sourceEntity = createSourceEntity();
    }

    @After
    public void tearDown() {
        TestSynapseService.resetDynamicEntities();
        super.tearDown();
    }

    private EntityDefinition createSourceEntity() {
        // Use SchemaHelper to build the entity with id and watermark fields
        EntityDefinition entity = SchemaHelper.createEntityDefinition("account", sourceConnector)
                .id()  // Add id field (required)
                .watermark("updatedAt", DatetimeType.VALUE)  // Add watermark field (required)
                .string("name")  // Account name
                .field("description", TextareaType.VALUE)  // Description
                .string("phone")  // Phone
                .getEntityDefinition();

        // Set connector type id
        entity.setConnectorTypeId(sourceConnector.getMetadataId());
        final List<AttributeDefinition> activeAttributes = entity.getActiveAttributes();
        // Save entity first to get the ID
        entity = entityDefinitionRepo.save(entity);

        // Save all fields with the entity ID

        for (AttributeDefinition field : activeAttributes) {
            field.setEntityId(entity.getId());
            field.setStatus(Status.ACTIVE);
            field.setDraftStatus(DraftStatus.APPROVED);
            attributeRepo.save(field);
        }

        // Reload entity to get fields from DB
        return schemaService.getEntity(entity.getId());
    }

    @Test
    public void testCreateExtractLoadPipeline_WithAutoMapFields() {
        // Given
        String syncariEntityName = "unified_account";
        String pipelineName = "Account Extract-Load Pipeline";

        // When
        PipelineService.ExtractLoadResult result = pipelineService.createExtractLoadPipeline(
                sourceEntity.getId(),
                null, // no existing destination entity
                destConnector.getId(),
                "dest_account", // create new destination entity
                true, // createDestinationEntity
                syncariEntityName,
                pipelineName,
                false, // don't publish
                false, // don't start resync
                false  // don't enable auto schema sync
        );

        // Then
        assertNotNull(result);
        assertEquals(sourceEntity.getId(), result.getSourceEntityId());
        assertEquals(sourceEntity.getDisplayName(), result.getSourceEntityName());
        assertNotNull(result.getSyncariEntityId());
        assertEquals(syncariEntityName, result.getSyncariEntityName());
        assertNotNull(result.getDestinationEntityId());
        assertNotNull(result.getPipelineId());
        assertEquals(pipelineName, result.getPipelineName());
        assertTrue(result.getFieldsMapped() > 0);
        assertFalse(result.isPublished());
        assertFalse(result.isResyncStarted());
        assertTrue(result.isDestEntityCreated());

        // Verify mapped fields
        assertNotNull(result.getMappedFields());
        assertFalse(result.getMappedFields().isEmpty());
        int expectedFieldCount = result.getFieldsMapped();

        // Verify entity pipeline was created using MappingGraphService
        Optional<MappingGraph> entityPipeline = mappingGraphService.retrieveEntityGraph(result.getSyncariEntityId());
        assertTrue("Entity pipeline should exist", entityPipeline.isPresent());
        assertEquals(DraftStatus.NEW, entityPipeline.get().getDraftStatus());
        assertEquals(pipelineName, entityPipeline.get().getName());

        // Validate the entity graph
        mappingGraphService.validateGraph(entityPipeline.get().getId());

        // Verify entity pipeline has source and sink nodes
        MappingGraph pipeline = entityPipeline.get();
        long sourceCount = pipeline.getSources().count();
        long sinkCount = pipeline.getSinks().count();
        assertTrue("Entity pipeline should have at least 1 source", sourceCount >= 1);
        assertTrue("Entity pipeline should have at least 1 sink", sinkCount >= 1);

        // Verify field/attribute pipelines were created
        List<MappingGraph> fieldPipelines = mappingGraphService.retrieveAttributeGraphsForEntityGraph(pipeline.getId());
        assertNotNull("Field pipelines should not be null", fieldPipelines);
        assertEquals("Number of field pipelines should match fields mapped", expectedFieldCount, fieldPipelines.size());

        // Validate each field pipeline and verify it has source and sink nodes
        for (MappingGraph fieldPipeline : fieldPipelines) {
            mappingGraphService.validateGraph(fieldPipeline.getId());
            long fieldSourceCount = fieldPipeline.getSources().count();
            long fieldSinkCount = fieldPipeline.getSinks().count();
            assertTrue("Field pipeline should have at least 1 source", fieldSourceCount >= 1);
            assertTrue("Field pipeline should have at least 1 sink", fieldSinkCount >= 1);
        }
    }

    @Test
    public void testCreateExtractLoadPipeline_WithExistingEntities() {
        // Given - Create syncari entity and destination entity first
        EntityDefinition syncariEntity = createSyncariEntity("manual_account");
        EntityDefinition destEntity = createDestinationEntity("manual_dest_account");

        // When - Use existing entities (auto-maps by apiName)
        PipelineService.ExtractLoadResult result = pipelineService.createExtractLoadPipeline(
                sourceEntity.getId(),
                destEntity.getId(),
                destConnector.getId(),
                null, // not creating new destination entity
                false, // don't create destination entity
                syncariEntity.getApiName(),
                "Manual Pipeline",
                false,
                false,
                false  // don't enable auto schema sync
        );

        // Then
        assertNotNull(result);
        assertTrue(result.getFieldsMapped() > 0);
        assertFalse(result.isDestEntityCreated());

        // Verify entity pipeline using MappingGraphService
        Optional<MappingGraph> entityPipeline = mappingGraphService.retrieveEntityGraph(result.getSyncariEntityId());
        assertTrue("Entity pipeline should exist", entityPipeline.isPresent());

        // Validate the entity graph
        mappingGraphService.validateGraph(entityPipeline.get().getId());

        // Verify field pipelines were created
        List<MappingGraph> fieldPipelines = mappingGraphService.retrieveAttributeGraphsForEntityGraph(entityPipeline.get().getId());
        assertEquals("Number of field pipelines should match fields mapped", result.getFieldsMapped(), fieldPipelines.size());

        // Validate each field pipeline
        for (MappingGraph fieldPipeline : fieldPipelines) {
            mappingGraphService.validateGraph(fieldPipeline.getId());
        }
    }

    @Test
    public void testCreateExtractLoadPipeline_WithPublish() {
        // Given
        String syncariEntityName = "publish_test_account";
        String pipelineName = "Publish Test Pipeline";

        // When
        PipelineService.ExtractLoadResult result = pipelineService.createExtractLoadPipeline(
                sourceEntity.getId(),
                null,
                destConnector.getId(),
                "dest_publish_account",
                true,
                syncariEntityName,
                pipelineName,
                true, // publish
                false,
                false  // don't enable auto schema sync
        );

        // Then
        assertNotNull(result);
        assertTrue(result.isPublished());

        // Verify pipeline is approved using MappingGraphService
        Optional<MappingGraph> entityPipeline = mappingGraphService.retrieveEntityGraph(result.getSyncariEntityId(), DraftStatus.APPROVED);
        assertTrue("Published entity pipeline should exist", entityPipeline.isPresent());
        assertEquals(DraftStatus.APPROVED, entityPipeline.get().getDraftStatus());

        // Note: validateGraph is designed for draft pipelines, not APPROVED ones
        // Verify field pipelines were created and approved
        List<MappingGraph> fieldPipelines = mappingGraphService.retrieveAttributeGraphsForEntityGraph(entityPipeline.get().getId());
        assertNotNull("Field pipelines should not be null", fieldPipelines);
        assertEquals("Number of field pipelines should match fields mapped", result.getFieldsMapped(), fieldPipelines.size());
    }

    @Test(expected = NotFoundException.class)
    public void testCreateExtractLoadPipeline_SourceEntityNotFound() {
        pipelineService.createExtractLoadPipeline(
                "non-existent-id",
                null,
                destConnector.getId(),
                "dest_entity",
                true,
                "syncari_entity",
                "Test Pipeline",
                false,
                false,
                false
        );
    }

    @Test(expected = SyncariValidationException.class)
    public void testCreateExtractLoadPipeline_MissingDestinationEntityName() {
        pipelineService.createExtractLoadPipeline(
                sourceEntity.getId(),
                null,
                destConnector.getId(),
                null, // missing name when createDestinationEntity=true
                true,
                "syncari_entity",
                "Test Pipeline",
                false,
                false,
                false
        );
    }

    @Test(expected = SyncariValidationException.class)
    public void testCreateExtractLoadPipeline_ResyncWithoutPublish() {
        pipelineService.createExtractLoadPipeline(
                sourceEntity.getId(),
                null,
                destConnector.getId(),
                "dest_entity",
                true,
                "syncari_entity",
                "Test Pipeline",
                false, // not published
                true,  // but trying to start resync
                false
        );
    }

    @Test
    public void testCreateExtractLoadPipeline_ReuseExistingSyncariEntity() {
        // Given - Create a syncari entity first
        EntityDefinition existingSyncariEntity = createSyncariEntity("existing_syncari");

        // When - Create pipeline with same syncari entity name
        PipelineService.ExtractLoadResult result = pipelineService.createExtractLoadPipeline(
                sourceEntity.getId(),
                null,
                destConnector.getId(),
                "dest_reuse",
                true,
                existingSyncariEntity.getApiName(), // reuse existing
                "Reuse Syncari Pipeline",
                false,
                false,
                false  // don't enable auto schema sync
        );

        // Then - Should reuse the existing syncari entity
        assertNotNull(result);
        assertEquals(existingSyncariEntity.getId(), result.getSyncariEntityId());
    }

    private EntityDefinition createSyncariEntity(String apiName) {
        Connector syncariConnector = connectorService.getSyncariConnector();

        // Use SchemaHelper to create entity with same fields as source (including id and watermark)
        EntityDefinition entity = SchemaHelper.createEntityDefinition(apiName, syncariConnector)
                .id()  // Add id field
                .watermark("updatedAt", DatetimeType.VALUE)  // Add watermark field
                .string("name")
                .field("description", TextareaType.VALUE)
                .string("phone")
                .getEntityDefinition();

        entity.setConnectorTypeId(syncariConnector.getMetadataId());
        entity = entityDefinitionRepo.save(entity);

        // Save all fields
        for (AttributeDefinition field : entity.getActiveAttributes()) {
            field.setEntityId(entity.getId());
            field.setStatus(Status.ACTIVE);
            field.setDraftStatus(DraftStatus.APPROVED);
            attributeRepo.save(field);
        }

        return schemaService.getEntity(entity.getId());
    }

    private EntityDefinition createDestinationEntity(String apiName) {
        // Use SchemaHelper to create entity with same fields as source (including id and watermark)
        EntityDefinition entity = SchemaHelper.createEntityDefinition(apiName, destConnector)
                .id()  // Add id field
                .watermark("updatedAt", DatetimeType.VALUE)  // Add watermark field
                .string("name")
                .field("description", TextareaType.VALUE)
                .string("phone")
                .getEntityDefinition();

        entity.setConnectorTypeId(destConnector.getMetadataId());
        entity = entityDefinitionRepo.save(entity);

        // Save all fields
        for (AttributeDefinition field : entity.getActiveAttributes()) {
            field.setEntityId(entity.getId());
            field.setStatus(Status.ACTIVE);
            field.setDraftStatus(DraftStatus.APPROVED);
            attributeRepo.save(field);
        }

        return schemaService.getEntity(entity.getId());
    }

    @Test
    public void testCreateExtractLoadPipeline_CreatesNewFieldPipelinesForNewSourceFields() {
        // Given - Create initial pipeline
        String syncariEntityName = "field_pipeline_test";
        String pipelineName = "Field Pipeline Test";

        PipelineService.ExtractLoadResult initialResult = pipelineService.createExtractLoadPipeline(
                sourceEntity.getId(),
                null,
                destConnector.getId(),
                "dest_field_test",
                true,
                syncariEntityName,
                pipelineName,
                false,
                false,
                false
        );

        assertNotNull(initialResult);
        int initialFieldCount = initialResult.getFieldsMapped();
        assertTrue("Should have mapped some fields initially", initialFieldCount > 0);

        // Get initial field pipelines count
        Optional<MappingGraph> entityPipeline = mappingGraphService.retrieveEntityGraph(initialResult.getSyncariEntityId());
        assertTrue("Entity pipeline should exist", entityPipeline.isPresent());
        List<MappingGraph> initialFieldPipelines = mappingGraphService.retrieveAttributeGraphsForEntityGraph(entityPipeline.get().getId());
        int initialPipelineCount = initialFieldPipelines.size();

        // When - Add a new field to the source entity
        AttributeDefinition newField = new AttributeDefinition();
        newField.setApiName("newField");
        newField.setDisplayName("New Field");
        newField.setDataType(new TextareaType());
        newField.setStatus(Status.ACTIVE);
        newField.setDraftStatus(DraftStatus.APPROVED);
        newField.setEntityId(sourceEntity.getId());
        attributeRepo.save(newField);

        // Reload source entity to pick up the new field
        sourceEntity = schemaService.getEntity(sourceEntity.getId());

        // Run extract-load again - this should create field pipeline for the new field
        PipelineService.ExtractLoadResult secondResult = pipelineService.createExtractLoadPipeline(
                sourceEntity.getId(),
                initialResult.getDestinationEntityId(),
                destConnector.getId(),
                null,
                false,
                syncariEntityName,
                pipelineName,
                false,
                false,
                false
        );

        // Then - Verify new field pipeline was created
        assertNotNull(secondResult);

        // Reload entity pipeline (may have changed)
        entityPipeline = mappingGraphService.retrieveEntityGraph(secondResult.getSyncariEntityId());
        assertTrue("Entity pipeline should still exist", entityPipeline.isPresent());

        List<MappingGraph> updatedFieldPipelines = mappingGraphService.retrieveAttributeGraphsForEntityGraph(entityPipeline.get().getId());
        assertTrue("Should have more field pipelines after adding new field",
                updatedFieldPipelines.size() > initialPipelineCount);

        // Verify the new field pipeline has proper source and sink nodes
        boolean foundNewFieldPipeline = false;
        for (MappingGraph fieldPipeline : updatedFieldPipelines) {
            // Check if this is the pipeline for the new field by looking at the target
            if (fieldPipeline.getTargetId() != null) {
                AttributeDefinition targetField = attributeRepo.findById(fieldPipeline.getTargetId()).orElse(null);
                if (targetField != null && "newField".equals(targetField.getApiName())) {
                    foundNewFieldPipeline = true;
                    // Verify it has source and sink nodes
                    long sourceCount = fieldPipeline.getSources().count();
                    long sinkCount = fieldPipeline.getSinks().count();
                    assertTrue("New field pipeline should have at least 1 source", sourceCount >= 1);
                    assertTrue("New field pipeline should have at least 1 sink", sinkCount >= 1);
                    // Validate the pipeline
                    mappingGraphService.validateGraph(fieldPipeline.getId());
                    break;
                }
            }
        }
        assertTrue("Should have found and validated the new field pipeline", foundNewFieldPipeline);
    }

    @Test
    public void testCreateExtractLoadPipeline_UpdatesExistingDestFieldWithIdFlag() {
        // Given - Create a destination entity with an "id" field that does NOT have idField=true
        EntityDefinition destEntityWithoutIdFlag = createDestinationEntityWithoutIdFlag("dest_no_id_flag");

        // Verify the destination entity's id field does NOT have idField flag set
        AttributeDefinition destIdFieldBefore = destEntityWithoutIdFlag.getActiveAttributes().stream()
                .filter(f -> "id".equals(f.getApiName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Destination entity should have 'id' field"));
        assertFalse("Destination 'id' field should NOT have idField flag initially", destIdFieldBefore.isIdField());

        // Verify source entity's id field DOES have the idField flag
        AttributeDefinition sourceIdField = sourceEntity.getActiveAttributes().stream()
                .filter(AttributeDefinition::isIdField)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Source entity should have an id field marked"));
        assertEquals("id", sourceIdField.getApiName());

        // When - Run extract-load with the existing destination entity
        PipelineService.ExtractLoadResult result = pipelineService.createExtractLoadPipeline(
                sourceEntity.getId(),
                destEntityWithoutIdFlag.getId(),
                destConnector.getId(),
                null,
                false, // not creating new destination entity
                "syncari_id_flag_test",
                "Id Flag Test Pipeline",
                false,
                false,
                false
        );

        // Then - Verify pipeline was created successfully
        assertNotNull(result);
        assertEquals(destEntityWithoutIdFlag.getId(), result.getDestinationEntityId());

        // Reload the destination entity to verify the id field was updated
        EntityDefinition updatedDestEntity = schemaService.getEntity(destEntityWithoutIdFlag.getId());
        AttributeDefinition destIdFieldAfter = updatedDestEntity.getActiveAttributes().stream()
                .filter(f -> "id".equals(f.getApiName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Destination entity should still have 'id' field"));

        // The fix should have updated the existing field to set idField=true
        assertTrue("Destination 'id' field should now have idField flag set", destIdFieldAfter.isIdField());

        // Verify the pipeline validates successfully (this would fail before the fix)
        Optional<MappingGraph> entityPipeline = mappingGraphService.retrieveEntityGraph(result.getSyncariEntityId());
        assertTrue("Entity pipeline should exist", entityPipeline.isPresent());
        mappingGraphService.validateGraph(entityPipeline.get().getId());
    }

    /**
     * Creates a destination entity with fields matching the source, but WITHOUT the idField flag set.
     * This simulates a pre-existing destination entity that has an "id" field but it's not marked as the id field.
     */
    private EntityDefinition createDestinationEntityWithoutIdFlag(String apiName) {
        EntityDefinition entity = new EntityDefinition();
        entity.setApiName(apiName);
        entity.setDisplayName(apiName);
        entity.setStatus(Status.ACTIVE);
        entity.setDraftStatus(DraftStatus.APPROVED);
        entity.setConnectorId(destConnector.getId());
        entity.setConnectorTypeId(destConnector.getMetadataId());
        entity = entityDefinitionRepo.save(entity);

        // Create fields matching source but WITHOUT idField/watermarkField flags
        String entityId = entity.getId();

        // Create "id" field WITHOUT idField=true (this is the bug scenario)
        AttributeDefinition idField = new AttributeDefinition();
        idField.setApiName("id");
        idField.setDisplayName("Id");
        idField.setDataType(new TextareaType());
        idField.setStatus(Status.ACTIVE);
        idField.setDraftStatus(DraftStatus.APPROVED);
        idField.setEntityId(entityId);
        idField.setIdField(false); // Explicitly NOT setting as id field
        attributeRepo.save(idField);

        // Create "updatedAt" field WITHOUT watermarkField=true
        AttributeDefinition watermarkField = new AttributeDefinition();
        watermarkField.setApiName("updatedAt");
        watermarkField.setDisplayName("Updated At");
        watermarkField.setDataType(new DatetimeType());
        watermarkField.setStatus(Status.ACTIVE);
        watermarkField.setDraftStatus(DraftStatus.APPROVED);
        watermarkField.setEntityId(entityId);
        watermarkField.setWatermarkField(false); // Explicitly NOT setting as watermark field
        attributeRepo.save(watermarkField);

        // Create other fields
        AttributeDefinition nameField = new AttributeDefinition();
        nameField.setApiName("name");
        nameField.setDisplayName("Name");
        nameField.setDataType(new TextareaType());
        nameField.setStatus(Status.ACTIVE);
        nameField.setDraftStatus(DraftStatus.APPROVED);
        nameField.setEntityId(entityId);
        attributeRepo.save(nameField);

        return schemaService.getEntity(entity.getId());
    }
}
