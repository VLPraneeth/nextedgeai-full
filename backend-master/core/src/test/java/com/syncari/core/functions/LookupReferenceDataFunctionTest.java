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
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.misc.DataImportStatus;
import com.syncari.core.model.misc.ReferenceDataSource;
import com.syncari.core.model.misc.ReferenceDataSourceType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EdgeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.ReferenceDataService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.validation.ValidationContext;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class LookupReferenceDataFunctionTest extends AbstractSyncariTest {

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
    @Qualifier(FunctionConstants.LOOKUP_REF_DATA)
    LookupReferenceDataFunction function;

    @After
    public void tearDown(){
        resetRepos(nodeRepo, edgeRepo, graphRepo, entityProxyRepo, attributeProxyRepo);
    }

    @Test
    public void validate(){
        // source -> function -> core
        ReferenceDataService mockRefDataService = mock(ReferenceDataService.class);
        ReferenceDataMeta refDataMeta = new ReferenceDataMeta().setName("dataset1").setStatus(DataImportStatus.ACTIVE)
                .setFields(Map.of("name", new StringType(), "value", new StringType())).setTotalRecords(100l)
                .setDatasetCollectionName("Dataset1")
                .setSource(new ReferenceDataSource(ReferenceDataSourceType.s3, "/path/to/dataset"));
        refDataMeta.setId("dataset1");
        doReturn(Optional.of(refDataMeta)).when(mockRefDataService).findReferenceData("dataset1");
        doReturn(Optional.empty()).when(mockRefDataService).findReferenceData("dataset123");
        function.referenceDataService = mockRefDataService;
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

        FunctionDefinition lookupRefFunc = functionService.findByNameAndScope(FunctionConstants.LOOKUP_REF_DATA, Scope.ATTRIBUTE).get();
        FunctionCall call = lookupRefFunc.withParams();
        var funcConfig = new SimpleFunctionNodeConfig().setFunctionCall(call);
        MappingNode lookupRefFuncNode = nodeRepo.save(new MappingNode().setName("LookupRef Function")
                .setApiName(FunctionConstants.LOOKUP_REF_DATA).setScope(Scope.ATTRIBUTE)
                .setConfiguration(funcConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(lookupRefFuncNode);

        var coreAttribConfig = new CoreAttributeNodeConfig().setAttributeDefinition(syncariAttrib);
        MappingNode coreNode = nodeRepo.save(new MappingNode().setName("coreNode").setApiName("account")
                .setScope(Scope.ATTRIBUTE).setConfiguration(coreAttribConfig).setMappingGraphId(graph.getId()));
        graph.getNodes().add(coreNode);

        var edge1 = edgeRepo.save(new Edge().setDestinationStage(lookupRefFuncNode)
                .setSourceStage(sourceNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
        var edge2 = edgeRepo.save(new Edge().setDestinationStage(coreNode)
                .setSourceStage(lookupRefFuncNode).setOutput(OutputPort.any()).setInput(InputPort.any()));
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

        // case 2: validate missing datasetId in config
        context.setNode(lookupRefFuncNode);
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Dataset from LookupRef Function in graph attribGraph", e.getMessage());
        }

        // Case 3: validate missing lookupKey in config
        call.setConfig(Map.of("datasetId", "dataset123"));
        funcConfig.setFunctionCall(call);
        lookupRefFuncNode = nodeRepo.save(lookupRefFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Lookup Key from LookupRef Function in graph attribGraph", e.getMessage());
        }

        // Case 4: validate missing destinationFieldName in config
        call.setConfig(Map.of("datasetId", "dataset123", "lookUpKey", "key"));
        funcConfig.setFunctionCall(call);
        lookupRefFuncNode = nodeRepo.save(lookupRefFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Destination Field from LookupRef Function in graph attribGraph", e.getMessage());
        }

        // case 5: validate attributeDefId in config
        call.setConfig(Map.of("datasetId", "dataset123", "lookUpKey", "key", "destinationFieldName", "field", "operator", "exactMatch"));
        funcConfig.setFunctionCall(call);
        lookupRefFuncNode = nodeRepo.save(lookupRefFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Dataset 'dataset123' in node LookupRef Function of graph attribGraph", e.getMessage());
        }

        // case 6: validate lookupKey in config
        call.setConfig(Map.of("datasetId", "dataset1", "lookUpKey", "key", "destinationFieldName", "field", "operator", "exactMatch"));
        funcConfig.setFunctionCall(call);
        lookupRefFuncNode = nodeRepo.save(lookupRefFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Lookup Key 'key' in node LookupRef Function of graph attribGraph", e.getMessage());
        }

        // case 7: validate destinationFieldName in config
        call.setConfig(Map.of("datasetId", "dataset1", "lookUpKey", "name", "destinationFieldName", "column", "operator", "exactMatch"));
        funcConfig.setFunctionCall(call);
        lookupRefFuncNode = nodeRepo.save(lookupRefFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Destination Field 'column' in node LookupRef Function of graph attribGraph", e.getMessage());
        }

        // case 7: validate destinationFieldName in config
        call.setConfig(Map.of("datasetId", "dataset1", "lookUpKey", "name", "destinationFieldName", "value", "operator", "exactMatch"));
        funcConfig.setFunctionCall(call);
        lookupRefFuncNode = nodeRepo.save(lookupRefFuncNode.setConfiguration(funcConfig));
        function.validate(context);

        // case 8: validate lookupKey is null and valid datasetId
        call.setConfig(Map.of("datasetId", "dataset1", "destinationFieldName", "value",  "operator", "exactMatch"));
        funcConfig.setFunctionCall(call);
        lookupRefFuncNode = nodeRepo.save(lookupRefFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Lookup Key from LookupRef Function in graph attribGraph", e.getMessage());
        }

        // case 9: validate destinationFieldName is null and valid datasetId
        call.setConfig(Map.of("datasetId", "dataset1", "lookUpKey", "name", "operator", "exactMatch"));
        funcConfig.setFunctionCall(call);
        lookupRefFuncNode = nodeRepo.save(lookupRefFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Destination Field from LookupRef Function in graph attribGraph", e.getMessage());
        }

        // case 10: validate operator
        call.setConfig(Map.of("datasetId", "dataset1", "lookUpKey", "name","destinationFieldName", "value", "operator", "xyz"));
        funcConfig.setFunctionCall(call);
        lookupRefFuncNode = nodeRepo.save(lookupRefFuncNode.setConfiguration(funcConfig));
        try{
            function.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid null 'xyz' in node LookupRef Function of graph attribGraph", e.getMessage());
        }
    }
}
