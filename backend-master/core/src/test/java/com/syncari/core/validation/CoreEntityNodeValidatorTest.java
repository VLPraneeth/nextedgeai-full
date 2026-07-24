package com.syncari.core.validation;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AdvancedDedupeConfig;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FieldLevelWinnerSelection;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.WinnerOverridePolicy;
import com.syncari.core.model.WinnerSelection;
import com.syncari.core.model.WinnerValueSelectionPolicy;
import com.syncari.core.model.WinningAttributeOverride;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.DedupeTestHelper;
import com.syncari.core.service.FunctionService;
import com.syncari.core.utils.SchemaHelper;
import com.syncari.utils.KeyValue;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CoreEntityNodeValidatorTest extends AbstractSyncariTest {

    @Autowired
    FunctionService functionService;

    @Autowired
    CoreEntityNodeValidator nodeValidator;

    @Test
    public void validate(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        // case 1: valid case
        MappingGraph graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        ValidationContext context = new ValidationContext().setGraph(graph);
        context.setSourceEntityMap(Map.of(srcEntity1.getId(), srcEntity1, srcEntity2.getId(), srcEntity2));
        context.setCoreEntity(coreEntity);
        context.setNode(graph.getCoreNode());
        nodeValidator.validate(context);

        // case 2: core with deleted attribute
        coreEntity.setDraftStatus(DraftStatus.ARCHIVED);
        graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        context.setGraph(graph).setNode(graph.getCoreNode());
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Core node coreAccount of graph coreAccount is using deleted entity. Please remove the the node and use valid entity.", e.getMessage());
        }

    }

    @Test
    public void validate_InvalidFieldReferenceInFindDupes(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        AttributeDefinition validSyncariField = SchemaHelper.createAttribute("validField", new StringType(), coreEntity.getId());
        AttributeDefinition invalidSyncariField = SchemaHelper.createAttribute("invalidField", new StringType(), coreEntity.getId());
        coreEntity.addField(validSyncariField);

        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        // case 1: invalid case
        MappingGraph graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        MappingNode coreNode = graph.getCoreNode();
        CoreEntityNodeConfig coreEntityNodeConfig = coreNode.getTypedConfiguration();
        var findDupesCriteria = toFindDupesMap(getExpressionForField(validSyncariField), getExpressionForField(invalidSyncariField));
        coreEntityNodeConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig().setFindDupes(findDupesCriteria));
        coreNode.setConfiguration(coreEntityNodeConfig);

        ValidationContext context = new ValidationContext().setGraph(graph);
        context.setSourceEntityMap(Map.of(srcEntity1.getId(), srcEntity1, srcEntity2.getId(), srcEntity2));
        context.setCoreEntity(coreEntity);
        context.setNode(graph.getCoreNode());
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals(String.format("Invalid Field (id = %s) in the field merge condition in dedupe config of syncari node coreAccount in coreAccount pipeline", invalidSyncariField.getId()), e.getMessage());
        }

        // case 2: only valid findDupes criteria - SUCCESS
        coreNode = graph.getCoreNode();
        coreEntityNodeConfig = coreNode.getTypedConfiguration();
        findDupesCriteria = toFindDupesMap(getExpressionForField(validSyncariField));
        coreEntityNodeConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig().setFindDupes(findDupesCriteria));
        coreNode.setConfiguration(coreEntityNodeConfig);
        graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        context.setGraph(graph).setNode(graph.getCoreNode());
        nodeValidator.validate(context);
    }

    @Test
    public void validate_InvalidFieldReferenceInWinnerSelection(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        AttributeDefinition validSyncariField = SchemaHelper.createAttribute("validField", new StringType(), coreEntity.getId());
        AttributeDefinition invalidSyncariField = SchemaHelper.createAttribute("invalidField", new StringType(), coreEntity.getId());
        coreEntity.addField(validSyncariField);

        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        // case 1: invalid case
        MappingGraph graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        MappingNode coreNode = graph.getCoreNode();
        CoreEntityNodeConfig coreEntityNodeConfig = coreNode.getTypedConfiguration();
        var findDupesCriteria = toFindDupesMap(getExpressionForField(validSyncariField));
        var winnerSelectionCriteria = toSelectWinnerMap(getExpressionForField(validSyncariField), getExpressionForField(invalidSyncariField));
        coreEntityNodeConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig().setFindDupes(findDupesCriteria).setSelectWinner(winnerSelectionCriteria));
        coreNode.setConfiguration(coreEntityNodeConfig);

        ValidationContext context = new ValidationContext().setGraph(graph);
        context.setSourceEntityMap(Map.of(srcEntity1.getId(), srcEntity1, srcEntity2.getId(), srcEntity2));
        context.setCoreEntity(coreEntity);
        context.setNode(coreNode);
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals(String.format("Invalid Field (id = %s) in the field merge condition in dedupe config of syncari node coreAccount in coreAccount pipeline", invalidSyncariField.getId()), e.getMessage());
        }

        // case 2: only valid selectWinner criteria - SUCCESS
        coreNode = graph.getCoreNode();
        coreEntityNodeConfig = coreNode.getTypedConfiguration();
        findDupesCriteria = toFindDupesMap(getExpressionForField(validSyncariField));
        coreEntityNodeConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig().setFindDupes(findDupesCriteria));
        winnerSelectionCriteria = toSelectWinnerMap(getExpressionForField(validSyncariField));
        coreEntityNodeConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig().setFindDupes(findDupesCriteria).setSelectWinner(winnerSelectionCriteria));
        coreNode.setConfiguration(coreEntityNodeConfig);
        graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        context.setGraph(graph).setNode(graph.getCoreNode());
        nodeValidator.validate(context);
    }

    @Test
    public void validate_InvalidFieldReferenceInFieldOverride(){
        EntityDefinition coreEntity = SchemaHelper.createEntityDef("coreAccount", "account", null);
        AttributeDefinition validSyncariField = SchemaHelper.createAttribute("validField", new StringType(), coreEntity.getId());
        AttributeDefinition invalidSyncariField = SchemaHelper.createAttribute("invalidField", new StringType(), coreEntity.getId());
        coreEntity.addField(validSyncariField);

        EntityDefinition srcEntity1 = SchemaHelper.createEntityDef("srcAccount1", "Source Account1", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));
        EntityDefinition srcEntity2 = SchemaHelper.createEntityDef("srcAccount2", "Source Account2", createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta"));

        // case 1: invalid case
        MappingGraph graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        MappingNode coreNode = graph.getCoreNode();
        CoreEntityNodeConfig coreEntityNodeConfig = coreNode.getTypedConfiguration();
        var findDupesCriteria = toFindDupesMap(getExpressionForField(validSyncariField));
        var winnerSelectionCriteria = toSelectWinnerMap(getExpressionForField(validSyncariField));
        var fieldMergeCriteria = toFieldMergePolicyMapForField(invalidSyncariField);
        coreEntityNodeConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig().setFindDupes(findDupesCriteria)
                .setSelectWinner(winnerSelectionCriteria).setFieldMergePolicies(fieldMergeCriteria));
        coreNode.setConfiguration(coreEntityNodeConfig);

        ValidationContext context = new ValidationContext().setGraph(graph);
        context.setSourceEntityMap(Map.of(srcEntity1.getId(), srcEntity1, srcEntity2.getId(), srcEntity2));
        context.setCoreEntity(coreEntity);
        context.setNode(graph.getCoreNode());
        try {
            nodeValidator.validate(context);
            fail();
        } catch (SyncariValidationException e){
            assertEquals(String.format("Invalid Field (id = %s) in the field merge condition in dedupe config of syncari node coreAccount in coreAccount pipeline", invalidSyncariField.getId()), e.getMessage());
        }

        // case 2: only valid selectWinner criteria - SUCCESS
        coreNode = graph.getCoreNode();
        coreEntityNodeConfig = coreNode.getTypedConfiguration();
        findDupesCriteria = toFindDupesMap(getExpressionForField(validSyncariField));
        coreEntityNodeConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig().setFindDupes(findDupesCriteria));
        winnerSelectionCriteria = toSelectWinnerMap(getExpressionForField(validSyncariField));
        fieldMergeCriteria = toFieldMergePolicyMapForField(validSyncariField);
        coreEntityNodeConfig.setAdvancedDedupeConfig(new AdvancedDedupeConfig().setFindDupes(findDupesCriteria)
                .setSelectWinner(winnerSelectionCriteria).setFieldMergePolicies(fieldMergeCriteria));
        coreNode.setConfiguration(coreEntityNodeConfig);

        graph = newGraph(coreEntity, functionService)
                .src(srcEntity1)
                .src(srcEntity2)
                .connect("srcAccount1", "coreAccount")
                .connect("srcAccount2", "coreAccount").getGraph();

        context.setGraph(graph).setNode(graph.getCoreNode());
        nodeValidator.validate(context);
    }

    public static Map<String, Object> toSelectWinnerMap(Expression... expressions) {

        List<Map<String, Object>> predicateMaps =new ArrayList<>();
        for(Expression expression : expressions) {
            ExpressionToMapVisitor visitor = new ExpressionToMapVisitor();
            Map<String, Object> winnerSelectionPredicate = DedupeTestHelper.toCompositeMap(visitor, expression, "winnerSelectionPredicate");
            predicateMaps.add(winnerSelectionPredicate);
        }
        return Map.of("configId", ObjectId.get().toHexString(), "name", "selectWinner", "compositeValues", predicateMaps);
    }

    private Expression getExpressionForField(AttributeDefinition field){
        return Expression.eq(Expression.var(field.getId()),Expression.lit(field.getId()));
    }
    private Map<String, Object> toFindDupesMap(Expression... expressions) {
        return DedupeTestHelper.toFindDupesMap(expressions);
    }

    private Map<String, Object> toFieldMergePolicyMapForField(AttributeDefinition field) {

        var fieldMergePolicy = KeyValue.of("name","fieldMergePolicies","compositeValues", List.of(
                KeyValue.of("fieldMergePredicate",KeyValue.of("name","fieldMergePredicate","value",KeyValue.of("predicates",
                        List.of(

                                KeyValue.of(
                                        "left",KeyValue.of("datatype","picklist","picklistGroup","Fields","label","Credit Line :Account","type","variable","value",field.getId()),
                                        "operator","max",
                                        "name","fieldMergePredicate",
                                        "right", KeyValue.of("type", "literal", "value", Map.of("retainfields", List.of()))
                                )
                        ),"operator","AND"
                        )),
                        "fieldOverridePolicy",KeyValue.of("name","fieldOverridePolicy","value","ALWAYS")
                )
        ));

        return fieldMergePolicy;
    }
}
