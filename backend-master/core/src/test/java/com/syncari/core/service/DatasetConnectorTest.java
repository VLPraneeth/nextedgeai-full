package com.syncari.core.service;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.DescribeAllRequest;
import com.syncari.connector.data.EntityParams;
import com.syncari.connector.data.EntitySchema;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.DataTransformer;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.DatasetConfig;
import com.syncari.core.model.insights.Projection;
import com.syncari.core.model.insights.NoQueryFunction;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DatasetConnectorTest extends AbstractSyncariTest {
    static { System.setProperty("os.arch", "i686_64"); }

    @Autowired
    DatasetConnector service;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    DataTransformer transformer;

    @Autowired
    DatasetConnector datasetConnector;

    @Autowired
    DatasetService datasetService;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void testSourceParamValidation() {

        //order by exists validation
        EntityParams entityParams = new EntityParams();
        try{
            datasetConnector.validateEntityConfig(entityParams);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("OrderBy Field is not available in dataset source config", e.getMessage());
        }
        entityParams.setSourceParams(Map.of());
        try{
            datasetConnector.validateEntityConfig(entityParams);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("OrderBy Field is not available in dataset source config", e.getMessage());
        }
        entityParams.setSourceParams(Map.of("orderBy", ""));
        try{
            datasetConnector.validateEntityConfig(entityParams);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("OrderBy field must be configured in the source node configuration for dataset to be used as a source", e.getMessage());
        }
        EntitySchema schema = new EntitySchema();
        schema.setApiName("testEntity");
        Map<String, Object> additionalProps = new HashMap<>();
        additionalProps.put("datasetId", "test-id");
        schema.setAdditionalProperties(additionalProps);
        entityParams.setSchema(schema);

        Dataset mockDataset = new Dataset();
        DatasetConfig config = new DatasetConfig();
        List<Projection> projections = new ArrayList<>();

        Projection idProjection = new Projection();
        idProjection.setAliasName("id");
        projections.add(idProjection);

        Projection id1Projection = new Projection();
        id1Projection.setAliasName("id1");
        projections.add(id1Projection);

        config.setProjectionsList(projections);
        mockDataset.setDatasetConfig(config);

        DatasetConnector testConnector = new DatasetConnector();
        DatasetService mockedDatasetService = mock(DatasetService.class);
        when(mockedDatasetService.getDataset("test-id")).thenReturn(mockDataset);
        testConnector.datasetService = mockedDatasetService;

        entityParams.setSourceParams(Map.of("orderBy", "id"));
        assertTrue(testConnector.validateEntityConfig(entityParams));
        entityParams.setSourceParams(Map.of("orderBy", "id, id1"));
        assertTrue(testConnector.validateEntityConfig(entityParams));
        entityParams.setSourceParams(Map.of("orderBy", "id ASC, id1 DESC"));
        assertTrue(testConnector.validateEntityConfig(entityParams));

    }

    @Test
    public void testSourceParamValidationWithDataset() {
        String datasetId = "test-dataset-id";
        Dataset mockDataset = new Dataset();
        DatasetConfig config = new DatasetConfig();
        List<Projection> projections = new ArrayList<>();

        Projection idProjection = new Projection();
        idProjection.setAliasName("id");
        NoQueryFunction idFunc = new NoQueryFunction();
        idFunc.setDataType("string");
        idProjection.setFunction(idFunc);
        projections.add(idProjection);

        Projection nameProjection = new Projection();
        nameProjection.setAliasName("name");
        NoQueryFunction nameFunc = new NoQueryFunction();
        nameFunc.setDataType("string");
        nameProjection.setFunction(nameFunc);
        projections.add(nameProjection);

        Projection lastModifiedProjection = new Projection();
        lastModifiedProjection.setAliasName("T2:Last Modified Time");
        NoQueryFunction lastModifiedFunc = new NoQueryFunction();
        lastModifiedFunc.setDataType("datetime");
        lastModifiedProjection.setFunction(lastModifiedFunc);
        projections.add(lastModifiedProjection);

        config.setProjectionsList(projections);
        mockDataset.setDatasetConfig(config);

        DatasetConnector testConnector = new DatasetConnector();
        DatasetService mockedDatasetService = mock(DatasetService.class);
        when(mockedDatasetService.getDataset(datasetId)).thenReturn(mockDataset);
        testConnector.datasetService = mockedDatasetService;

        EntityParams entityParams = new EntityParams();
        EntitySchema schema = new EntitySchema();
        schema.setApiName("testEntity");
        Map<String, Object> additionalProps = new HashMap<>();
        additionalProps.put("datasetId", datasetId);
        schema.setAdditionalProperties(additionalProps);
        entityParams.setSchema(schema);

        entityParams.setSourceParams(Map.of("orderBy", "id"));
        assertTrue(testConnector.validateEntityConfig(entityParams));

        entityParams.setSourceParams(Map.of("orderBy", "id ASC"));
        assertTrue(testConnector.validateEntityConfig(entityParams));

        entityParams.setSourceParams(Map.of("orderBy", "name DESC, id ASC"));
        assertTrue(testConnector.validateEntityConfig(entityParams));

        entityParams.setSourceParams(Map.of("orderBy", "T2:Last Modified Time"));
        assertTrue(testConnector.validateEntityConfig(entityParams));

        entityParams.setSourceParams(Map.of("orderBy", "T2:Last Modified Time DESC"));
        assertTrue(testConnector.validateEntityConfig(entityParams));

        try {
            entityParams.setSourceParams(Map.of("orderBy", "invalidField"));
            testConnector.validateEntityConfig(entityParams);
            fail();
        } catch (SyncariValidationException e) {
            assertTrue(e.getMessage().contains("Invalid orderBy field 'invalidField'"));
            assertTrue(e.getMessage().contains("Valid fields are:"));
            assertTrue(e.getMessage().contains("id"));
            assertTrue(e.getMessage().contains("name"));
            assertTrue(e.getMessage().contains("T2:Last Modified Time"));
        }

        try {
            entityParams.setSourceParams(Map.of("orderBy", "name, invalidField DESC"));
            testConnector.validateEntityConfig(entityParams);
            fail();
        } catch (SyncariValidationException e) {
            assertTrue(e.getMessage().contains("Invalid orderBy field 'invalidField'"));
            assertTrue(e.getMessage().contains("Valid fields are:"));
            assertTrue(e.getMessage().contains("id"));
            assertTrue(e.getMessage().contains("name"));
            assertTrue(e.getMessage().contains("T2:Last Modified Time"));
        }

    }


    @Test
    public void testdescribeAll() {
        DescribeAllRequest request = new DescribeAllRequest(transformer.toConnectorInfo(connectorService.getSyncariConnector()), List.of());
        List<EntitySchema> schemaList = service.describeAll(request);
        assertTrue(CollectionUtils.isNotEmpty(schemaList));
        assertTrue(schemaList.size() >= 24);
        List<EntitySchema> entitySchemaList = schemaList.stream().filter(s -> s.getApiName().equalsIgnoreCase("allOpenNewPipelineCountDS")).collect(Collectors.toList());
        assertTrue(entitySchemaList.stream().findFirst().isPresent());
        assertNotNull(entitySchemaList.stream().findFirst().get());
        assertTrue(CollectionUtils.isNotEmpty(entitySchemaList.stream().findFirst().get().getAttributes()));
        List<AttributeSchema> attribs = entitySchemaList.stream().findFirst().get().getAttributes();
        assertTrue(CollectionUtils.isNotEmpty(attribs.stream().filter(f -> f.getApiName().equalsIgnoreCase("open_pipeline_count")).collect(Collectors.toList())));
        assertTrue(attribs.stream().filter(f -> f.getApiName().equalsIgnoreCase("open_pipeline_count")).collect(Collectors.toList()).stream().findFirst().isPresent());
        assertTrue(attribs.stream().filter(f -> f.getApiName().equalsIgnoreCase("open_pipeline_count")).collect(Collectors.toList()).stream().findFirst().get().getDataType().equalsIgnoreCase("integer"));
        assertTrue(attribs.stream().filter(f -> f.getApiName().equalsIgnoreCase("syncariDefinedUpdatedAt")).collect(Collectors.toList()).stream().findFirst().get().getDataType().equalsIgnoreCase("datetime"));

    }
}
