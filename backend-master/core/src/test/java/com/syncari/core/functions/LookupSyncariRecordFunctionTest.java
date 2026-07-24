package com.syncari.core.functions;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.expression.Expression;
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
import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class LookupSyncariRecordFunctionTest extends AbstractSyncariTest {

    @Autowired
    LookupSyncariRecordOnEntityFunction entityFunction;

    @Autowired
    LookupSyncariRecordOnFieldFunction attributeFunction;

    @Autowired
    FunctionService functionService;

    @Test
    public void validateEntityLookupSyncariRecordFunction(){

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
                .function("advancedLookupSyncariRecord", "Lookup", Map.of("predicate", predicateMap))
                .dest(sinkEntity)
                .connect("srcAccount", "Lookup")
                .connect("Lookup", "coreAccount")
                .connect("coreAccount", "sinkAccount").getGraph();

        doReturn(Optional.of(syncariEntity)).when(mockSchemaService).getSyncariEntityById(syncariEntity.getId());
        doReturn(Optional.of(coreEntity)).when(mockSchemaService).getSyncariEntityById(coreEntity.getId());

        // case 1: No syncari entityId
        MappingNode lookupNode = entityGraph.findNodeByName("Lookup").get();
        ValidationContext context = new ValidationContext().setGraph(entityGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(lookupNode);
        String expectedErrorMessage = "Missing Syncari Entity from Lookup in graph coreAccount";
        // test validate method
        try {
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals(expectedErrorMessage, e.getMessage());
        }
        // test validateWithoutException method
        List<ValidationError> validationErrors = entityFunction.validateWithoutException(context);
        assertEquals(validationErrors, List.of(ValidationError.scopedError(lookupNode.getScope(), lookupNode.getId()).withMessage(expectedErrorMessage).withErrorCode("1091")));

        // case 3: invalid syncari entityId
        SimpleFunctionNodeConfig lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", "INVALID", "predicate", predicateMap));
        lookupNode.setConfiguration(lookupConfig);
        expectedErrorMessage = "Invalid Syncari Entity 'INVALID' in node Lookup of graph coreAccount";
        // test validate method
        try {
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals(expectedErrorMessage, e.getMessage());
        }
        // test validateWithoutException method
        validationErrors = entityFunction.validateWithoutException(context);
        assertEquals(validationErrors, List.of(ValidationError.scopedError(lookupNode.getScope(), lookupNode.getId()).withMessage(expectedErrorMessage).withErrorCode("1116")));

        // case 4: valid syncari entityId with invalid predicate
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap));
        lookupNode.setConfiguration(lookupConfig);
        expectedErrorMessage = "Invalid lookup condition in node Lookup of graph coreAccount";
        // test validate method
        try {
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals(expectedErrorMessage, e.getMessage());
        }
        // test validateWithoutException method
        validationErrors = entityFunction.validateWithoutException(context);
        assertEquals(validationErrors, List.of(ValidationError.scopedError(lookupNode.getScope(), lookupNode.getId()).withMessage(expectedErrorMessage)));

        // case 5: valid syncari entityId with empty predicate
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", Map.of("predicateId", (Object) "predicateId")));
        lookupNode.setConfiguration(lookupConfig);
        expectedErrorMessage = "Unknown operator ";
        // test validate method
        try {
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals(expectedErrorMessage, e.getMessage());
        }
        // test validateWithoutException method
        validationErrors = entityFunction.validateWithoutException(context);
        assertEquals(validationErrors, List.of(ValidationError.scopedError(lookupNode.getScope(), lookupNode.getId()).withMessage(expectedErrorMessage)));

        // case 6: valid syncari entityId with valid predicate - SUCCESS
        mapper = new ExpressionToMapVisitor();
        validPredicate.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap));
        lookupNode.setConfiguration(lookupConfig);
        entityFunction.validate(context);
        // test validateWithoutException method
        validationErrors = entityFunction.validateWithoutException(context);
        assertTrue(validationErrors.isEmpty());

        // case 7: sortFields input NOT from selected syncari entity
        var sortFields = List.of(Map.of("sortField", new KeyValue("name", "sortField", "value", coreField1.getId()),
                "sortDirection", new KeyValue("name", "sortDirection", "value", "asc")));
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "sortFields", sortFields));
        lookupNode.setConfiguration(lookupConfig);
        expectedErrorMessage = "Invalid Sort Field in node Lookup of graph coreAccount";
        // test validate method
        try {
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals(expectedErrorMessage, e.getMessage());
        }
        // test validateWithoutException method
        validationErrors = entityFunction.validateWithoutException(context);
        assertEquals(validationErrors, List.of(ValidationError.scopedError(lookupNode.getScope(), lookupNode.getId()).withMessage(expectedErrorMessage).withErrorCode("1118")));

        // case 8: valid sortField with invalid sort direction
        sortFields = List.of(Map.of("sortField", new KeyValue("name", "sortField", "value", syncariField1.getId()),
                "sortDirection", new KeyValue("name", "sortDirection", "value", "INVALID")));
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "sortFields", sortFields));
        lookupNode.setConfiguration(lookupConfig);
        expectedErrorMessage = "Invalid Sort Direction 'INVALID' in node Lookup of graph coreAccount";
        // test validate method
        try {
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals(expectedErrorMessage, e.getMessage());
        }
        // test validateWithoutException method
        validationErrors = entityFunction.validateWithoutException(context);
        assertEquals(validationErrors, List.of(ValidationError.scopedError(lookupNode.getScope(), lookupNode.getId()).withMessage(expectedErrorMessage).withErrorCode("1119")));

        // case 9: null safe check for sortField and sortDirection values
        sortFields = List.of(Map.of("sortField_rename", new KeyValue("name", "sortField", "value", syncariField1.getId()),
                "sortDirection", new KeyValue("name", "sortDirection", "value", "INVALID")));
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "sortFields", sortFields));
        lookupNode.setConfiguration(lookupConfig);
        expectedErrorMessage = "Invalid Sort Field in node Lookup of graph coreAccount";
        // test validate method
        try {
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals(expectedErrorMessage, e.getMessage());
        }
        // test validateWithoutException method
        validationErrors = entityFunction.validateWithoutException(context);
        assertEquals(validationErrors, List.of(
                ValidationError.scopedError(lookupNode.getScope(), lookupNode.getId()).withMessage(expectedErrorMessage).withErrorCode("1118"),
                ValidationError.scopedError(lookupNode.getScope(), lookupNode.getId()).withMessage("Invalid Sort Direction 'INVALID' in node Lookup of graph coreAccount").withErrorCode("1119")
        ));

        // case 10: null safe check for sortField and sortDirection values
        sortFields = List.of(Map.of("sortField", new KeyValue("name", "sortField", "value", syncariField1.getId()),
                "sortDirection_rename", new KeyValue("name", "sortDirection", "value", "INVALID")));
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "sortFields", sortFields));
        lookupNode.setConfiguration(lookupConfig);
        expectedErrorMessage = "Invalid Sort Direction '' in node Lookup of graph coreAccount";
        // test validate method
        try {
            entityFunction.validate(context);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals(expectedErrorMessage, e.getMessage());
        }
        // test validateWithoutException method
        validationErrors = entityFunction.validateWithoutException(context);
        assertEquals(validationErrors, List.of(ValidationError.scopedError(lookupNode.getScope(), lookupNode.getId()).withMessage(expectedErrorMessage).withErrorCode("1119")));

        // case 11: valid sortfields and direction
        sortFields = List.of(Map.of("sortField", new KeyValue("name", "sortField", "value", syncariField1.getId()),
                "sortDirection", new KeyValue("name", "sortDirection", "value", "asc")));
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "sortFields", sortFields));
        lookupNode.setConfiguration(lookupConfig);
        // test validate method
        entityFunction.validate(context);
        // test validateWithoutException method
        validationErrors = entityFunction.validateWithoutException(context);
        assertTrue(validationErrors.isEmpty());
    }

    @Test
    public void validateAttributeLookupSyncariRecordFunction(){

        SchemaService mockSchemaService = mock(SchemaService.class);
        attributeFunction.schemaService = mockSchemaService;
        EntityDefinition syncariEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        var syncariField1 = SchemaHelper.createAttribute("syncarifield1", StringType.VALUE, syncariEntity.getId());
        var syncariField2 = SchemaHelper.createAttribute("syncarifield2", StringType.VALUE, syncariEntity.getId());
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

        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", StringType.VALUE, srcEntity.getId());
        var sinkField2 = SchemaHelper.createAttribute("sinkfield2", StringType.VALUE, srcEntity.getId());
        srcEntity.addField(sinkField1);
        srcEntity.addField(sinkField2);

        var validPredicate = Expression.notEmpty(Expression.var(syncariField1.getId()));
        var invalidPredicate = Expression.notEmpty(Expression.var(coreField1.getId()));

        ExpressionToMapVisitor mapper = new ExpressionToMapVisitor();
        invalidPredicate.accept(mapper);
        var predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");

        MappingGraph fieldGraph = newGraph(coreField1, functionService)
                .src(srcField1)
                .function("advancedLookupSyncariRecordOnField", "Lookup", Map.of("predicate", predicateMap))
                .dest(sinkField1)
                .connect("srcfield1", "Lookup")
                .connect("Lookup", "corefield1")
                .connect("corefield1", "sinkfield1").getGraph();

        doReturn(Optional.of(syncariEntity)).when(mockSchemaService).getSyncariEntityById(syncariEntity.getId());
        doReturn(Optional.of(coreEntity)).when(mockSchemaService).getSyncariEntityById(coreEntity.getId());

        // case 1: No syncari entityId
        MappingNode lookupNode = fieldGraph.findNodeByName("Lookup").get();
        ValidationContext context = new ValidationContext().setGraph(fieldGraph);
        context.setSourceEntityMap(Map.of(srcEntity.getId(), srcEntity));
        context.setCoreEntity(coreEntity);
        context.setNode(lookupNode);
        try{
            attributeFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Missing Syncari Entity from Lookup in graph corefield1", e.getMessage());
        }

        // case 3: invalid syncari entityId
        SimpleFunctionNodeConfig lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", "INVALID", "predicate", predicateMap));
        lookupNode.setConfiguration(lookupConfig);
        try{
            attributeFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Syncari Entity 'INVALID' in node Lookup of graph corefield1", e.getMessage());
        }

        // case 4: valid syncari entityId with invalid predicate
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap));
        lookupNode.setConfiguration(lookupConfig);
        try{
            attributeFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid lookup condition in node Lookup of graph corefield1", e.getMessage());
        }

        // case 5: valid syncari entityId with valid predicate - SUCCESS
        mapper = new ExpressionToMapVisitor();
        validPredicate.accept(mapper);
        predicateMap = Map.of("predicates", List.of(mapper.getMap()), "operator", "AND");
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap));
        lookupNode.setConfiguration(lookupConfig);

        attributeFunction.validate(context);

        // case 6: sortFields input NOT from selected syncari entity
        var sortFields = List.of(Map.of("sortField", new KeyValue("name", "sortField", "value", coreField1.getId()),
                "sortDirection", new KeyValue("name", "sortDirection", "value", "asc")));
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "sortFields", sortFields));
        lookupNode.setConfiguration(lookupConfig);
        try{
            attributeFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Sort Field in node Lookup of graph corefield1", e.getMessage());
        }

        // case 6: valid sortField with invalid sort direction
        sortFields = List.of(Map.of("sortField", new KeyValue("name", "sortField", "value", syncariField1.getId()),
                "sortDirection", new KeyValue("name", "sortDirection", "value", "INVALID")));
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "sortFields", sortFields));
        lookupNode.setConfiguration(lookupConfig);
        try{
            attributeFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Sort Direction 'INVALID' in node Lookup of graph corefield1", e.getMessage());
        }

        // case 7: valid sortField with invalid sort row

        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "sortFields", "bad_value"));
        lookupNode.setConfiguration(lookupConfig);
        try{
            attributeFunction.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Sort Field in node Lookup of graph corefield1", e.getMessage());
        }
        // case 7: valid sortfields and direction
        sortFields = List.of(Map.of("sortField", new KeyValue("name", "sortField", "value", syncariField1.getId()),
                "sortDirection", new KeyValue("name", "sortDirection", "value", "asc")));
        lookupConfig = lookupNode.getTypedConfiguration();
        lookupConfig.getFunctionCall().setConfig(Map.of("syncariEntityDefId", syncariEntity.getId(), "predicate", predicateMap, "sortFields", sortFields));
        lookupNode.setConfiguration(lookupConfig);
        attributeFunction.validate(context);
    }
}
