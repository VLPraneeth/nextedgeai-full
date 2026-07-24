package com.syncari.viper.streams.stages;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.model.util.Scope;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.BatchedOperations;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.sync.CurrentBatch;
import com.syncari.core.sync.RecordsBySyncariId;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.utils.Pair;

import java.util.*;

import static com.syncari.core.utils.GraphHelper.*;
import static com.syncari.viper.streams.stages.PipelineHelper.toApiName;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class FieldPipelineTestHelper {
    FunctionService functionService;
    SchemaService schemaService;
    EntityRepo entityRepo;
    ConnectorService connectorService;
    ExecuteFieldPipeline executeFieldPipeline;

    public FieldPipelineTestHelper(FunctionService functionService, SchemaService schemaService, EntityRepo entityRepo, ConnectorService connectorService, ExecuteFieldPipeline executeFieldPipeline) {
        this.functionService = functionService;
        this.schemaService = schemaService;
        this.entityRepo = entityRepo;
        this.connectorService = connectorService;
        this.executeFieldPipeline = executeFieldPipeline;
    }

    public FunctionDefinition func(String name, Scope scope) {
        return functionService.findByNameAndScope(name, scope).get();
    }

    public EntityDefinition getEntityDef(String name, Connector connector, List<Pair> fields) {
        connector = connector == null
                ? createConnector("my zendesk connector", "my zendesk connector", "zendeskConnectorId")
                : connector;
        EntityDefinition entityDef = SchemaHelper.createEntityDef(name, name, connector);
        for (Pair<String, DateType> pair : fields) {
            AttributeDefinition attr = SchemaHelper.createAttribute(pair.x, pair.y, entityDef.getId());
            entityDef.addField(attr);
        }
        return entityDef;
    }

    public Change executeFunction(EntityDefinition coreEntityDef, EntityDefinition sourceEntityDef, String sourceField, String coreField, String functionName, 
            Map<String, Object> functionParams, EntityData entityData, boolean createDuplicateEdges) {
        return executeFunction(coreEntityDef, sourceEntityDef, sourceField, coreField, functionName, functionParams, entityData, createDuplicateEdges, true);
    }

    public Change executeFunction(EntityDefinition coreEntityDef, EntityDefinition sourceEntityDef, String sourceField, String coreField, String functionName,
                                  Map<String, Object> functionParams, EntityData entityData, boolean createDuplicateEdges, boolean rejectEmpty) {

        return executeFunction(coreEntityDef, sourceEntityDef, sourceField, coreField, functionName, functionParams, entityData, createDuplicateEdges, rejectEmpty, null);
    }


        public Change executeFunction(EntityDefinition coreEntityDef, EntityDefinition sourceEntityDef, String sourceField, String coreField, String functionName,
                                  Map<String, Object> functionParams, EntityData entityData, boolean createDuplicateEdges, boolean rejectEmpty, GraphContext context) {
        Connector connector = createConnector("my zendesk connector", "my zendesk connector", "zendeskConnectorId");
        EntityDefinition srcEntityDef = sourceEntityDef == null ? getEntityDef("Organization", connector, List.of(Pair.of(coreField, new IntegerType()), Pair.of(sourceField, new DatetimeType())))
                : sourceEntityDef;

        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode = srcEntityNode(srcEntityDef, entityGraph);
        edge(srcNode, coreNode, entityGraph);

        MappingGraph coreAttrGraph = createGraph(coreEntityDef.getFieldByName(coreField).getId(), Scope.ATTRIBUTE);
        var srcAttr = srcEntityDef.getFieldByName(sourceField);

        MappingNode srcNowAttrNode = srcAttributeNode(srcAttr, coreAttrGraph);

        MappingNode functionNode = createFunctionNode(srcNowAttrNode, func(functionName, Scope.ATTRIBUTE), Scope.ATTRIBUTE, functionParams, srcEntityDef.getFieldByName(sourceField).getDataType());
        coreAttrGraph.getNodes().add(functionNode);
        edge(srcNowAttrNode, functionNode, coreAttrGraph);
        if (createDuplicateEdges) {
            edge(srcNowAttrNode, functionNode, coreAttrGraph);
        }

        MappingNode coreDayAttrNode = coreAttributeNode(coreEntityDef.getFieldByName(coreField), coreAttrGraph);
        CoreAttributeNodeConfig config = coreDayAttrNode.getTypedConfiguration();
        config.setRejectEmptyString(rejectEmpty);
        config.setRejectEmptyValue(rejectEmpty);

        edge(functionNode, coreDayAttrNode, coreAttrGraph);
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(connectorService.refreshAuthentication(any(Connector.class))).thenReturn(connector);
        when(entityRepo.findById(coreEntityDef, entityData.getSyncariEntityId())).thenReturn(Optional.of(entityData));
        when(schemaService.getAttribute(srcAttr.getId())).thenReturn(srcAttr);

        GraphContext currentContext = context != null ? context : new GraphContext();
            currentContext.setCurrentBatch(createCurrentBatch());
            currentContext.setGraph(entityGraph);
        srcEntityDef.getActiveAttributes().forEach(attributeDefinition -> {
            currentContext.set("field_" + attributeDefinition.getId(), entityData.getValue(attributeDefinition.getApiName()));
        });
        currentContext.set("previous",entityData);
        currentContext.set(toApiName(connector.getName()),Map.of(toApiName(srcEntityDef.getApiName()),entityData.getValues()));

        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(entityData.getSyncariEntityId());
        recordsBySyncariId.setExistingRecord(entityData);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreEntityDef.getFieldByName(coreField), coreAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations())
                .setResolvedIds(new HashMap<>(Map.of("test", Map.of("test", "test")))).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        return executeFieldPipeline.createSyncariEntityWithGraph(request);
    }
    
    public Change executeFunction(EntityDefinition coreEntityDef, EntityDefinition sourceEntityDef, String coreField, String functionName, Map<String, Object> functionParams, EntityData entityData, String... sourceFields) {
        Connector connector = createConnector("my zendesk connector", "my zendesk connector", "zendeskConnectorId");
        List<Pair> fields = new ArrayList<>();
        List<MappingNode> nodes = new ArrayList<>();
        for (String sourceField : sourceFields) {
            fields.add(Pair.of(sourceField, new StringType()));
        }
        fields.add(Pair.of(coreField, new IntegerType()));
        EntityDefinition srcEntityDef = sourceEntityDef == null ? getEntityDef("Organization", connector, fields)
                : sourceEntityDef;
        
        MappingGraph entityGraph = createGraph(coreEntityDef.getId(), Scope.ENTITY);
        MappingNode coreNode = coreEntityNode(coreEntityDef, entityGraph);
        MappingNode srcNode = srcEntityNode(srcEntityDef, entityGraph);
        edge(srcNode, coreNode, entityGraph);
        
        MappingGraph coreAttrGraph = createGraph(coreEntityDef.getFieldByName(coreField).getId(), Scope.ATTRIBUTE);
        for (String sourceField : sourceFields) {
            nodes.add(srcAttributeNode(srcEntityDef.getFieldByName(sourceField), coreAttrGraph));
        }
        MappingNode functionNode = createFunctionNode(func(functionName, Scope.ATTRIBUTE), Scope.ATTRIBUTE, functionParams, new StringType(), nodes);
        coreAttrGraph.getNodes().add(functionNode);
        for (MappingNode srcAttrNode : nodes) {
            edge(srcAttrNode, functionNode, coreAttrGraph);
        }
        
        MappingNode coreDayAttrNode = coreAttributeNode(coreEntityDef.getFieldByName(coreField), coreAttrGraph);
        edge(functionNode, coreDayAttrNode, coreAttrGraph);
        
        when(schemaService.getEntity(srcEntityDef.getId())).thenReturn(srcEntityDef);
        when(schemaService.getEntity(coreEntityDef.getId())).thenReturn(coreEntityDef);
        when(connectorService.get(srcEntityDef.getConnectorId())).thenReturn(connector);
        when(connectorService.refreshAuthentication(any(Connector.class))).then(returnsFirstArg());
        when(entityRepo.findById(coreEntityDef, entityData.getSyncariEntityId())).thenReturn(Optional.of(entityData));
        
        GraphContext currentContext = new GraphContext();
        currentContext.setCurrentBatch(createCurrentBatch());
        srcEntityDef.getActiveAttributes().forEach(attributeDefinition -> {
            currentContext.set("field_" + attributeDefinition.getId(), entityData.getValue(attributeDefinition.getApiName()));
        });
        
        RecordsBySyncariId recordsBySyncariId = new RecordsBySyncariId(entityData.getSyncariEntityId());
        recordsBySyncariId.setExistingRecord(entityData);
        FieldsGraphRequest request = new FieldsGraphRequest().setEntityName("account")
                .setRecords(recordsBySyncariId)
                .setGraphContext(currentContext)
                .setAttributeDAGs(Map.of(coreEntityDef.getFieldByName(coreField), coreAttrGraph))
                .setSyncariEntityDef(coreEntityDef)
                .setAttributeBatchActionContext(new BatchActionContext())
                .setBatchedOperations(new BatchedOperations()).setEntityDefCache(new HashMap<>()).setConnectorCache(new HashMap<>());

        return executeFieldPipeline.createSyncariEntityWithGraph(request);
    }

    public Change executeFunction(EntityDefinition coreEntityDef, String sourceField, String coreField, String functionName, EntityData entityData) {
        return executeFunction(coreEntityDef, null, sourceField, coreField, functionName, Map.of(), entityData);
    }

    public Change executeFunction(EntityDefinition coreEntityDef, EntityDefinition sourceEntityDef, String sourceField, String coreField, String functionName, Map<String, Object> functionParams, EntityData entityData) {
        return executeFunction(coreEntityDef, sourceEntityDef, sourceField, coreField, functionName, functionParams, entityData, false);
    }

    private static CurrentBatch createCurrentBatch() {
        return new CurrentBatch(null).setCurrentBatchId(UUID.randomUUID().toString());
    }

}
