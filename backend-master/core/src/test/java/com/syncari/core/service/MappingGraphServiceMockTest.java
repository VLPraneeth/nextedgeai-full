package com.syncari.core.service;

import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.mapper.AutoFieldMapperFactory;
import com.syncari.core.mapper.MapperType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.service.mapper.AutoFieldMapper;
import com.syncari.core.service.mapper.LLMAutoFieldMapper;
import com.syncari.core.service.mapper.LuceneAutoFieldMapper;
import com.syncari.core.utils.GraphHelper;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.utils.SchemaHelper.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class MappingGraphServiceMockTest {

    @Test
    public void mappedFieldsInPublishedExcludedFromAutomap() {
        var src = createEntityDefinition("sourceEntity")
                .field("First Name", "fName")
                .field("Last Name", "lName")
                .field("Email Address", "emailAddr")
                .getEntityDefinition();
        var syncari = createEntityDefinition("syncariEntity")
                .field("First", "first_name")
                .field("Last", "last_name")
                .field("Email", "email")
                .getEntityDefinition();
        var mgs = new MappingGraphService() {
            public Optional<MappingGraph> retrieveDraftEntityGraph(String syncariEntityId) {
                return Optional.empty();
            }

            @Override
            public Optional<MappingGraph> retrieveApprovedEntityGraph(String syncariEntityId) {
                final MappingGraph graph = new MappingGraph();
                graph.setDraftStatus(DraftStatus.APPROVED);
                graph.setId("gid");
                return Optional.of(graph);
            }

            @Override
            public List<MappingGraph> retrieveApprovedAttributeGraphs(String entityGraphId) {
                return List.of(GraphHelper.newGraph(syncari.getFieldByName("first_name"))
                        .src(src.getFieldByName("fName"))
                        .connect("fName", "first_name").getGraph());
            }
        };
        mgs.mapperFactory = mock(AutoFieldMapperFactory.class);
        when(mgs.mapperFactory.getMapper(MapperType.BASIC_SEARCH)).thenReturn(new LuceneAutoFieldMapper());

        final Map<AttributeDefinition, AttributeDefinition> automap = mgs.automap(src, syncari, MapperType.BASIC_SEARCH);
        assertFalse("Expecting atleast one automapping", automap.isEmpty());
        assertFalse("First Name is already mapped. Should not be automapped", automap.containsKey(src.getFieldByName("fName")));
        assertTrue(automap.containsKey(src.getFieldByName("lName")));
        assertEquals(syncari.getFieldByName("last_name"), automap.get(src.getFieldByName("lName")));
        assertTrue(automap.containsKey(src.getFieldByName("emailAddr")));
        assertEquals(syncari.getFieldByName("email"), automap.get(src.getFieldByName("emailAddr")));
    }

    @Test
    public void mappedFieldsInDraftExcludedFromAutomap() {
        var src = createEntityDefinition("sourceEntity")
                .field("First Name", "fName")
                .field("Last Name", "lName")
                .field("Email Address", "emailAddr")
                .getEntityDefinition();
        var syncari = createEntityDefinition("syncariEntity")
                .field("First", "first_name")
                .field("Last", "last_name")
                .field("Email", "email")
                .getEntityDefinition();
        var mgs = new MappingGraphService() {
            @Override
            public Optional<MappingGraph> retrieveDraftEntityGraph(String syncariEntityId) {
                final MappingGraph graph = new MappingGraph();
                graph.setId("gid");
                return Optional.of(graph);
            }

            @Override
            public List<MappingGraph> retrieveDraftAttributeGraphs(String entityGraphId) {
                return List.of(GraphHelper.newGraph(syncari.getFieldByName("first_name"))
                        .src(src.getFieldByName("fName"))
                        .connect("fName", "first_name").getGraph());
            }
        };
        mgs.mapperFactory = mock(AutoFieldMapperFactory.class);
        when(mgs.mapperFactory.getMapper(MapperType.BASIC_SEARCH)).thenReturn(new LuceneAutoFieldMapper());

        final Map<AttributeDefinition, AttributeDefinition> automap = mgs.automap(src, syncari, MapperType.BASIC_SEARCH);
        assertFalse("Expecting atleast one automapping", automap.isEmpty());
        assertFalse("First Name is already mapped. Should not be automapped", automap.containsKey(src.getFieldByName("fName")));
        assertTrue(automap.containsKey(src.getFieldByName("lName")));
        assertEquals(syncari.getFieldByName("last_name"), automap.get(src.getFieldByName("lName")));
        assertTrue(automap.containsKey(src.getFieldByName("emailAddr")));
        assertEquals(syncari.getFieldByName("email"), automap.get(src.getFieldByName("emailAddr")));
    }

    @Test
    public void newFieldsCreatedForUnmappableFields() {
        var src = createEntityDefinition("sourceEntity")
                .field("First Name", "fName")
                .field("Last Name", "lName")
                .field("Email Address", "emailAddr")
                .getEntityDefinition();
        var syncari = createEntityDefinition("syncariEntity")
                .field("First", "first_name")
                .getEntityDefinition();
        var mgs = new MappingGraphService() {
            @Override
            public Optional<MappingGraph> retrieveDraftEntityGraph(String syncariEntityId) {
                final MappingGraph graph = new MappingGraph();
                graph.setId("gid");
                return Optional.of(graph);
            }

            @Override
            public List<MappingGraph> retrieveDraftAttributeGraphs(String entityGraphId) {
                return List.of();
            }
        };
        mgs.mapperFactory = mock(AutoFieldMapperFactory.class);
        when(mgs.mapperFactory.getMapper(MapperType.BASIC_SEARCH)).thenReturn(new LuceneAutoFieldMapper());

        final Map<AttributeDefinition, AttributeDefinition> automap = mgs.automapWithCreate(src, syncari, MapperType.BASIC_SEARCH);
        assertFalse("Expecting atleast one automapping", automap.isEmpty());
        assertTrue(automap.containsKey(src.getFieldByName("fName")));
        assertTrue(automap.containsKey(src.getFieldByName("lName")));
        //id not set on new field
        assertNull(automap.get(src.getFieldByName("lName")).getId());
        assertTrue(automap.containsKey(src.getFieldByName("emailAddr")));
        //id not set on new field
        assertNull(automap.get(src.getFieldByName("emailAddr")).getId());
    }

    @Test
    public void validationRealtimePipelineWhenNoSourcePresent() {
        var mgs = new MappingGraphService();
        final ConnectorService connectorService = mock(ConnectorService.class);
        mgs.setConnectorService(connectorService);
        final EntityDefinition testEntity = createEntityDefinition("test").getEntityDefinition();
        final MappingGraph graphWithNoSources = GraphHelper.newGraph(testEntity).getGraph();
        graphWithNoSources.setSettings(new PipelineSettings().setRealtimePipeline(true));
        final List<ValidationError> validationErrorsNoSource = mgs.validateRealTimeGraph(graphWithNoSources);
        assertEquals(1, validationErrorsNoSource.size());
        assertEquals("No source found. Configure a webhook source for this realtime pipeline", validationErrorsNoSource.get(0).getMessage());
        final Connector connector1 = createConnector();
        final EntityDefinition src1 = createEntityDefinition("src1", connector1).getEntityDefinition();
        final Connector connector2 = createConnector();
        final EntityDefinition src2 = createEntityDefinition("src2", connector2).getEntityDefinition();
        final MappingGraph graphWithMultipleSources = GraphHelper.newGraph(testEntity)
                .src(src1)
                .src(src2)
                .connect("src1", "test")
                .connect("src2", "test").getGraph();
        graphWithMultipleSources.setSettings(new PipelineSettings().setRealtimePipeline(true));
        when(connectorService.findByEntityDefId(Optional.of(src1.getId()))).thenReturn(Optional.of(connector1));
        when(connectorService.findByEntityDefId(Optional.of(src2.getId()))).thenReturn(Optional.of(connector2));
        final List<ValidationError> validationErrorsMultipleSources = mgs.validateRealTimeGraph(graphWithMultipleSources);
        assertEquals("There are more than one sources in this realtime pipeline", validationErrorsMultipleSources.get(0).getMessage());
        final String expectedSrc1Error = String.format("Entity %s from connector %s is not a webhook entity. " +
                "Only Webhook entities are allowed as a source on realtime pipelines.", src1.getDisplayName(), connector1.getName());
        final String expectedSrc2Error = String.format("Entity %s from connector %s is not a webhook entity. " +
                "Only Webhook entities are allowed as a source on realtime pipelines.", src2.getDisplayName(), connector2.getName());

        final String expectedWebhookErrorMessage = "There are more than one sources in this realtime pipeline";
        assertEquals(expectedWebhookErrorMessage, validationErrorsMultipleSources.get(0).getMessage());
        assertEquals(expectedSrc1Error, validationErrorsMultipleSources.get(1).getMessage());
        assertEquals(expectedSrc2Error, validationErrorsMultipleSources.get(2).getMessage());

        connector1.getMetadata().setWebhook(true);
        final MappingGraph validGraph = GraphHelper.newGraph(testEntity)
                .src(src1)
                .connect("src1", "test")
                .getGraph();
        validGraph.setSettings(new PipelineSettings().setRealtimePipeline(true));
        assertEquals(0, mgs.validateRealTimeGraph(validGraph).size());
        final MappingGraph fp = GraphHelper.newGraph(createAttribute("test", StringType.VALUE, testEntity.getId()))
                .src(createAttribute("src1", StringType.VALUE, src1.getId()))
                .connect("src1", "test")
                .getGraph();
        assertEquals(0, mgs.validateRealTimeGraph(fp).size());
    }

    @Test
    public void testAutomapWithLLMMapper() {
        var src = createEntityDefinition("sourceEntity")
                .field("First Name", "fName")
                .field("Last Name", "lName")
                .field("Email Address", "emailAddr")
                .getEntityDefinition();
        var syncari = createEntityDefinition("syncariEntity")
                .field("First", "first_name")
                .field("Last", "last_name")
                .field("Email", "email")
                .getEntityDefinition();
        var mgs = new MappingGraphService() {
            @Override
            public Optional<MappingGraph> retrieveDraftEntityGraph(String syncariEntityId) {
                return Optional.empty();
            }

            @Override
            public Optional<MappingGraph> retrieveApprovedEntityGraph(String syncariEntityId) {
                return Optional.empty();
            }
        };

        // Mock the mapper itself to avoid LLM service dependency
        mgs.mapperFactory = mock(AutoFieldMapperFactory.class);
        AutoFieldMapper mockLLMMapper = mock(AutoFieldMapper.class);

        // Mock the mapping result
        Map<AttributeDefinition, AttributeDefinition> expectedMappings = Map.of(
                src.getFieldByName("lName"), syncari.getFieldByName("last_name"),
                src.getFieldByName("emailAddr"), syncari.getFieldByName("email")
        );
        when(mockLLMMapper.automap(anyList(), anyList())).thenReturn(expectedMappings);
        when(mgs.mapperFactory.getMapper(MapperType.SYNC_AI)).thenReturn(mockLLMMapper);

        final Map<AttributeDefinition, AttributeDefinition> automap = mgs.automap(src, syncari, MapperType.SYNC_AI);

        // Verify that LLM mapper was used
        assertNotNull("Automap should return a result", automap);
        verify(mgs.mapperFactory).getMapper(MapperType.SYNC_AI);
    }

    @Test
    public void testAutomapWithCreateUsingLLMMapper() {
        var src = createEntityDefinition("sourceEntity")
                .field("First Name", "fName")
                .field("Last Name", "lName")
                .field("Custom Field", "customField")
                .getEntityDefinition();
        var syncari = createEntityDefinition("syncariEntity")
                .field("First", "first_name")
                .getEntityDefinition();
        var mgs = new MappingGraphService() {
            @Override
            public Optional<MappingGraph> retrieveDraftEntityGraph(String syncariEntityId) {
                return Optional.empty();
            }

            @Override
            public Optional<MappingGraph> retrieveApprovedEntityGraph(String syncariEntityId) {
                return Optional.empty();
            }
        };

        // Mock the mapper to avoid LLM service dependency
        mgs.mapperFactory = mock(AutoFieldMapperFactory.class);
        AutoFieldMapper mockLLMMapper = mock(AutoFieldMapper.class);

        // Use HashMap instead of Map.of() because automapWithCreate modifies the map
        Map<AttributeDefinition, AttributeDefinition> expectedMappings = new java.util.HashMap<>();
        expectedMappings.put(src.getFieldByName("fName"), syncari.getFieldByName("first_name"));
        when(mockLLMMapper.automap(anyList(), anyList())).thenReturn(expectedMappings);
        when(mgs.mapperFactory.getMapper(MapperType.SYNC_AI)).thenReturn(mockLLMMapper);

        final Map<AttributeDefinition, AttributeDefinition> automap = mgs.automapWithCreate(src, syncari, MapperType.SYNC_AI);

        assertNotNull("Automap with create should return a result", automap);
        verify(mgs.mapperFactory).getMapper(MapperType.SYNC_AI);
    }

    @Test
    public void testMapperTypeRoutingBasicMapper() {
        var src = createEntityDefinition("sourceEntity")
                .field("First Name", "fName")
                .getEntityDefinition();
        var syncari = createEntityDefinition("syncariEntity")
                .field("First", "first_name")
                .getEntityDefinition();
        var mgs = new MappingGraphService() {
            @Override
            public Optional<MappingGraph> retrieveDraftEntityGraph(String syncariEntityId) {
                return Optional.empty();
            }

            @Override
            public Optional<MappingGraph> retrieveApprovedEntityGraph(String syncariEntityId) {
                return Optional.empty();
            }
        };

        // Create a spy/mock to verify which mapper is called
        AutoFieldMapperFactory mockFactory = mock(AutoFieldMapperFactory.class);
        LuceneAutoFieldMapper luceneMapper = new LuceneAutoFieldMapper();
        when(mockFactory.getMapper(MapperType.BASIC_SEARCH)).thenReturn(luceneMapper);

        mgs.mapperFactory = mockFactory;

        mgs.automap(src, syncari, MapperType.BASIC_SEARCH);

        // Verify that basic mapper was requested
        verify(mockFactory).getMapper(MapperType.BASIC_SEARCH);
    }

    @Test
    public void testMapperTypeRoutingSyncAI() {
        var src = createEntityDefinition("sourceEntity")
                .field("First Name", "fName")
                .getEntityDefinition();
        var syncari = createEntityDefinition("syncariEntity")
                .field("First", "first_name")
                .getEntityDefinition();
        var mgs = new MappingGraphService() {
            @Override
            public Optional<MappingGraph> retrieveDraftEntityGraph(String syncariEntityId) {
                return Optional.empty();
            }

            @Override
            public Optional<MappingGraph> retrieveApprovedEntityGraph(String syncariEntityId) {
                return Optional.empty();
            }
        };

        // Create a spy/mock to verify which mapper is called
        AutoFieldMapperFactory mockFactory = mock(AutoFieldMapperFactory.class);
        AutoFieldMapper mockLLMMapper = mock(AutoFieldMapper.class);

        // Mock the return value to avoid NullPointerException
        when(mockLLMMapper.automap(anyList(), anyList())).thenReturn(Map.of());
        when(mockFactory.getMapper(MapperType.SYNC_AI)).thenReturn(mockLLMMapper);

        mgs.mapperFactory = mockFactory;

        mgs.automap(src, syncari, MapperType.SYNC_AI);

        // Verify that LLM mapper was requested
        verify(mockFactory).getMapper(MapperType.SYNC_AI);
    }

}