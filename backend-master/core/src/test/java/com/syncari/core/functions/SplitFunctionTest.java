package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.AttributeSourceNodeConfig;
import com.syncari.core.model.CoreAttributeNodeConfig;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SplitFunctionTest extends AbstractSyncariTest {

    @Autowired
    SchemaService schemaService;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    MappingGraphRepo graphRepo;

    @Autowired
    MappingNodeRepo nodeRepo;

    @Autowired
    FunctionService functionService;

    @Autowired
    EdgeRepo edgeRepo;

    @Autowired
    SplitFunction function;

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

        FunctionDefinition splitFunc = functionService.findByNameAndScope(FunctionConstants.SPLIT, Scope.ATTRIBUTE).get();
        FunctionCall call = splitFunc.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode splitNode = nodeRepo.save(new MappingNode().setName("Split Function")
                .setApiName(FunctionConstants.SPLIT).setScope(Scope.ATTRIBUTE)
                .setConfiguration(funcConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(splitNode);

        var coreAttribConfig = new CoreAttributeNodeConfig().setAttributeDefinition(syncariAttrib);
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(coreAttribConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);

        var edge1 = edgeRepo.save(new Edge().setDestinationStage(splitNode)
                .setSourceStage(sourceNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(splitNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        graph.getEdges().add(edge1);
        graph.getEdges().add(edge2);

        ValidationContext context = new ValidationContext().setGraph(graph).setCoreEntity(syncariEntity)
                .setSourceEntityMap(Map.of(entity.getId(), entity))
                .setNode(splitNode);

        // Case 1: null delimiter
        call.setConfig(Map.of());
        funcConfig.setFunctionCall(call);
        splitNode = nodeRepo.save(splitNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Delimiter from Split Function in graph attribGraph", e.getMessage());
        }

        // Case 2: empty delimiter
        call.setConfig(Map.of("delimiter", ""));
        funcConfig.setFunctionCall(call);
        splitNode = nodeRepo.save(splitNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Delimiter from Split Function in graph attribGraph", e.getMessage());
        }

        // Case 3: space delimiter
        call.setConfig(Map.of("delimiter", " "));
        funcConfig.setFunctionCall(call);
        splitNode = nodeRepo.save(splitNode.setConfiguration(funcConfig));
        function.validate(context); // valid

        // Case 4: comma delimiter
        call.setConfig(Map.of("delimiter", ","));
        funcConfig.setFunctionCall(call);
        splitNode = nodeRepo.save(splitNode.setConfiguration(funcConfig));
        function.validate(context); // valid
    }
}
