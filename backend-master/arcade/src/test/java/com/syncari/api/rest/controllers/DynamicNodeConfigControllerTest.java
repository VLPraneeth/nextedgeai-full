package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.rest.controllers.data.NodeDef;
import com.syncari.connector.Constants;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.*;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.Schema;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.restutils.data.EdgeDTO;
import com.syncari.restutils.data.MappingGraphDTO;
import com.syncari.restutils.data.MappingNodeDTO;
import com.syncari.restutils.data.NodeRef;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.utils.KeyValue;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;


public class DynamicNodeConfigControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    PipelineController pipelineController;

    @Autowired
    DynamicNodeConfigController nodeConfigController;

    @Autowired
    MappingGraphService graphService;


    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    private FunctionService functionService;

    @Autowired
    SchemaService schemaService;

    @Autowired
    ObjectMapper mapper;
    @Autowired
    MappingNodeRepo  nodeRepo;

    private static Connector connector;

    @Autowired
    private ConnectorService connectorService;
    @Autowired
    private EndSystemConfig config;

    @Autowired
    private GraphTransformer transformer;
    @Autowired
    private EdgeRepo edgeRepo;

    Schema syncariSchema;

    @Override
    public void setUp() {
        super.setUp();
        mappingGraphRepo.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();

        if(connector == null) {
            connector = new Connector("dynamicnode", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://someurl");
            connector = connectorService.save(connector);
            connectorService.authenticated(connector.getId());
            connectorService.activate(connector.getId());
        }
        schemaService.activateMapping(connector);
        if(syncariSchema == null) syncariSchema = schemaService.getSyncariSchema();
        pushContext();
    }

    @Override
    public void tearDown() {
        restoreContext();
        mappingGraphRepo.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
        super.tearDown();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void filterNodeConfig() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var sfdcAccount = schemaService.getEntity(connector.getId(), "account");

        var filterFunction =  functionService.findByNameAndScope("filter",Scope.ENTITY).orElseThrow();

        var graph0 = pipelineController.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ENTITY).findFirst().get();
        var entitySource = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get();

        MappingNodeDTO filterNode = new MappingNodeDTO()
                .setName(sfdcAccount.getDisplayName())
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(Map.of("definition",filterFunction.getId()))
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());

        graph0.getNodes().add(filterNode);
        
        MappingNodeDTO tempVariable = getSetValueNode(entityDef, "temp");
        graph0.getNodes().add(tempVariable);

        var result = mvc.perform(
                post("/api/v1/nodeConfig/{currentNodeId}", filterNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        var config = mapper.readValue(result.getResponse().getContentAsString(), NodeDef.class);

        assertEquals(12, config.getConfiguration().size());
        //Add edges now
        List<EdgeDTO> newEdges = new ArrayList<>(graph0.getEdges().stream().filter(e -> !e.getSource().getNodeId().equals(entitySource.getId())).collect(Collectors.toList()));
        EdgeDTO filterToCore = new EdgeDTO()
                .setDestination(new NodeRef(filterNode.getId(), filterNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(entitySource.getId(), entitySource.getOutputPorts().get(0),"0"));
        newEdges.add(filterToCore);

        
        EdgeDTO sourceToTemp = new EdgeDTO()
                .setDestination(new NodeRef(syncariNode.getId(), syncariNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(tempVariable.getId(), tempVariable.getOutputPorts().get(0),"0"));
        newEdges.add(sourceToTemp);
        
        EdgeDTO tempToFilter = new EdgeDTO()
        		.setDestination(new NodeRef(filterNode.getId(), filterNode.getInputPorts().get(0),"0"))
        		.setSource(new NodeRef(tempVariable.getId(), tempVariable.getOutputPorts().get(0),"0"));
        newEdges.add(tempToFilter);
        
        graph0.setEdges(newEdges);
        result = mvc.perform(
                post("/api/v1/nodeConfig/{currentNodeId}", filterNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        config = mapper.readValue(result.getResponse().getContentAsString(), NodeDef.class);
        assertEquals(12, config.getConfiguration().size());
        KeyValue fieldConfig = config.getConfiguration().stream().filter(c ->"field".equals(c.get("name"))).findFirst().get();
        assertEquals(((List<LinkedHashMap>)fieldConfig.get("values")).get(2).get("label"), "Account Name (name)");
        var sfdcAttributeIds = sfdcAccount.getAttributes().stream().map(a->a.getId()).collect(Collectors.toSet());
        var configAttributeIds = ((List<Map<String,Object>>)fieldConfig.get("values")).stream().map(a->a.get("value").toString()).collect(Collectors.toSet());
        for(String sfdcAttributeId : sfdcAttributeIds){
            assertTrue(configAttributeIds.contains(sfdcAttributeId));
        }
        //Assert output from previous node is also a selectable value in filter
        assertTrue(configAttributeIds.contains("output_"+entitySource.getId()+".x.typedValue"));
        assertTrue(configAttributeIds.contains("{{syncari.temp.testvar1}}"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void attributeFilterNodeConfigWithLookup() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var sfdcAccount = schemaService.getEntity(connector.getId(), "account");

        AttributeDefinition sourceName = sfdcAccount.getFieldByName("name");
        AttributeDef coreName = entityDef.getField("Name").get();
        var filterFunction =  functionService.findByNameAndScope("filter",Scope.ATTRIBUTE).orElseThrow();
        var lookupFunction =  functionService.findByNameAndScope("advancedLookUpSyncariRecordOnField",Scope.ATTRIBUTE).orElseThrow();

        var graph0 = pipelineController.getFieldPipeline(coreName.getId());

        var coreNameNode = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get();
        var sourceNameNode = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get();
        var destNameNode = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SINK).findFirst().get();

        MappingNodeDTO lookupNode = new MappingNodeDTO()
                .setName(sfdcAccount.getDisplayName())
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(Map.of("definition",lookupFunction.getId()))
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());

        MappingNodeDTO filterNode = new MappingNodeDTO()
                .setName(sfdcAccount.getDisplayName())
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(Map.of("definition",filterFunction.getId()))
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());

        graph0.getNodes().add(filterNode);
        graph0.getNodes().add(lookupNode);
        EdgeDTO sourceToLookup = new EdgeDTO()
                .setDestination(new NodeRef(lookupNode.getId(), lookupNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(sourceNameNode.getId(), sourceNameNode.getOutputPorts().get(0),"0"));
        sourceToLookup.setId(ObjectId.get().toHexString());
        EdgeDTO lookupToFilter = new EdgeDTO()
                .setDestination(new NodeRef(filterNode.getId(), filterNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(lookupNode.getId(), lookupNode.getOutputPorts().get(0),"0"));
        lookupToFilter.setId(ObjectId.get().toHexString());
        EdgeDTO coreToDest = new EdgeDTO()
                .setDestination(new NodeRef(destNameNode.getId(), destNameNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(coreNameNode.getId(), coreNameNode.getOutputPorts().get(0),"0"));
        coreToDest.setId(ObjectId.get().toHexString());
        graph0.setEdges(List.of(sourceToLookup,lookupToFilter,coreToDest));

        var result = mvc.perform(
                post("/api/v1/nodeConfig/{currentNodeId}", filterNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        var config = mapper.readValue(result.getResponse().getContentAsString(), NodeDef.class);

        assertEquals(12, config.getConfiguration().size());
        //Add edges now
        result = mvc.perform(
                post("/api/v1/nodeConfig/{currentNodeId}", filterNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        config = mapper.readValue(result.getResponse().getContentAsString(), NodeDef.class);
        assertEquals(12, config.getConfiguration().size());
        KeyValue fieldConfig = config.getConfiguration().stream().filter(c ->"field".equals(c.get("name"))).findFirst().get();
        var sfdcAttributeIds = sfdcAccount.getAttributes().stream().map(a->a.getId()).collect(Collectors.toSet());
        var configAttributeIds = ((List<Map<String,Object>>)fieldConfig.get("values")).stream().map(a->a.get("value").toString()).collect(Collectors.toSet());
        var configAttributeLabels = ((List<Map<String,Object>>)fieldConfig.get("values")).stream().map(a->a.get("label").toString()).collect(Collectors.toSet());
        for(String sfdcAttributeId : sfdcAttributeIds){
            assertTrue(configAttributeIds.contains(sfdcAttributeId));
        }
        //Make sure the filter field list contains all core attribute ids
        for(AttributeDef coreAttribute : entityDef.getActiveFields()){
            assertTrue(configAttributeIds.contains(coreAttribute.getId()));
            assertTrue(configAttributeLabels.contains("Syncari: "+coreAttribute.getDisplayName()));
        }
        //Assert output from previous node is also a selectable value in filter
        assertTrue(configAttributeIds.contains("output_"+lookupNode.getId()+".x.typedValue"));
        assertTrue(configAttributeIds.contains("output_"+lookupNode.getId()+".x.lookupResult"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void enrichPersonConfig() throws Exception {
        var graph = createPipelineWithFunction(functionService.findByNameAndScope(FunctionConstants.ENRICH_PERSON, Scope.ATTRIBUTE).orElseThrow());
        var funcNode = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.FUNCTION).findFirst();

        var result = mvc.perform(
                post("/api/v1/nodeConfig/{currentNodeId}", funcNode.get().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph))
        ).andReturn();
        var config = mapper.readValue(result.getResponse().getContentAsString(), NodeDef.class);

        assertEquals(Scope.ATTRIBUTE, graph.getScope());
        assertEquals("Enrich Person", config.getDisplayName());
        assertNotNull(config.getConfiguration().stream().filter(f -> "Source Entity".equalsIgnoreCase(f.get("label"))).findFirst().get().get("values"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void enrichCompanyConfig() throws Exception {
        var graph = createPipelineWithFunction(functionService.findByNameAndScope(FunctionConstants.ENRICH_COMPANY, Scope.ATTRIBUTE).orElseThrow());
        var funcNode = graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.FUNCTION).findFirst();

        var result = mvc.perform(
                post("/api/v1/nodeConfig/{currentNodeId}", funcNode.get().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph))
        ).andReturn();
        var config = mapper.readValue(result.getResponse().getContentAsString(), NodeDef.class);

        assertEquals(Scope.ATTRIBUTE, graph.getScope());
        assertEquals("Enrich Company", config.getDisplayName());
        assertNotNull(config.getConfiguration().stream().filter(f -> "Source Entity".equalsIgnoreCase(f.get("label"))).findFirst().get().get("values"));
    }

    private MappingGraphDTO createPipelineWithFunction(FunctionDefinition funcDef) {

        var sfdcAccount = schemaService.getEntity(connector.getId(), "account");
        var sfdcAccountNameAttribute = sfdcAccount.getFieldByName("name");

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var entityGraph = pipelineController.getEntityPipeline(entityDef.getId());
        AttributeDef attributeDef = entityDef.getFields().stream().filter(a->a.getApiName().equals("Name")).findFirst().orElseThrow();
        setDefaultValueOnSinkNode(attributeDef);

        var graph0 = pipelineController.getFieldPipeline(attributeDef.getId());

        var syncariNode = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get();
        var attributeSource = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get();
        var attributeSink = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ATTRIBUTE_SINK).findFirst().get();
        assertEquals(sfdcAccountNameAttribute.getEntityId()+"_source",attributeSource.getRequiredConfiguration("configId"));

        var functionConfig = new SimpleFunctionNodeConfig().setFunctionCall(new FunctionCall().setFunctionDefinition(funcDef)
                .setParams(List.of(ParameterValue.string("output_"+attributeSource.getId(),"input"))));

        var newEdges = new ArrayList<EdgeDTO>();
        newEdges.add(graph0.getEdges().stream().filter(e -> e.getDestination().getNodeId().equals(attributeSink.getId())).findFirst().get());
        MappingNodeDTO enrichFunctionNode = new MappingNodeDTO()
                .setName(funcDef.getDisplayName())
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(functionConfig.getConfigMap())
                .setOutputPorts(transformer.toOutputPortDTO(functionConfig.getOutputPorts()))
                .setInputPorts(transformer.toInputPortDTO(functionConfig.getInputPorts()))
                .setLocation(Map.of("x",100,"y",200))
                .setId(new ObjectId().toHexString());
        graph0.getNodes().add(enrichFunctionNode);

        EdgeDTO sourceToFunction = new EdgeDTO()
                .setDestination(new NodeRef(enrichFunctionNode.getId(), enrichFunctionNode.getInputPorts().get(0),"1"))
                .setSource(new NodeRef(attributeSource.getId(), attributeSource.getOutputPorts().get(0),"2"))
                .setId(new ObjectId().toHexString());
        newEdges.add(sourceToFunction);

        EdgeDTO functionToSyncari = new EdgeDTO()
                .setDestination(new NodeRef(syncariNode.getId(), syncariNode.getInputPorts().get(0),"3"))
                .setSource(new NodeRef(enrichFunctionNode.getId(), enrichFunctionNode.getOutputPorts().get(0),"4"))
                .setId(new ObjectId().toHexString());

        newEdges.add(functionToSyncari);
        graph0.setEdges(newEdges);

        pipelineController.createFieldPipeline(attributeDef.getId(), graph0);
        return pipelineController.getFieldPipeline(attributeDef.getId());
    }

    private void setDefaultValueOnSinkNode(AttributeDef attributeDef) {
        MappingGraph nameGraph = getOrCreateAttributeGraph(attributeDef);
        nameGraph.getSinks().forEach(sink -> {
            ((AttributeSinkNodeConfig) sink.getConfiguration()).setDefaultValue("Default");
            graphService.upsertAttributeGraph(nameGraph);
        });
    }

    private MappingGraph getOrCreateAttributeGraph(AttributeDef attributeDef) {
        return graphService.retrieveAttributeGraph(attributeDef.getId()).orElseGet(()-> graphService.createDefaultAttributeGraph(attributeDef.getId()));
    }
    
    private MappingNodeDTO getSetValueNode(EntityDef entityDef, String name) {
        var setValueFunction =  functionService.findByNameAndScope(FunctionConstants.SET_VALUE_ON_ENTITY,Scope.ENTITY).orElseThrow();
        MappingNodeDTO setValueNode = new MappingNodeDTO()
                .setName(name)
                .setNodeType(MappingNodeType.FUNCTION)
				.setConfiguration(Map.of("definition", setValueFunction.getId(), "setValueField", Map.of("type", "temporary", "apiName",
						"testVar1", "displayName", "Test Variable", "dataType", "string"), "newValue", "123"))
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());
        return setValueNode;
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void coreEntityNodeConfig() throws Exception {

        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        var sfdcAccount = schemaService.getEntity(connector.getId(), "account");

        var filterFunction =  functionService.findByNameAndScope("filter",Scope.ENTITY).orElseThrow();

        var graph0 = pipelineController.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ENTITY).findFirst().get();
        var entitySource = graph0.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE).findFirst().get();

        MappingNodeDTO filterNode = new MappingNodeDTO()
                .setName(sfdcAccount.getDisplayName())
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(Map.of("definition",filterFunction.getId()))
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());

        graph0.getNodes().add(filterNode);
        
        MappingNodeDTO tempVariable = getSetValueNode(entityDef, "temp");
        graph0.getNodes().add(tempVariable);

        var result = mvc.perform(
                post("/api/v1/nodeConfig/{currentNodeId}", syncariNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        var config = mapper.readValue(result.getResponse().getContentAsString(), NodeDef.class);

        assertEquals(23, config.getConfiguration().size());
        //Add edges now
        List<EdgeDTO> newEdges = new ArrayList<>(graph0.getEdges().stream().filter(e -> !e.getSource().getNodeId().equals(entitySource.getId())).collect(Collectors.toList()));
        EdgeDTO filterToCore = new EdgeDTO()
                .setDestination(new NodeRef(filterNode.getId(), filterNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(entitySource.getId(), entitySource.getOutputPorts().get(0),"0"));
        newEdges.add(filterToCore);

        
        EdgeDTO sourceToTemp = new EdgeDTO()
                .setDestination(new NodeRef(syncariNode.getId(), syncariNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(tempVariable.getId(), tempVariable.getOutputPorts().get(0),"0"));
        newEdges.add(sourceToTemp);
        
        EdgeDTO tempToFilter = new EdgeDTO()
        		.setDestination(new NodeRef(filterNode.getId(), filterNode.getInputPorts().get(0),"0"))
        		.setSource(new NodeRef(tempVariable.getId(), tempVariable.getOutputPorts().get(0),"0"));
        newEdges.add(tempToFilter);
        
        graph0.setEdges(newEdges);
        result = mvc.perform(
                post("/api/v1/nodeConfig/{currentNodeId}", syncariNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph0))
        ).andReturn();
        config = mapper.readValue(result.getResponse().getContentAsString(), NodeDef.class);
        assertEquals(23, config.getConfiguration().size());
        KeyValue skipWhen = config.getConfiguration().stream().filter(c ->"skipWhenCriteria".equals(c.get("id"))).findFirst().get();
        assertTrue(((List<LinkedHashMap>) skipWhen.get("fieldValues")).stream().count() >= 40);
    }

}
