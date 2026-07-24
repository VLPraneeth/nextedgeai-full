package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.*;
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

public class SetValueOnEntityFunctionTest extends AbstractSyncariTest {

    private static final String USE_EMPTY = "Do not convert empty string to null";
    private static final String NEW_VALUE = "newValue";

    @Autowired
    SetValueOnEntityFunction function;

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

    @After
    public void tearDown(){
        resetRepos(nodeRepo, edgeRepo, graphRepo, entityProxyRepo, attributeProxyRepo);
    }

    @Test
    public void validate_ConnectedToSource(){
        // source -> function -> core
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        EntityDefinition entity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new StringType()).setEntityId(entity.getId())
                .setStatus(Status.ACTIVE));
        entity.setAttributes(List.of(attribute));
        MappingGraph graph = graphRepo.save(new MappingGraph().setTargetId(syncariEntity.getId())
                .setScope(Scope.ENTITY).setName(syncariEntity.getDisplayName()));
        var srcEntityConfig = new EntitySourceNodeConfig().setEntityDefinition(entity);
        MappingNode sourceNode = nodeRepo.save(new MappingNode().setName("sourceNode").setApiName("Account")
                .setScope(Scope.ENTITY).setConfiguration(srcEntityConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(sourceNode);

        FunctionDefinition mask = functionService.findByNameAndScope(FunctionConstants.SET_VALUE_ON_ENTITY, Scope.ENTITY).get();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode setValueFuncNode = nodeRepo.save(new MappingNode().setName("Set Value Function")
                .setApiName(FunctionConstants.SET_VALUE_ON_ENTITY).setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(setValueFuncNode);

        var coreEntityConfig = new CoreEntityNodeConfig().setEntityDefinition(syncariEntity);
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ENTITY).setConfiguration(coreEntityConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);

        var edge1 = edgeRepo.save(new Edge().setDestinationStage(setValueFuncNode)
                .setSourceStage(sourceNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(setValueFuncNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        graph.getEdges().add(edge1);
        graph.getEdges().add(edge2);

        // case 1: validation context missing node
        ValidationContext context = new ValidationContext().setGraph(graph).setCoreEntity(syncariEntity)
                .setSourceEntityMap(Map.of(entity.getId(), entity));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing node in validation", e.getMessage());
        }

        // case 2: validate missing attributeDefId in config
        context.setNode(setValueFuncNode);
//        try{
//            function.validate(context);
//            fail();
//        } catch (SyncariValidationException e){
//            assertEquals("Missing Field Name from Set Value Function in graph Account", e.getMessage());
//        }

        // case 3: validate attributeDefId in config
        call.setConfig(Map.of("attributeDefinitionId", "attribute123", NEW_VALUE, "Value", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        setValueFuncNode = nodeRepo.save(setValueFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Field Name 'attribute123' in node Set Value Function of graph Account", e.getMessage());
        }

        // case 4: valid config
        call.setConfig(Map.of("attributeDefinitionId", attribute.getId(), NEW_VALUE, "Value", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        setValueFuncNode = nodeRepo.save(setValueFuncNode.setConfiguration(funcConfig));
        function.validate(context);

        // case 5: valid config with null value and useEmpty as true
        Map<String, Object> configWithNullValue = new HashMap<>();
        configWithNullValue.put("attributeDefinitionId", attribute.getId());
        configWithNullValue.put(NEW_VALUE, null);
        configWithNullValue.put(USE_EMPTY, true);
        call.setConfig(configWithNullValue);
        funcConfig.setFunctionCall(call);
        setValueFuncNode = nodeRepo.save(setValueFuncNode.setConfiguration(funcConfig));
        function.validate(context);

        
      //temporary variable without api name
        try {
	        call.setConfig(Map.of("setValueField", Map.of("dataType", "boolean", "type", "temporary"), NEW_VALUE, "true", USE_EMPTY, true));
	        funcConfig.setFunctionCall(call);
	        setValueFuncNode = nodeRepo.save(setValueFuncNode.setConfiguration(funcConfig));
	        function.validate(context);
	        fail();
        }catch (SyncariValidationException e) {
        	assertEquals("Invalid apiName '' in node Set Value Function of graph Account", e.getMessage());
		}

        //temporary variable with api name
        call.setConfig(Map.of("setValueField", Map.of("dataType", "boolean", "type", "temporary", "apiName", "test", "displayName", "test"), NEW_VALUE, "true", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        setValueFuncNode = nodeRepo.save(setValueFuncNode.setConfiguration(funcConfig));
        function.validate(context);
        
      //temporary variable with formula
        call.setConfig(Map.of("setValueField", Map.of("dataType", "string", "type", "temporary", "apiName", "test2", "displayName", "test"), NEW_VALUE, "FORMULA({{var1}} + {{var2}})",USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        setValueFuncNode = nodeRepo.save(setValueFuncNode.setConfiguration(funcConfig));
        function.validate(context);
    }

    @Test
    public void validate_ConnectedToCore(){
        // source -> core -> function
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        EntityDefinition entity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new StringType()).setEntityId(entity.getId())
                .setStatus(Status.ACTIVE));
        entity.setAttributes(List.of(attribute));
        MappingGraph graph = graphRepo.save(new MappingGraph().setTargetId(syncariEntity.getId())
                .setScope(Scope.ENTITY).setName(syncariEntity.getDisplayName()));
        var srcEntityConfig = new EntitySourceNodeConfig().setEntityDefinition(entity);
        MappingNode sourceNode = nodeRepo.save(new MappingNode().setName("sourceNode").setApiName("Account")
                .setScope(Scope.ENTITY).setConfiguration(srcEntityConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(sourceNode);

        var sinkEntityConfig = new EntitySinkNodeConfig().setEntityDefinition(entity);
        MappingNode sinkNode = nodeRepo.save(new MappingNode().setName("sinkNode").setApiName("Account")
                .setScope(Scope.ENTITY).setConfiguration(sinkEntityConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(sinkNode);

        FunctionDefinition mask = functionService.findByNameAndScope(FunctionConstants.SET_VALUE_ON_ENTITY, Scope.ENTITY).get();
        FunctionCall call = mask.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode setValueFuncNode = nodeRepo.save(new MappingNode().setName("Set Value Function")
                .setApiName(FunctionConstants.SET_VALUE_ON_ENTITY).setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(setValueFuncNode);

        var coreEntityConfig = new CoreEntityNodeConfig().setEntityDefinition(syncariEntity);
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ENTITY).setConfiguration(coreEntityConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);

        var edge1 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(sourceNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(setValueFuncNode)
                .setSourceStage(coreNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge3 = edgeRepo.save(new Edge().setDestinationStage(sinkNode)
                .setSourceStage(setValueFuncNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        graph.getEdges().add(edge1);
        graph.getEdges().add(edge2);
        graph.getEdges().add(edge3);

        ValidationContext context = new ValidationContext().setGraph(graph).setNode(setValueFuncNode)
                .setCoreEntity(syncariEntity).setSourceEntityMap(Map.of(entity.getId(), entity));

        // case 1: validate missing attributeDefId in config
        context.setNode(setValueFuncNode);
//        try{
//            function.validate(context);
//            fail();
//        } catch (SyncariValidationException e){
//            assertEquals("Missing Field Name from Set Value Function in graph Account", e.getMessage());
//        }

        // case 2: validate attributeDefId in config
        call.setConfig(Map.of("attributeDefinitionId", "attribute123", NEW_VALUE, "Value", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        setValueFuncNode = nodeRepo.save(setValueFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Field Name 'attribute123' in node Set Value Function of graph Account", e.getMessage());
        }

        // case 3: valid config
        call.setConfig(Map.of("attributeDefinitionId", syncariEntity.getAttributes().get(0).getId(), NEW_VALUE, "Value", USE_EMPTY, true));
        funcConfig.setFunctionCall(call);
        setValueFuncNode = nodeRepo.save(setValueFuncNode.setConfiguration(funcConfig));
        function.validate(context);

        // case 4: valid config with empty value
        Map<String, Object> configWithNullValue = new HashMap<>();
        configWithNullValue.put("attributeDefinitionId", syncariEntity.getAttributes().get(0).getId());
        configWithNullValue.put(NEW_VALUE, "");
        configWithNullValue.put(USE_EMPTY, true);
        call.setConfig(configWithNullValue);
        funcConfig.setFunctionCall(call);
        setValueFuncNode = nodeRepo.save(setValueFuncNode.setConfiguration(funcConfig));
        function.validate(context);
    }

    @Test
    public void validate_InconvertibleDataType(){
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        EntityDefinition entity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new IntegerType()).setEntityId(entity.getId())
                .setStatus(Status.ACTIVE));
        entity.setAttributes(List.of(attribute));
        MappingGraph graph = graphRepo.save(new MappingGraph().setTargetId(syncariEntity.getId())
                .setScope(Scope.ENTITY).setName(syncariEntity.getDisplayName()));
        var srcEntityConfig = new EntitySourceNodeConfig().setEntityDefinition(entity);
        MappingNode sourceNode = nodeRepo.save(new MappingNode().setName("sourceNode").setApiName("Account")
                .setScope(Scope.ENTITY).setConfiguration(srcEntityConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(sourceNode);

        FunctionDefinition mask = functionService.findByNameAndScope(FunctionConstants.SET_VALUE_ON_ENTITY, Scope.ENTITY).get();
        FunctionCall call = mask.withParams();
        call.setConfig(Map.of("attributeDefinitionId", attribute.getId(), NEW_VALUE, "Value", USE_EMPTY, true));
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode setValueFuncNode = nodeRepo.save(new MappingNode().setName("Set Value Function")
                .setApiName(FunctionConstants.SET_VALUE_ON_ENTITY).setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(setValueFuncNode);

        var coreEntityConfig = new CoreEntityNodeConfig().setEntityDefinition(syncariEntity);
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ENTITY).setConfiguration(coreEntityConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);

        var edge1 = edgeRepo.save(new Edge().setDestinationStage(setValueFuncNode)
                .setSourceStage(sourceNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(setValueFuncNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        graph.getEdges().add(edge1);
        graph.getEdges().add(edge2);

        ValidationContext context = new ValidationContext().setGraph(graph).setNode(setValueFuncNode)
                .setCoreEntity(syncariEntity).setSourceEntityMap(Map.of(entity.getId(), entity));
        context.setNode(setValueFuncNode);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("The value 'Value' in node 'Set Value Function' of pipeline 'Account' must be of type 'integer' or a token", e.getMessage());
        }
        //validate skips datatype validation for token types
        call.setConfig(Map.of("attributeDefinitionId", attribute.getId(), NEW_VALUE, "{{custom.token}}", USE_EMPTY, true));
        function.validate(context);
        //no failure
        //validate skips datatype validation for empty valuess
        call.setConfig(Map.of("attributeDefinitionId", attribute.getId(), NEW_VALUE, "", USE_EMPTY, true));
        function.validate(context);
    }
}
