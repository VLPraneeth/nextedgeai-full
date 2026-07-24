package com.syncari.restutils.transformers;

import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySinkNodeConfig;
import com.syncari.core.model.EntitySourceNodeConfig;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.NodeConfiguration;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FeatureService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.LayoutService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.GraphHelper;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.restutils.data.EdgeDTO;
import com.syncari.restutils.data.MappingGraphDTO;
import com.syncari.restutils.data.MappingNodeDTO;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;
import com.syncari.core.model.Layout;
import com.syncari.core.model.util.Scope;

public class GraphTransformerTest {

    @Test
    public void transformationFiltersInvalidEdgesInGraph() {
        LayoutService mock = mock(LayoutService.class);

        GraphTransformer graphTransformer = new GraphTransformer();
        graphTransformer.layoutService = mock;
        when(mock.findEdgeLayouts(List.of("111","222","123"))).thenReturn(List.of());
        Edge edgeWithNoSourceStage = new Edge().setDestinationStage(new MappingNode());
        edgeWithNoSourceStage.setId("111");
        Edge edgeWithNoDestStage = new Edge().setSourceStage(new MappingNode());
        edgeWithNoDestStage.setId("222");
        Edge normalEdge = new Edge().setSourceStage(new MappingNode()).setDestinationStage(new MappingNode())
                .setInput(InputPort.any()).setOutput(OutputPort.any());
        normalEdge.setId("123");
        List<EdgeDTO> edgeDTOS = graphTransformer.toEdgeDTO(List.of(edgeWithNoDestStage, edgeWithNoSourceStage, normalEdge), List.of());
        assertEquals(1, edgeDTOS.size());
        Assert.assertEquals("123", edgeDTOS.get(0).getId());
        verify(mock).findEdgeLayouts(List.of("222","111","123"));
    }

    @Test
    public void transformationThrowsValidationErrorWhenNodeMissingType() {
        GraphTransformer graphTransformer = new GraphTransformer();
        try {
            graphTransformer.toNodeConfiguration(new MappingNodeDTO().setLabel("Sync To Account"), new MappingGraphDTO());
            fail();
        }catch(SyncariValidationException v){
            assertEquals("Choose Direction for node 'Sync To Account'",v.getMessage());
        }

    }

    @Test
    public void transformationThrowsValidationErrorForConnectorEntityType() {
        GraphTransformer graphTransformer = new GraphTransformer();
        MappingNodeDTO testDTO = new MappingNodeDTO().setNodeType(MappingNodeType.CONNECTOR_ENTITY);
        testDTO.setName("test");
        try {
            graphTransformer.toNodeConfiguration(testDTO,new MappingGraphDTO());
            fail();
        }catch(SyncariValidationException v){
            Assert.assertEquals("Connector node is not configured, please configure node " + testDTO.getName(),v.getMessage());
        }

    }
    @Test
    public void functionDefinitionShouldNotBeNull(){

        try{
            MappingGraphDTO graphDTO = new MappingGraphDTO();
            MappingNodeDTO dto = new MappingNodeDTO().setNodeType(MappingNodeType.FUNCTION).setId("123");
            graphDTO.setNodes(List.of(dto));
            GraphTransformer graphTransformer = new GraphTransformer();

            FunctionService functionService = mock(FunctionService.class);
            when(functionService.findById(anyString())).thenThrow(new SyncariValidationException("Invalid"));
            //graphTransformer.toNodeConfiguration(dto,new GraphDTO(),new ArrayList<>());
            graphTransformer.toMappingGraph(graphDTO,new ArrayList<>());
        }catch (Exception e){
            fail();
        }


    }

