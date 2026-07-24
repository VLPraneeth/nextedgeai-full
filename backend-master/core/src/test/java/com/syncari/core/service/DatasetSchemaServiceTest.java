package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.NoQueryFunction;
import com.syncari.core.model.insights.Projection;
import com.syncari.core.model.insights.QField;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.repositories.customer.DatasetRepo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class DatasetSchemaServiceTest extends AbstractSyncariTest {

    @Autowired
    DatasetSchemaService datasetSchemaService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    DatasetRepo datasetRepo;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void testCreateDatasetSyncariSourceSchemaIncludesSourceParams() {
        Dataset dataset = createTestDataset("testDatasetSchema_" + System.currentTimeMillis(), "Test Dataset Schema");

        datasetSchemaService.createDatasetSyncariSourceSchema(dataset);

        assertNotNull(dataset.getEntityDefinitionId());
        EntityDefinition entityDefinition = schemaService.getEntity(dataset.getEntityDefinitionId());

        assertNotNull(entityDefinition);
        List<AttributeDefinition> sourceParams = entityDefinition.getSourceParams();
        assertNotNull(sourceParams);
        assertEquals(1, sourceParams.size());

        AttributeDefinition orderByParam = sourceParams.get(0);
        assertEquals("orderBy", orderByParam.getApiName());
        assertEquals("Order By", orderByParam.getDisplayName());
    }

    @Test
    public void testUpdateDatasetSyncariSourceSchemaUpdatesSourceParams() {
        Dataset dataset = createTestDataset("updateDatasetSchema_" + System.currentTimeMillis(), "Update Test Dataset Schema");

        datasetSchemaService.createDatasetSyncariSourceSchema(dataset);

        dataset.setDisplayName("Updated Test Dataset");
        datasetSchemaService.updateDatasetSyncariSourceSchema(dataset);

        EntityDefinition entityDefinition = schemaService.getEntity(dataset.getEntityDefinitionId());

        assertNotNull(entityDefinition);
        assertEquals("Updated Test Dataset", entityDefinition.getDisplayName());

        List<AttributeDefinition> sourceParams = entityDefinition.getSourceParams();
        assertNotNull(sourceParams);
        assertEquals(1, sourceParams.size());

        AttributeDefinition orderByParam = sourceParams.get(0);
        assertEquals("orderBy", orderByParam.getApiName());
        assertEquals("Order By", orderByParam.getDisplayName());
    }

    private Dataset createTestDataset(String name, String displayName) {
        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setDisplayName(displayName);
        dataset.setDraftStatus(DraftStatus.APPROVED);
        dataset.setCreatedAt(new Date());
        dataset.setUpdatedAt(new Date());

        DatasetConfig config = new DatasetConfig();
        Projection projection = new Projection();
        projection.setAliasName("testField");
        projection.setDataType("string");

        NoQueryFunction function = new NoQueryFunction();
        function.setDataType("string");
        function.setAlias("testField");

        QField qField = new QField();
        qField.setName("testField");
        qField.setDataType("string");
        function.setColumns(List.of(qField));

        projection.setFunction(function);
        config.setProjectionsList(List.of(projection));
        dataset.setDatasetConfig(config);

        Dataset savedDataset = datasetRepo.save(dataset);
        return savedDataset;
    }

    @Test
    public void testCreateDatasetUsesSyncariDefinedWatermark() {
        Dataset dataset = createTestDataset("newDataset_" + System.currentTimeMillis(), "New Dataset");

        datasetSchemaService.createDatasetSyncariSourceSchema(dataset);

        assertNotNull(dataset.getEntityDefinitionId());
        EntityDefinition entityDefinition = schemaService.getEntity(dataset.getEntityDefinitionId());

        assertNotNull(entityDefinition);
        List<AttributeDefinition> attributes = entityDefinition.getAttributes();

        AttributeDefinition watermarkField = attributes.stream()
            .filter(AttributeDefinition::isWatermarkField)
            .findFirst()
            .orElse(null);

        assertNotNull("Watermark field should be created", watermarkField);
        assertEquals("Should use syncariDefinedUpdatedAt as watermark name", "syncariDefinedUpdatedAt", watermarkField.getApiName());
        assertEquals("Should have correct display name", "Syncari Defined Updated At", watermarkField.getDisplayName());
        assertTrue("Should be marked as syncari defined", watermarkField.isSyncariDefined());
    }

    @Test
    public void testUpdateDatasetPreservesExistingWatermarkName() {
        Dataset dataset = createTestDataset("updateTest_" + System.currentTimeMillis(), "Update Test");

        datasetSchemaService.createDatasetSyncariSourceSchema(dataset);

        EntityDefinition entityDefinition = schemaService.getEntity(dataset.getEntityDefinitionId());
        AttributeDefinition originalWatermark = entityDefinition.getAttributes().stream()
            .filter(AttributeDefinition::isWatermarkField)
            .findFirst()
            .orElse(null);
        assertNotNull(originalWatermark);
        String originalWatermarkName = originalWatermark.getApiName();

        dataset.setDisplayName("Updated Dataset");
        Projection newProjection = new Projection();
        newProjection.setAliasName("newField");
        newProjection.setDataType("string");
        NoQueryFunction function = new NoQueryFunction();
        function.setDataType("string");
        function.setAlias("newField");
        QField qField = new QField();
        qField.setName("newField");
        qField.setDataType("string");
        function.setColumns(List.of(qField));
        newProjection.setFunction(function);

        List<Projection> projections = new ArrayList<>(dataset.getDatasetConfig().getProjectionsList());
        projections.add(newProjection);
        dataset.getDatasetConfig().setProjectionsList(projections);

        datasetSchemaService.updateDatasetSyncariSourceSchema(dataset);

        EntityDefinition updatedEntityDef = schemaService.getEntity(dataset.getEntityDefinitionId());
        AttributeDefinition updatedWatermark = updatedEntityDef.getAttributes().stream()
            .filter(AttributeDefinition::isWatermarkField)
            .findFirst()
            .orElse(null);

        assertNotNull("Watermark field should still exist", updatedWatermark);
        assertEquals("Watermark name should be preserved", originalWatermarkName, updatedWatermark.getApiName());
    }

    @Test
    public void testFetchDatasetSchemaForNewDataset() {
        Dataset dataset = createTestDataset("fetchNew_" + System.currentTimeMillis(), "Fetch New");
        dataset.setEntityDefinitionId(null);

        EntityDefinition schema = datasetSchemaService.fetchDatasetSchema(dataset);

        assertNotNull(schema);
        AttributeDefinition watermarkField = schema.getAttributes().stream()
            .filter(AttributeDefinition::isWatermarkField)
            .findFirst()
            .orElse(null);

        assertNotNull("Watermark field should be in schema", watermarkField);
        assertEquals("Should use syncariDefinedUpdatedAt for new dataset", "syncariDefinedUpdatedAt", watermarkField.getApiName());
    }

    @Test
    public void testFetchDatasetSchemaForExistingDataset() {
        Dataset dataset = createTestDataset("fetchExisting_" + System.currentTimeMillis(), "Fetch Existing");

        datasetSchemaService.createDatasetSyncariSourceSchema(dataset);

        String entityDefinitionId = dataset.getEntityDefinitionId();
        assertNotNull(entityDefinitionId);

        EntityDefinition originalSchema = schemaService.getEntity(entityDefinitionId);
        String originalWatermarkName = originalSchema.getAttributes().stream()
            .filter(AttributeDefinition::isWatermarkField)
            .findFirst()
            .map(AttributeDefinition::getApiName)
            .orElse(null);

        EntityDefinition fetchedSchema = datasetSchemaService.fetchDatasetSchema(dataset);

        assertNotNull(fetchedSchema);
        AttributeDefinition watermarkField = fetchedSchema.getAttributes().stream()
            .filter(AttributeDefinition::isWatermarkField)
            .findFirst()
            .orElse(null);

        assertNotNull("Watermark field should be in fetched schema", watermarkField);
        assertEquals("Should preserve existing watermark name", originalWatermarkName, watermarkField.getApiName());
    }

}