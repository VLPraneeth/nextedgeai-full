package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.NodeInfoContext;
import com.syncari.core.repositories.customer.*;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.validation.ValidationContext;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SetValueFunctionTest extends AbstractSyncariTest {

    private static final String USE_EMPTY = "Do not convert empty string to null";
    private static final String NEW_VALUE = "newValue";

    @Autowired
    SetValueFunction function;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    MappingGraphRepo graphRepo;

    @Autowired
    SchemaService schemaService;

    @Autowired
    MappingNodeRepo nodeRepo;

    @Autowired
    EdgeRepo edgeRepo;

    @Autowired
    FunctionService functionService;

    @Autowired
    ConnectorService connectorService;

    @After
    public void tearDown(){
        resetRepos(nodeRepo, edgeRepo, graphRepo, entityProxyRepo, attributeProxyRepo);
    }

    @Test
    public void validate(){

        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        AttributeDefinition syncariAttrib = syncariEntity.getAttributes().get(0);
        EntityDefinition externalEntity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new StringType()).setEntityId(externalEntity.getId())
                .setStatus(Status.ACTIVE));
        externalEntity.setAttributes(List.of(attribute));

        MappingGraph graph = createGraph(syncariEntity, externalEntity);
        MappingNode node = graph.getNodes().stream().filter(n -> FunctionConstants.SET_VALUE.equals(n.getApiName())).findFirst().get();
        SimpleFunctionNodeConfig funcConfig = node.getTypedConfiguration();
        FunctionCall call = funcConfig.getFunctionCall();
        call.setConfig(Map.of(NEW_VALUE, "true", USE_EMPTY, true));
        ValidationContext context = new ValidationContext().setGraph(graph).setNode(node)
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(syncariEntity)
                .setSourceEntityMap(Map.of(externalEntity.getId(), externalEntity));

        try {
	        function.validate(context);
	        fail();
	    } catch (SyncariValidationException e){
	        assertEquals("Invalid Data Type '' in node Set Value Function of graph attribGraph", e.getMessage());
        }

        // datatype = integer, newValue = "string_value"
        call.setConfig(Map.of("setValueField",Map.of("dataType", "integer"),
                NEW_VALUE, "string_value", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Data Type 'integer' in node Set Value Function of graph attribGraph", e.getMessage());
        }

        try{
            // newValue has invalid token
            call.setConfig(Map.of("dataType", "string",
                    NEW_VALUE, "{{current.name", USE_EMPTY, true));
            funcConfig.setFunctionCall(call);
            node = nodeRepo.save(node.setConfiguration(funcConfig));
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("The token syntax is incorrect in node 'Set Value Function' of graph 'attribGraph'", e.getMessage());
        }


        // datatype = string, newValue = "string_value" - valid
        call.setConfig(Map.of("dataType", "string",
                NEW_VALUE, "string_value", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        function.validate(context);

        // datatype = integer, newValue = "10" - valid
        call.setConfig(Map.of("dataType", "integer",
                NEW_VALUE, "10", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        function.validate(context);

        // newValue has token - datatype check skipped and will be done at runtime
        call.setConfig(Map.of("dataType", "string",
                NEW_VALUE, "{{current.name}}", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        function.validate(context);

        // datatype = integer, newValue = 10 - valid
        call.setConfig(Map.of("dataType", "integer",
                NEW_VALUE, 10, USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        function.validate(context);

        // datatype = boolean, newValue = true - valid
        call.setConfig(Map.of("dataType", "boolean",
                NEW_VALUE, true, USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        function.validate(context);

        // datatype = boolean, newValue = "true" - valid
        call.setConfig(Map.of("dataType", "boolean",
                NEW_VALUE, "true", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        function.validate(context);

        //temporary variable without api name
        try {
	        call.setConfig(Map.of("setValueField", Map.of("dataType", "boolean", "type", "temporary"), NEW_VALUE, "true", USE_EMPTY, true));
	        funcConfig.setFunctionCall(call);
	        node = nodeRepo.save(node.setConfiguration(funcConfig));
	        function.validate(context);
	        fail();
        }catch (SyncariValidationException e) {
        	assertEquals("Invalid apiName '' in node Set Value Function of graph attribGraph", e.getMessage());
		}

        //temporary variable with api name
        call.setConfig(Map.of("setValueField", Map.of("dataType", "boolean", "type", "temporary", "apiName", "test", "displayName", "test"), NEW_VALUE, "true", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        function.validate(context);

      //temporary variable with formula
        call.setConfig(Map.of("setValueField", Map.of("dataType", "string", "type", "temporary", "apiName", "test2", "displayName", "test"), NEW_VALUE, "FORMULA({{var1}} + {{var2}})", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        function.validate(context);

        //set value with setValueField null (one edge case)
        var config = new HashMap<String, Object>();
        config.put("setValueField", null);
        config.put(NEW_VALUE, "true");
        config.put(USE_EMPTY, true);
        call.setConfig(config);
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        try {
        	function.validate(context);
        }catch (SyncariValidationException e) {
        	assertEquals("Invalid Data Type '' in node Set Value Function of graph attribGraph", e.getMessage());
		}

    }

    @Test
    public void inferNodeOutputDatatype(){
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        AttributeDefinition syncariAttrib = syncariEntity.getAttributes().get(0);
        EntityDefinition externalEntity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new StringType()).setEntityId(externalEntity.getId())
                .setStatus(Status.ACTIVE));
        externalEntity.setAttributes(List.of(attribute));

        MappingGraph graph = createGraph(syncariEntity, externalEntity);
        MappingNode node = graph.getNodes().stream().filter(n -> FunctionConstants.SET_VALUE.equals(n.getApiName())).findFirst().get();
        SimpleFunctionNodeConfig funcConfig = node.getTypedConfiguration();
        FunctionCall call = funcConfig.getFunctionCall();

        call.setConfig(Map.of("dataType", "integer",
                NEW_VALUE, 10, USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        NodeInfoContext context = new NodeInfoContext().setCurrentNode(node).setPipeline(graph);
        assertEquals("integer", function.inferNodeOutputDatatype(context));

        call.setConfig(Map.of("dataType", "double",
                NEW_VALUE, 10.0, USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        context = new NodeInfoContext().setCurrentNode(node).setPipeline(graph);
        assertEquals("double", function.inferNodeOutputDatatype(context));

        call.setConfig(Map.of("dataType", "",
                NEW_VALUE, 10.0, USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        node = nodeRepo.save(node.setConfiguration(funcConfig));
        context = new NodeInfoContext().setCurrentNode(node).setPipeline(graph);
        assertEquals("string", function.inferNodeOutputDatatype(context));
    }

    private MappingGraph createGraph(EntityDefinition syncariEntity, EntityDefinition externalEntity){
        AttributeDefinition syncariAttrib = syncariEntity.getAttributes().get(0);
        AttributeDefinition externalAttrib = externalEntity.getAttributes().get(0);
        MappingGraph graph = graphRepo.save(new MappingGraph().setTargetId(syncariEntity.getId())
                .setScope(Scope.ATTRIBUTE).setName("attribGraph"));
        var srcAttribConfig = new AttributeSourceNodeConfig().setAttributeDefinition(externalAttrib);
        MappingNode sourceNode = nodeRepo.save(new MappingNode().setName("sourceNode").setApiName("Account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(srcAttribConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(sourceNode);

        FunctionDefinition enrichPersonFunc = functionService.findByNameAndScope(FunctionConstants.SET_VALUE, Scope.ATTRIBUTE).get();
        FunctionCall call = enrichPersonFunc.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode setValueFuncNode = nodeRepo.save(new MappingNode().setName("Set Value Function")
                .setApiName(FunctionConstants.SET_VALUE).setScope(Scope.ATTRIBUTE)
                .setConfiguration(funcConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(setValueFuncNode);

        var coreAttribConfig = new CoreAttributeNodeConfig().setAttributeDefinition(syncariAttrib);
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(coreAttribConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);

        var edge1 = edgeRepo.save(new Edge().setDestinationStage(setValueFuncNode)
                .setSourceStage(sourceNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(setValueFuncNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        graph.getEdges().add(edge1);
        graph.getEdges().add(edge2);

        return graph;
    }
}