    @Test
    public void transformationCheckEntityAttributes() {
        GraphTransformer graphTransformerSpy = Mockito.spy(new GraphTransformer());
        SchemaService schemaMock = mock(SchemaService.class);
        FeatureService featureService = mock(FeatureService.class);
        graphTransformerSpy.schemaService = schemaMock;
        graphTransformerSpy.featureService = featureService;
        EntityDefinition mockentity = new EntityDefinition("Account", "Account");
        mockentity.addField(new AttributeDefinition().setApiName("attr1"));
        when(schemaMock.getSyncariEntityById(any(String.class))).thenReturn(Optional.of(mockentity));
        MappingNodeDTO testDTO = new MappingNodeDTO().setNodeType(MappingNodeType.CORE_ENTITY);
        testDTO.getConfiguration().put("entityDefinition", "Account");
        NodeConfiguration nodeConfiguration = graphTransformerSpy.toNodeConfiguration(testDTO, new MappingGraphDTO());
        Assert.assertNotNull(nodeConfiguration);
        CoreEntityNodeConfig coreEntityNodeConfig = (CoreEntityNodeConfig) nodeConfiguration;
        Assert.assertNotNull(coreEntityNodeConfig);
        Assert.assertNotNull(coreEntityNodeConfig.getEntityDefinition());
        Assert.assertEquals("Account", coreEntityNodeConfig.getEntityDefinition().getApiName());
        Assert.assertNotNull(coreEntityNodeConfig.getEntityDefinition().getAttributes());
        Assert.assertTrue(coreEntityNodeConfig.getEntityDefinition().getAttributes().size()>0);

        testDTO = new MappingNodeDTO().setNodeType(MappingNodeType.ENTITY_SINK);
        testDTO.getConfiguration().put("entityDefinition", "Account");
        nodeConfiguration = graphTransformerSpy.toNodeConfiguration(testDTO, new MappingGraphDTO());
        Assert.assertNotNull(nodeConfiguration);
        EntitySinkNodeConfig entitySinkNodeConfig = (EntitySinkNodeConfig) nodeConfiguration;
        Assert.assertNotNull(entitySinkNodeConfig);
        Assert.assertNotNull(entitySinkNodeConfig.getEntityDefinition());
        Assert.assertEquals("Account", entitySinkNodeConfig.getEntityDefinition().getApiName());
        Assert.assertNotNull(entitySinkNodeConfig.getEntityDefinition().getAttributes());
        Assert.assertTrue(entitySinkNodeConfig.getEntityDefinition().getAttributes().size()>0);

        testDTO = new MappingNodeDTO().setNodeType(MappingNodeType.ENTITY_SOURCE);
        testDTO.getConfiguration().put("entityDefinition", "Account");
        nodeConfiguration = graphTransformerSpy.toNodeConfiguration(testDTO, new MappingGraphDTO());
        Assert.assertNotNull(nodeConfiguration);
        EntitySourceNodeConfig entitySourceNodeConfig = (EntitySourceNodeConfig) nodeConfiguration;
        Assert.assertNotNull(entitySourceNodeConfig);
        Assert.assertNotNull(entitySourceNodeConfig.getEntityDefinition());
        Assert.assertEquals("Account", entitySourceNodeConfig.getEntityDefinition().getApiName());
        Assert.assertNotNull(entitySourceNodeConfig.getEntityDefinition().getAttributes());
        Assert.assertTrue(entitySourceNodeConfig.getEntityDefinition().getAttributes().size()>0);
    }

