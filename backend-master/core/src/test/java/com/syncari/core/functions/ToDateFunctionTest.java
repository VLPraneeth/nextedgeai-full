package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
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

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ToDateFunctionTest extends AbstractSyncariTest {

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
    ToDateFunction function;

    @After
    public void tearDown(){
        resetRepos(nodeRepo, edgeRepo, graphRepo, entityProxyRepo, attributeProxyRepo);
    }

    @Test
    public void validate(){
        // source -> function -> core
        EntityDefinition syncariEntity = schemaService.getSyncariEntityByName("account").get();
        AttributeDefinition syncariAttrib = syncariEntity.getAttributes().get(0);
        EntityDefinition entity = entityProxyRepo.save(new EntityDefinition("account", "Account"));
        AttributeDefinition attribute = attributeProxyRepo.save(new AttributeDefinition().setApiName("attribute1")
                .setDisplayName("Attribute1").setDataType(new StringType()).setEntityId(entity.getId())
                .setStatus(Status.ACTIVE));
        entity.setAttributes(List.of(attribute));
        MappingGraph graph = graphRepo.save(new MappingGraph().setTargetId(syncariEntity.getId())
                .setScope(Scope.ATTRIBUTE).setName("attribGraph"));
        var srcAttribConfig = new AttributeSourceNodeConfig().setAttributeDefinition(attribute);
        MappingNode sourceNode = nodeRepo.save(new MappingNode().setName("sourceNode").setApiName("Account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(srcAttribConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(sourceNode);

        FunctionDefinition lookupRefFunc = functionService.findByNameAndScope(FunctionConstants.DATE_PARSE, Scope.ATTRIBUTE).get();
        FunctionCall call = lookupRefFunc.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode formatNode = nodeRepo.save(new MappingNode().setName("ToDate Function")
                .setApiName(FunctionConstants.DATE_PARSE).setScope(Scope.ATTRIBUTE)
                .setConfiguration(funcConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(formatNode);

        var coreAttribConfig = new CoreAttributeNodeConfig().setAttributeDefinition(syncariAttrib);
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(coreAttribConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);

        var edge1 = edgeRepo.save(new Edge().setDestinationStage(formatNode)
                .setSourceStage(sourceNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(formatNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
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

        // case 2: validate empty format in config
        context.setNode(formatNode);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Date Format from ToDate Function in graph attribGraph", e.getMessage());
        }

        // Case 3: Epoch in seconds
        call.setConfig(Map.of("format", "Epoch Timestamp in Seconds"));
        funcConfig.setFunctionCall(call);
        formatNode = nodeRepo.save(formatNode.setConfiguration(funcConfig));
        function.validate(context);

        // Case 4: Epoch in milliseconds
        call.setConfig(Map.of("format", "Epoch Timestamp in Milliseconds"));
        funcConfig.setFunctionCall(call);
        formatNode = nodeRepo.save(formatNode.setConfiguration(funcConfig));
        function.validate(context);

        // Case 5: valid date format
        call.setConfig(Map.of("format", "dd_MM_yyyy hh_MM_ss"));
        funcConfig.setFunctionCall(call);
        formatNode = nodeRepo.save(formatNode.setConfiguration(funcConfig));
        function.validate(context);

        // Case 6: invalid date format
        call.setConfig(Map.of("format", "ddd mm yyy hh:MM:ss"));
        funcConfig.setFunctionCall(call);
        formatNode = nodeRepo.save(formatNode.setConfiguration(funcConfig));

        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid date format 'ddd mm yyy hh:MM:ss' in node ToDate Function of graph attribGraph", e.getMessage());
        }
    }
}
