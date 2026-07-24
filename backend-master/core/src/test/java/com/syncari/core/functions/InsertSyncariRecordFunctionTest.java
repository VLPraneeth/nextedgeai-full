package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.service.FunctionService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.KeyValue;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class InsertSyncariRecordFunctionTest extends AbstractSyncariTest {
    @Autowired
    InsertSyncariRecordFunction entityFunction;

    @Autowired
    FunctionService functionService;

    @Test
    public void validateInsertSyncariRecordFunction(){

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

        MappingGraph entityGraph = newGraph(coreEntity, functionService)
                .src(srcEntity)
                .function(FunctionConstants.INSERT_SYNCARI_RECORD, "Insert Records", Map.of())
                .dest(sinkEntity)
                .connect("srcAccount", "Insert Records")
                .connect("Insert Records", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();

        doReturn(Optional.of(syncariEntity)).when(mockSchemaService).getSyncariEntityById(syncariEntity.getId());
        doReturn(Optional.of(coreEntity)).when(mockSchemaService).getSyncariEntityById(coreEntity.getId());
        doReturn(Optional.of(syncariEntity)).when(mockSchemaService).findEntity(syncariEntity.getId());
        doReturn(Optional.of(coreEntity)).when(mockSchemaService).findEntity(coreEntity.getId());

        // case 1: No syncari entityId
        MappingNode insertRecordNode = entityGraph.findNodeByName("Insert Records").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(insertRecordNode);
        try{
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Syncari Entity from Insert Records in graph coreAccount", e.getMessage());
        }

        // case 3: invalid syncari entityId
        SimpleFunctionNodeConfig insertRecordConfig = insertRecordNode.getTypedConfiguration();
        insertRecordConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", "INVALID"));
        insertRecordNode.setConfiguration(insertRecordConfig);
        try{
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Syncari Entity 'INVALID' in node Insert Records of graph coreAccount", e.getMessage());
        }

        // case 4: empty insertFields
        insertRecordConfig = insertRecordNode.getTypedConfiguration();
        insertRecordConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "insertFields", List.of()));
        insertRecordNode.setConfiguration(insertRecordConfig);
        try{
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Fields 'Empty Fields' in node Insert Records of graph coreAccount", e.getMessage());
        }

        // case 5: insertFields input NOT from selected syncari entity
        var updateFields = List.of(Map.of("updateField", new KeyValue("name", "updateField", "value", coreField1.getId())));
        insertRecordConfig = insertRecordNode.getTypedConfiguration();
        insertRecordConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "insertFields", updateFields));
        insertRecordNode.setConfiguration(insertRecordConfig);
        try{
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid field configuration in node Insert Records of graph coreAccount", e.getMessage());
        }

        // case 7: valid insertFields
        updateFields = List.of(Map.of("updateField", new KeyValue("name", "updateField", "value", syncariField1.getId())));
        insertRecordConfig = insertRecordNode.getTypedConfiguration();
        insertRecordConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "insertFields", updateFields));
        insertRecordNode.setConfiguration(insertRecordConfig);
        entityFunction.validate(context);
    }
}