    @Test
    public void generateSubLabelTest(){
        GraphTransformer graphTransformerSpy = Mockito.spy(new GraphTransformer());
        ConnectorService mockConnService = mock(ConnectorService.class);
        graphTransformerSpy.connectorService = mockConnService;
        EntityDefinitionRepo mockEntityRepo = mock(EntityDefinitionRepo.class);
        graphTransformerSpy.entityProxyRepo = mockEntityRepo;

        Connector testConn = getTestConnector();
        doReturn(testConn).when(mockConnService).findLite(testConn.getId());

        int i = 0; // track number of time ConnectorService#findLite is called
        // Source entity Node
        EntityDefinition srcEntity = SchemaHelper.createEntityDefinition("srcEntity", testConn).getEntityDefinition();
        MappingNode srcEntityNode = GraphHelper.srcEntityNode(srcEntity, new MappingGraph());
        String sublabelSrc = graphTransformerSpy.generateSubLabel(srcEntityNode);
        assertEquals(testConn.getName(), sublabelSrc);
        verify(mockConnService, times(++i)).findLite(testConn.getId());

        // Sink entity Node
        EntityDefinition sinkEntity = SchemaHelper.createEntityDefinition("sinkEntity", testConn).getEntityDefinition();
        MappingNode sinkEntityNode = GraphHelper.srcEntityNode(sinkEntity, new MappingGraph());
        String sublabelSink = graphTransformerSpy.generateSubLabel(sinkEntityNode);
        assertEquals(testConn.getName(), sublabelSink);
        verify(mockConnService, times(++i)).findLite(testConn.getId());


        // Source Field Node
        doReturn(Optional.of(srcEntity)).when(mockEntityRepo).findById(srcEntity.getId());
        AttributeDefinition srcField = SchemaHelper.createAttribute("srcField", new StringType(), srcEntity.getId());
        MappingNode srcFieldNode = GraphHelper.srcAttributeNode(srcField, new MappingGraph());
        String sublabelSrcField = graphTransformerSpy.generateSubLabel(srcFieldNode);
        assertEquals(testConn.getName() + " " + srcEntity.getDisplayName(), sublabelSrcField);
        verify(mockConnService, times(++i)).findLite(testConn.getId());

        // Sink Field Node
        doReturn(Optional.of(sinkEntity)).when(mockEntityRepo).findById(sinkEntity.getId());
        AttributeDefinition sinkField = SchemaHelper.createAttribute("sinkField", new StringType(), sinkEntity.getId());
        MappingNode sinkFieldNode = GraphHelper.srcAttributeNode(sinkField, new MappingGraph());
        String sublabelSinkField = graphTransformerSpy.generateSubLabel(sinkFieldNode);
        assertEquals(testConn.getName() + " " + sinkEntity.getDisplayName(), sublabelSinkField);
        verify(mockConnService, times(++i)).findLite(testConn.getId());
    }

    @Test
    public void testDuplicateLayoutKeyHandledGracefullyInToEdgeDTO() {
        LayoutService mock = mock(LayoutService.class);

        GraphTransformer graphTransformer = new GraphTransformer();
        graphTransformer.layoutService = mock;

        // Create edge with specific ID that will have duplicate layouts
        Edge edge = new Edge().setSourceStage(new MappingNode()).setDestinationStage(new MappingNode())
                .setInput(InputPort.any()).setOutput(OutputPort.any());
        edge.setId("09e37978"); // Using the same ID from the error logs

        // Create duplicate Layout objects with the same targetId - this simulates the race condition
        Layout layout1 = Layout.edge("09e37978", "2", "3");
        Layout layout2 = Layout.edge("09e37978", "4", "5"); // Duplicate targetId but different properties

        List<Layout> duplicateLayouts = List.of(layout1, layout2);

        // This should now work gracefully without throwing an exception
        List<EdgeDTO> result = graphTransformer.toEdgeDTO(List.of(edge), duplicateLayouts);

        // Verify the result contains the edge
        Assert.assertEquals("Should have one edge DTO", 1, result.size());
        EdgeDTO edgeDTO = result.get(0);
        Assert.assertEquals("Edge ID should match", "09e37978", edgeDTO.getId());

        // Verify it uses the first layout (existing) values "2" and "3", not the duplicate "4" and "5"
        Assert.assertEquals("Should use first layout's srcAnchor", "2", edgeDTO.getSource().getAnchor());
        Assert.assertEquals("Should use first layout's destAnchor", "3", edgeDTO.getDestination().getAnchor());
    }

    // Note: Duplicate layout handling for nodes is covered by the same fix in toMappingNodeDTO
    // The same merge function (existing, replacement) -> existing is used for both edge and node layouts
    // This prevents IllegalStateException: Duplicate key when multiple Layout objects have the same targetId

    private Connector getTestConnector() {
        var connector = new Connector("testSynapse", "testMetaId", "http://someurl");
        connector.setId("testSynapseId");
        return connector;
    }
}
