package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.xml.bind.DatatypeConverter;

import com.syncari.connector.Constants;
import com.syncari.connector.service.TestSynapseService;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.schema.Schema;
import com.syncari.restutils.data.EdgeDTO;
import com.syncari.restutils.data.MappingGraphDTO;
import com.syncari.restutils.data.MappingNodeDTO;
import com.syncari.restutils.data.NodeRef;
import com.syncari.restutils.transformers.GraphTransformer;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.functions.FunctionConstants;
import com.syncari.core.model.Connector;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import static org.hamcrest.Matchers.*;


public class TokenControllerTest extends AbstractSyncariTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    PipelineController pipelineController;

    @Autowired
    MappingGraphService graphService;

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
            connector = new Connector("tokens", connectorService.describe(Constants.TEST_SYNAPSE).getId(), "http://someurl");
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
    public void getTokensOnlyOneSynapseInput() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        EntityDefinition synapseEntity = schemaService.getEntity(connector.getId(), "account");
        var graph0 = pipelineController.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = getSyncariNode(graph0);
        var entitySource = getSynapseNode(graph0);

        MappingNodeDTO filterNode = getFilterFunctionNode(entityDef);
        graph0.getNodes().add(filterNode);

        List<EdgeDTO> newEdges = getEdges(graph0, entitySource, filterNode);

        EdgeDTO sourceToFilter = new EdgeDTO()
                .setDestination(new NodeRef(syncariNode.getId(), syncariNode.getInputPorts().get(0),"0"))
                .setSource(new NodeRef(filterNode.getId(), filterNode.getOutputPorts().get(0),"0"));
        newEdges.add(sourceToFilter);
        graph0.setEdges(newEdges);
        var fieldMap = callRest(graph0, filterNode);

        List synapseList = (List)fieldMap.get("Synapse");
        assertEquals(synapseEntity.getAttributes().size(), synapseList.size());
        Map fieldConfig = (Map) synapseList.get(0);
        assertEquals("tokens / Account / Account Description (description)", fieldConfig.get("label").toString());
        assertEquals("textarea", fieldConfig.get("datatype").toString());
        assertEquals("Synapse", fieldConfig.get("group").toString());
        assertEquals("{{tokens.account.description}}", fieldConfig.get("token").toString());

        assertFalse(fieldMap.containsKey("Previous"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getTokensOnlySyncariInput() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();

        var graph0 = pipelineController.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = getSyncariNode(graph0);

        MappingNodeDTO filterNode = getFilterFunctionNode(entityDef);
        graph0.getNodes().add(filterNode);

        //Add edges now
        graph0.setEdges(getEdges(graph0, syncariNode, filterNode));

        var fieldMap = callRest(graph0, filterNode);

        List syncariList = (List)fieldMap.get("Syncari");
        assertTrue(syncariList.size() >= 40);
        Optional<Map> token = syncariList.stream().filter(l -> ((Map) l).get("token").equals("{{record.syncariEntityId}}")).findFirst();
        assertTrue(token.isPresent());
        Map fieldConfig = (Map) syncariList.get(0);
        assertTrue("syncari / Account / About Us (AboutUs)".equalsIgnoreCase(fieldConfig.get("label").toString()));
        assertEquals("string", fieldConfig.get("datatype").toString());
        assertEquals("Syncari", fieldConfig.get("group").toString());
        assertEquals("{{record.values.AboutUs}}", fieldConfig.get("token").toString());

        assertFalse(fieldMap.containsKey("Previous"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getTokensWithLookupNodes() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();

        var graph0 = pipelineController.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = getSyncariNode(graph0);

        MappingNodeDTO filterNode = getFilterFunctionNode(entityDef);
        graph0.getNodes().add(filterNode);

        MappingNodeDTO lookupNode1 = getLookupFunctionNode(entityDef, "lookupNode1");
        graph0.getNodes().add(lookupNode1);

        MappingNodeDTO lookupNode2 = getLookupFunctionNode(entityDef,"lookupNode2");
        graph0.getNodes().add(lookupNode2);

        //Add edges now
        graph0.getEdges().addAll(getEdges(graph0, syncariNode, lookupNode1));
        graph0.getEdges().addAll(getEdges(graph0, syncariNode, lookupNode2));
        graph0.getEdges().addAll(getEdges(graph0, lookupNode2, filterNode));
        graph0.getEdges().addAll(getEdges(graph0, lookupNode1, filterNode));

        var fieldMap = callRest(graph0, filterNode);

        List syncariList = (List)fieldMap.get("Lookup Results");
        assertTrue(syncariList.size() >= 39);
        Optional<Map> token = syncariList.stream().filter(l -> ((Map) l).get("token").equals("{{previousLookup.values.Id}}")).findFirst();
        assertTrue(token.isPresent());
        Map fieldConfig = (Map) syncariList.get(0);
        assertEquals("syncari / Account / About Us (AboutUs)", fieldConfig.get("label").toString());
        assertEquals("string", fieldConfig.get("datatype").toString());
        assertEquals("Lookup Results", fieldConfig.get("group").toString());
        assertEquals("{{previousLookup.values.AboutUs}}", fieldConfig.get("token").toString());

        assertFalse(fieldMap.containsKey("Previous"));
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getTokensWithTempVariables() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();

        var graph0 = pipelineController.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = getSyncariNode(graph0);

        MappingNodeDTO filterNode = getFilterFunctionNode(entityDef);
        graph0.getNodes().add(filterNode);

        MappingNodeDTO lookupNode1 = getLookupFunctionNode(entityDef, "lookupNode1");
        graph0.getNodes().add(lookupNode1);

        MappingNodeDTO lookupNode2 = getLookupFunctionNode(entityDef,"lookupNode2");
        graph0.getNodes().add(lookupNode2);
        
        MappingNodeDTO tempVariable = getSetValueNode(entityDef, "temp");
        graph0.getNodes().add(tempVariable);

        //Add edges now
        graph0.getEdges().addAll(getEdges(graph0, syncariNode, lookupNode1));
        graph0.getEdges().addAll(getEdges(graph0, syncariNode, lookupNode2));
        graph0.getEdges().addAll(getEdges(graph0, lookupNode1, tempVariable));
        graph0.getEdges().addAll(getEdges(graph0, lookupNode2, filterNode));
        graph0.getEdges().addAll(getEdges(graph0, tempVariable, filterNode));

        var fieldMap = callRest(graph0, filterNode);
        assertTrue(fieldMap.containsKey("Temporary Variables"));

        List tempList = (List)fieldMap.get("Temporary Variables");
        assertEquals(1, tempList.size());
        Map tempVarMap = (Map)tempList.get(0);
        assertEquals("testvar", tempVarMap.get("label").toString());
        assertEquals("Test Variable", tempVarMap.get("shortLabel").toString());
        assertEquals("{{syncari.temp.testvar}}", tempVarMap.get("token").toString());
        assertEquals("string", tempVarMap.get("datatype").toString());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getFieldPipelineTokens() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();

        var graph0 = pipelineController.getFieldPipeline(entityDef.getField("Name").get().getId());
        var syncariNode = getSyncariNode(graph0);

        MappingNodeDTO filterNode = getFilterFunctionNode(entityDef);
        graph0.getNodes().add(filterNode);

        //Add edges now
        graph0.setEdges(getEdges(graph0, syncariNode, filterNode));

        var fieldMap = callRest(graph0, filterNode);

        List syncariList = (List)fieldMap.get("Syncari");
        assertTrue(syncariList.size() >= 40);
        Map fieldConfig = (Map) syncariList.get(0);
        assertTrue("syncari / Account / About Us (AboutUs)".equalsIgnoreCase(fieldConfig.get("label").toString()));
        assertEquals("string", fieldConfig.get("datatype").toString());
        assertEquals("Syncari", fieldConfig.get("group").toString());
        assertEquals("{{record.values.AboutUs}}", fieldConfig.get("token").toString());

        List previousList = (List)fieldMap.get("Previous");
        assertEquals(1, previousList.size());
        fieldConfig = (Map) previousList.get(0);
        assertEquals("Output from previous node", fieldConfig.get("label").toString());
        assertEquals("object", fieldConfig.get("datatype").toString());
        assertEquals("Previous", fieldConfig.get("group").toString());
        assertEquals("{{previous}}", fieldConfig.get("token").toString());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getTokensBothSyncariSynapseInput() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        EntityDefinition synapseEntity = schemaService.getEntity(connector.getId(), "account");

        var graph0 = pipelineController.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = getSyncariNode(graph0);
        var entitySource = getSynapseNode(graph0);

        MappingNodeDTO filterNode = getFilterFunctionNode(entityDef);
        graph0.getNodes().add(filterNode);

        //Add syncari to filter edge
        graph0.setEdges(getEdges(graph0, syncariNode, filterNode));

        //Add synapse to filter edge
        graph0.setEdges(getEdges(graph0, entitySource, filterNode));

        var fieldMap = callRest(graph0, filterNode);

        List synapseList = (List)fieldMap.get("Syncari");
        assertTrue(synapseList.size() >= 40);
        Map fieldConfig = (Map) synapseList.get(0);
        assertTrue("syncari / Account / About Us (AboutUs)".equalsIgnoreCase(fieldConfig.get("label").toString()));
        assertEquals("string", fieldConfig.get("datatype").toString());
        assertEquals("Syncari", fieldConfig.get("group").toString());
        assertEquals("{{record.values.AboutUs}}", fieldConfig.get("token").toString());

        List syncariList = (List)fieldMap.get("Synapse");
        assertEquals(synapseEntity.getAttributes().size(), syncariList.size());
        fieldConfig = (Map) syncariList.get(4);
        assertEquals("tokens / Account / Phone (phone)", fieldConfig.get("label").toString());
        assertEquals("string", fieldConfig.get("datatype").toString());
        assertEquals("Synapse", fieldConfig.get("group").toString());
        assertEquals("{{tokens.account.phone}}", fieldConfig.get("token").toString());

        assertFalse(fieldMap.containsKey("Previous"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getTokensBothSyncariLookUpInput() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();
        EntityDef contact = syncariSchema.findEntityByName("contact").get();

        var graph0 = pipelineController.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = getSyncariNode(graph0);
        MappingNodeDTO filterNode = getFilterFunctionNode(entityDef);
        graph0.getNodes().add(filterNode);

        var lookupFunction =  functionService.findByNameAndScope("advancedLookUpSyncariRecord",Scope.ENTITY).orElseThrow();
        SimpleFunctionNodeConfig functionNodeConfig = new SimpleFunctionNodeConfig()
                .setFunctionCall(new FunctionCall().setFunctionDefinition(lookupFunction)
                        .setConfig(Map.of("syncariEntityDefId", contact.getId())).setParams(List.of()));
        MappingNodeDTO lookupNode = new MappingNodeDTO()
                .setName("advancedLookUpSyncariRecord")
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(functionNodeConfig.getConfigMap())
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());
        graph0.getNodes().add(lookupNode);

        //Add syncari to filter edge
        graph0.setEdges(getEdges(graph0, syncariNode, lookupNode));

        //Add lookup to filter edge
        graph0.setEdges(getEdges(graph0, lookupNode, filterNode));

        var fieldMap = callRest(graph0, filterNode);

        List syncariList = (List)fieldMap.get("Syncari");
        assertTrue(syncariList.size() >= 40);
        Map fieldConfig = (Map) syncariList.get(0);
        assertTrue("syncari / Account / About Us (AboutUs)".equalsIgnoreCase(fieldConfig.get("label").toString()));
        assertEquals("string", fieldConfig.get("datatype").toString());
        assertEquals("Syncari", fieldConfig.get("group").toString());
        assertEquals("{{record.values.AboutUs}}", fieldConfig.get("token").toString());

        List lookupList = (List)fieldMap.get("Lookup Results");
        fieldConfig = (Map) lookupList.stream().filter(c -> (((Map)c).get("label").toString().equalsIgnoreCase("syncari / Contact / System Modstamp (SystemModstamp)"))).findFirst().get();
        assertTrue("syncari / Contact / System Modstamp (SystemModstamp)".equalsIgnoreCase(fieldConfig.get("label").toString()));
        assertEquals("datetime", fieldConfig.get("datatype").toString());
        assertEquals("Lookup Results", fieldConfig.get("group").toString());
        assertEquals("{{previousLookup.values.SystemModstamp}}", fieldConfig.get("token").toString());

        assertFalse(fieldMap.containsKey("Previous"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void getTokensNoSyncariEntitySelectedForLookup() throws Exception {
        EntityDef entityDef = syncariSchema.findEntityByName("account").get();

        var graph0 = pipelineController.getEntityPipeline(entityDef.getId());
        assertEquals(entityDef.getDisplayName(), graph0.getName());
        var syncariNode = getSyncariNode(graph0);
        MappingNodeDTO filterNode = getFilterFunctionNode(entityDef);
        graph0.getNodes().add(filterNode);

        var lookupFunction =  functionService.findByNameAndScope("advancedLookUpSyncariRecord",Scope.ENTITY).orElseThrow();
        SimpleFunctionNodeConfig functionNodeConfig = new SimpleFunctionNodeConfig()
                .setFunctionCall(new FunctionCall().setFunctionDefinition(lookupFunction)
                        .setConfig(Map.of()).setParams(List.of()));
        MappingNodeDTO lookupNode = new MappingNodeDTO()
                .setName("advancedLookUpSyncariRecord")
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(functionNodeConfig.getConfigMap())
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());
        graph0.getNodes().add(lookupNode);

        //Add syncari to filter edge
        graph0.setEdges(getEdges(graph0, syncariNode, filterNode));

        //Add lookup to filter edge
        graph0.setEdges(getEdges(graph0, lookupNode, filterNode));

        var fieldMap = callRest(graph0, filterNode);

        List syncariList = (List)fieldMap.get("Syncari");
        assertTrue(syncariList.size() >= 40);
        Map fieldConfig = (Map) syncariList.get(0);
        assertTrue("syncari / Account / About Us (AboutUs)".equalsIgnoreCase(fieldConfig.get("label").toString()));
        assertEquals("string", fieldConfig.get("datatype").toString());
        assertEquals("Syncari", fieldConfig.get("group").toString());
        assertEquals("{{record.values.AboutUs}}", fieldConfig.get("token").toString());

        assertFalse(fieldMap.containsKey("Lookup Results"));
        assertFalse(fieldMap.containsKey("Previous"));
    }

    private Map callRest(MappingGraphDTO graph, MappingNodeDTO filterNode) throws Exception, JsonProcessingException,
            IOException, JsonParseException, JsonMappingException, UnsupportedEncodingException {
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(graph).getBytes());
        var result = mvc.perform(
                post("/api/v1/token/{currentNodeId}", filterNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(graph))
        ).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private List<EdgeDTO> getEdges(MappingGraphDTO graph, MappingNodeDTO sourceNode, MappingNodeDTO destNode) {
        List<EdgeDTO> newEdges = new ArrayList<>(graph.getEdges().stream()
                .filter(e -> !e.getSource().getNodeId().equals(sourceNode.getId())).collect(Collectors.toList()));
        EdgeDTO coreToFilter = new EdgeDTO()
                .setDestination(new NodeRef(destNode.getId(), destNode.getInputPorts().get(0), "0"))
                .setSource(new NodeRef(sourceNode.getId(), sourceNode.getOutputPorts().get(0), "0"));
        newEdges.add(coreToFilter);
        return newEdges;
    }

    private MappingNodeDTO getFilterFunctionNode(EntityDef entityDef) {
        var filterFunction =  functionService.findByNameAndScope("filter",Scope.ENTITY).orElseThrow();
        MappingNodeDTO filterNode = new MappingNodeDTO()
                .setName(entityDef.getDisplayName())
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(Map.of("definition",filterFunction.getId()))
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());
        return filterNode;
    }
    
    private MappingNodeDTO getSetValueNode(EntityDef entityDef, String name) {
        var setValueFunction =  functionService.findByNameAndScope(FunctionConstants.SET_VALUE_ON_ENTITY,Scope.ENTITY).orElseThrow();
        MappingNodeDTO setValueNode = new MappingNodeDTO()
                .setName(name)
                .setNodeType(MappingNodeType.FUNCTION)
				.setConfiguration(Map.of("definition", setValueFunction.getId(), "setValueField", Map.of("type", "temporary", "apiName",
						"testVar", "displayName", "Test Variable", "dataType", "string"), "newValue", "123"))
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());
        return setValueNode;
    }

    private MappingNodeDTO getLookupFunctionNode(EntityDef entityDef, String name) {
        var lookupFunction =  functionService.findByNameAndScope("advancedLookUpSyncariRecord",Scope.ENTITY).orElseThrow();
        MappingNodeDTO lookupFunctionNode = new MappingNodeDTO()
                .setName(name)
                .setNodeType(MappingNodeType.FUNCTION)
                .setConfiguration(Map.of("definition",lookupFunction.getId(), "syncariEntityDefId",entityDef.getId()))
                .setOutputPorts(transformer.toOutputPortDTO(List.of(OutputPort.any())))
                .setInputPorts(transformer.toInputPortDTO(List.of(InputPort.any())))
                .setId(new ObjectId().toHexString());
        return lookupFunctionNode;
    }

    private MappingNodeDTO getSyncariNode(MappingGraphDTO graph) {
        return graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.CORE_ENTITY || n.getNodeType()==MappingNodeType.CORE_ATTRIBUTE).findFirst().get();
    }

    private MappingNodeDTO getSynapseNode(MappingGraphDTO graph) {
        return graph.getNodes().stream().filter(n->n.getNodeType()==MappingNodeType.ENTITY_SOURCE || n.getNodeType()==MappingNodeType.ATTRIBUTE_SOURCE).findFirst().get();
    }
}