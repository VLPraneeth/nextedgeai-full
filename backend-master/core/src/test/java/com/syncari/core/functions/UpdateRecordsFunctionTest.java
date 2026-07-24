package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.KeyValue;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class UpdateRecordsFunctionTest extends AbstractSyncariTest {
    @Autowired
    @Qualifier(FunctionConstants.UPDATE_SYNCARI_RECORDS)
    UpdateRecordsFunction entityFunction;

    @Autowired
    FunctionService functionService;

    @Test
    public void validateUpdateRecordsFunction(){

        SchemaService mockSchemaService = mock(SchemaService.class);
        entityFunction.schemaService = mockSchemaService;
        EntityDefinition syncariEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var syncariField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, syncariEntity.getId());
        var syncariField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, syncariEntity.getId());
        syncariEntity.addField(syncariField1);
        syncariEntity.addField(syncariField2);

        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var coreField1 = SchemaHelper.createAttribute("corefield1", StringType.VALUE, coreEntity.getId());
        var coreField2 = SchemaHelper.createAttribute("corefield2", StringType.VALUE, coreEntity.getId());
        coreEntity.addField(coreField1);
        coreEntity.addField(coreField2);

        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        var srcField1 = SchemaHelper.createAttribute("srcfield1", StringType.VALUE, srcEntity.getId());
        var srcField2 = SchemaHelper.createAttribute("srcfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(srcField1);
        srcEntity.addField(srcField2);

        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));
        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, srcEntity.getId());
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(sinkField1);
        srcEntity.addField(sinkField2);

        var validPredicate = Expression.notEmpty(Expression.var(syncariField1.getId()));
        var invalidPredicate = Expression.notEmpty(Expression.var(coreField1.getId()));

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        invalidPredicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function(FunctionConstants.UPDATE_SYNCARI_RECORDS, "Update Records", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "Update Records")
                .connect("Update Records", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();

        doReturn(Optional.of(syncariEntity)).when(mockSchemaService).getSyncariEntityById(syncariEntity.getId());
        doReturn(Optional.of(coreEntity)).when(mockSchemaService).getSyncariEntityById(coreEntity.getId());
        doReturn(Optional.of(syncariEntity)).when(mockSchemaService).findEntity(syncariEntity.getId());
        doReturn(Optional.of(coreEntity)).when(mockSchemaService).findEntity(coreEntity.getId());

        // case 1: No syncari entityId
        MappingNode updateRecordNode = entityGraph.findNodeByName("Update Records").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(updateRecordNode);
        try{
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Syncari Entity from Update Records in graph coreAccount", e.getMessage());
        }

        // case 3: invalid syncari entityId
        SimpleFunctionNodeConfig updateRecordConfig = updateRecordNode.getTypedConfiguration();
        updateRecordConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", "INVALID", "predicate", predicateMap));
        updateRecordNode.setConfiguration(updateRecordConfig);
        try{
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Syncari Entity 'INVALID' in node Update Records of graph coreAccount", e.getMessage());
        }

        // case 4: valid syncari entityId with invalid predicate
        updateRecordConfig = updateRecordNode.getTypedConfiguration();
        updateRecordConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap));
        updateRecordNode.setConfiguration(updateRecordConfig);
        try{
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals(String.format("Invalid search condition in node Update Records of graph coreAccount", syncariEntity.getId()), e.getMessage());
        }

        // case 5: valid syncari entityId with valid predicate and empty update fields
        mapper = new ExpressionToMapVisitor();
        validPredicate.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        updateRecordConfig = updateRecordNode.getTypedConfiguration();
        updateRecordConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap));
        updateRecordNode.setConfiguration(updateRecordConfig);
        try{
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Update Fields 'Empty Update Fields' in node Update Records of graph coreAccount", e.getMessage());
        }

        // case 6: updateFields input NOT from selected syncari entity
        var updateFields = List.of(Map.of("updateField", new KeyValue("name", "updateField", "value", coreField1.getId()),"operation","replace"));
        updateRecordConfig = updateRecordNode.getTypedConfiguration();
        updateRecordConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "updateFields", updateFields));
        updateRecordNode.setConfiguration(updateRecordConfig);
        try{
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid field for Update field configuration in node Update Records of graph coreAccount", e.getMessage());
        }

        // case 7: valid updateFields
        updateFields = List.of(Map.of("updateField", new KeyValue("name", "updateField", "value", syncariField1.getId()),"operation","replace"));
        updateRecordConfig = updateRecordNode.getTypedConfiguration();
        updateRecordConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "updateFields", updateFields));
        updateRecordNode.setConfiguration(updateRecordConfig);
        entityFunction.validate(context);
    }
}
