package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.Edge;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.EntitySourceNodeConfig;
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
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.validation.ValidationContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AttachRecordFunctionTest extends AbstractSyncariTest {

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
    AttachRecordFunction function;

    @Autowired
    ConnectorService connectorService;

    @Test
    public void validate(){

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

        FunctionDefinition attachRecord = functionService.findByNameAndScope(FunctionConstants.ATTACH_RECORD, Scope.ENTITY).get();
        FunctionCall call = attachRecord.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode attachRecordFuncNode = nodeRepo.save(new MappingNode().setName("Attach Record Function")
                .setApiName(FunctionConstants.ATTACH_RECORD).setScope(Scope.ENTITY)
                .setConfiguration(funcConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(attachRecordFuncNode);

        var coreEntityConfig = new CoreEntityNodeConfig().setEntityDefinition(syncariEntity);
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ENTITY).setConfiguration(coreEntityConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);

        var edge1 = edgeRepo.save(new Edge().setDestinationStage(attachRecordFuncNode)
                .setSourceStage(sourceNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(attachRecordFuncNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        graph.getEdges().add(edge1);
        graph.getEdges().add(edge2);

        // case 1: validate required fields
        ValidationContext context = new ValidationContext().setGraph(graph).setNode(attachRecordFuncNode)
                .setSyncariConnector(connectorService.getSyncariConnector()).setCoreEntity(syncariEntity)
                .setSourceEntityMap(Map.of(entity.getId(), entity));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Link Record of Type from Attach Record Function in graph Account", e.getMessage());
        }

        // case 2: validate externalEntityId
        call.setConfig(Map.of("externalEntityDefId", "entity123",
                "syncariEntityDefId", "syncariEntity123",
                "inputFieldId", "inputField123",
                "searchFieldId", "searchField123"));
        funcConfig.setFunctionCall(call);
        attachRecordFuncNode = nodeRepo.save(attachRecordFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Link Record of Type 'entity123' in node Attach Record Function of graph Account", e.getMessage());
        }

        // case 3: valid externalEntityDefId - validate syncariEntityId
        call.setConfig(Map.of("externalEntityDefId", entity.getId(),
                "syncariEntityDefId", "syncariEntity123",
                "inputFieldId", "inputField123",
                "searchFieldId", "searchField123"));
        funcConfig.setFunctionCall(call);
        attachRecordFuncNode = nodeRepo.save(attachRecordFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Syncari Entity 'syncariEntity123' in node Attach Record Function of graph Account", e.getMessage());
        }

        // case 4: valid syncariEntityId - validate searchFieldId
        call.setConfig(Map.of("externalEntityDefId", entity.getId(),
                "syncariEntityDefId", syncariEntity.getId(),
                "inputFieldId", "inputField123",
                "searchFieldId", "searchField123"));
        funcConfig.setFunctionCall(call);
        attachRecordFuncNode = nodeRepo.save(attachRecordFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid By Matching On 'searchField123' in node Attach Record Function of graph Account", e.getMessage());
        }

        // case 5: valid searchFieldId - validate inputFieldId
        call.setConfig(Map.of("externalEntityDefId", entity.getId(),
                "syncariEntityDefId", syncariEntity.getId(),
                "inputFieldId", "inputField123",
                "searchFieldId", syncariEntity.getAttributes().get(0).getId()));
        funcConfig.setFunctionCall(call);
        attachRecordFuncNode = nodeRepo.save(attachRecordFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid With Input Field 'inputField123' in node Attach Record Function of graph Account", e.getMessage());
        }

        // case 6: all valid
        call.setConfig(Map.of("externalEntityDefId", entity.getId(),
                "syncariEntityDefId", syncariEntity.getId(),
                "inputFieldId", entity.getAttributes().get(0).getId(),
                "searchFieldId", syncariEntity.getAttributes().get(0).getId()));
        funcConfig.setFunctionCall(call);
        attachRecordFuncNode = nodeRepo.save(attachRecordFuncNode.setConfiguration(funcConfig));
    }
}
